package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AccessLogEntry
import com.example.data.model.AdminPinInboxItem
import com.example.data.model.AuthorizedUser
import com.example.data.model.PinChallenge
import com.example.util.AccessControlConfig
import com.example.util.PasswordHasher
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AccessControlRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireCurrentUid(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("Debés iniciar sesión primero.")

  fun observeAllAuthorizedUsers(): Flow<List<AuthorizedUser>> = flow {
    val path = COLLECTION_NAME
    emitAll(
      db.collection(COLLECTION_NAME)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(AuthorizedUser::class.java, ServerTimestampBehavior.ESTIMATE)
            .sortedWith(
              compareByDescending<AuthorizedUser> { it.enabled }
                .thenBy { it.displayName.lowercase() }
                .thenBy { it.email.lowercase() }
            )
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, path)
          throw error
        }
    )
  }

  fun observeAuthorizedUserByAccountId(rawAccountId: String): Flow<AuthorizedUser?> = flow {
    val accountId = AccessControlConfig.normalizeAccountId(rawAccountId)
    if (!AccessControlConfig.isValidAccountId(accountId)) {
      emit(null)
      return@flow
    }
    val docRef = db.collection(COLLECTION_NAME).document(accountId)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(AuthorizedUser::class.java, ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          logStreamError(error, OperationType.GET, docRef.path)
          throw error
        }
    )
  }

  fun observeAuthorizedUserByEmail(rawEmail: String): Flow<AuthorizedUser?> =
    observeAuthorizedUserByAccountId(rawEmail)

  fun observeBoundAccountId(uid: String): Flow<String?> = flow {
    if (uid.isBlank()) {
      emit(null)
      return@flow
    }
    val docRef = db.collection(USER_BINDINGS_COLLECTION).document(uid)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.getString("accountId")?.trim()?.takeIf { it.isNotBlank() }
          } else {
            null
          }
        }
        .catch { error ->
          logStreamError(error, OperationType.GET, docRef.path)
          throw error
        }
    )
  }

  suspend fun getAuthorizedUserByAccountId(rawAccountId: String): Result<AuthorizedUser?> {
    val accountId = AccessControlConfig.normalizeAccountId(rawAccountId)
    if (!AccessControlConfig.isValidAccountId(accountId)) {
      return Result.failure(
        IllegalArgumentException(
          "Ingresá un usuario válido (mín. 3 caracteres, sin espacios)."
        )
      )
    }
    val docRef = db.collection(COLLECTION_NAME).document(accountId)
    return try {
      val snapshot = docRef.get().await()
      if (snapshot.exists()) {
        Result.success(snapshot.toObject(AuthorizedUser::class.java, ServerTimestampBehavior.ESTIMATE))
      } else {
        Result.success(null)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: FirebaseFirestoreException) {
      if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        Result.failure(
          IllegalStateException(
            "Este usuario ya está vinculado a otro dispositivo. Pedile al administrador que lo desvincule."
          )
        )
      } else {
        handleFirestoreError(e, OperationType.GET, docRef.path, auth)
        Result.failure(e)
      }
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun clearCurrentUserBinding(): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.success(Unit)
    val docRef = db.collection(USER_BINDINGS_COLLECTION).document(uid)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun createAuthorizedUser(
    displayName: String,
    rawEmail: String,
    rawPassword: String,
    notes: String = "",
  ): Result<AuthorizedUser> {
    val adminUid = requireCurrentUid()
    val accountId = AccessControlConfig.normalizeAccountId(rawEmail)
    val cleanName = displayName.trim().take(80)
    val cleanNotes = notes.trim().take(200)

    if (cleanName.isBlank()) {
      return Result.failure(IllegalArgumentException("Ingresá un nombre para la persona."))
    }
    if (!AccessControlConfig.isValidAccountId(accountId)) {
      return Result.failure(
        IllegalArgumentException(
          "Ingresá un usuario válido (mín. 3 caracteres; solo letras, números, punto, guion o guion bajo, sin espacios)."
        )
      )
    }
    if (AccessControlConfig.isAdminEmail(accountId)) {
      return Result.failure(
        IllegalArgumentException("Tu correo administrador ya tiene acceso permanente.")
      )
    }
    if (rawPassword.length < AccessControlConfig.MIN_PASSWORD_LENGTH) {
      return Result.failure(
        IllegalArgumentException(
          "La contraseña debe tener al menos ${AccessControlConfig.MIN_PASSWORD_LENGTH} caracteres."
        )
      )
    }
    if (rawPassword.length > AccessControlConfig.MAX_PASSWORD_LENGTH) {
      return Result.failure(
        IllegalArgumentException(
          "La contraseña no puede superar los ${AccessControlConfig.MAX_PASSWORD_LENGTH} caracteres."
        )
      )
    }

    val docRef = db.collection(COLLECTION_NAME).document(accountId)
    return try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        return Result.failure(
          IllegalStateException(
            "Ya existe un acceso con ese usuario. Podés editarlo o cambiarle la contraseña."
          )
        )
      }

      val hashResult = withContext(Dispatchers.Default) { PasswordHasher.hashPassword(rawPassword) }
      val payload =
        mapOf(
          "email" to accountId,
          "displayName" to cleanName,
          "enabled" to true,
          "passwordHash" to hashResult.hashHex,
          "passwordSalt" to hashResult.saltHex,
          "passwordVersion" to 1L,
          "notes" to cleanNotes,
          "boundUid" to "",
          "lastAccessAt" to 0L,
          "createdBy" to adminUid,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      docRef.set(payload).await()
      Result.success(
        AuthorizedUser(
          id = accountId,
          email = accountId,
          displayName = cleanName,
          enabled = true,
          passwordHash = hashResult.hashHex,
          passwordSalt = hashResult.saltHex,
          passwordVersion = 1L,
          notes = cleanNotes,
          boundUid = "",
          lastAccessAt = 0L,
          createdBy = adminUid,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun setUserEnabled(rawEmail: String, enabled: Boolean): Result<Unit> {
    requireCurrentUid()
    val accountId = AccessControlConfig.normalizeAccountId(rawEmail)
    if (!AccessControlConfig.isValidAccountId(accountId)) {
      return Result.failure(IllegalArgumentException("Usuario inválido."))
    }
    val docRef = db.collection(COLLECTION_NAME).document(accountId)
    return try {
      docRef
        .update(
          mapOf(
            "enabled" to enabled,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateAuthorizedUserProfile(
    rawEmail: String,
    displayName: String,
    notes: String,
  ): Result<Unit> {
    requireCurrentUid()
    val accountId = AccessControlConfig.normalizeAccountId(rawEmail)
    val cleanName = displayName.trim().take(80)
    if (cleanName.isBlank()) {
      return Result.failure(IllegalArgumentException("El nombre no puede quedar vacío."))
    }
    val docRef = db.collection(COLLECTION_NAME).document(accountId)
    return try {
      docRef
        .update(
          mapOf(
            "displayName" to cleanName,
            "notes" to notes.trim().take(200),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun resetUserPassword(
    rawEmail: String,
    newRawPassword: String,
    currentVersion: Long,
  ): Result<Long> {
    requireCurrentUid()
    val accountId = AccessControlConfig.normalizeAccountId(rawEmail)
    if (!AccessControlConfig.isValidAccountId(accountId)) {
      return Result.failure(IllegalArgumentException("Usuario inválido."))
    }
    if (newRawPassword.length < AccessControlConfig.MIN_PASSWORD_LENGTH) {
      return Result.failure(
        IllegalArgumentException(
          "La contraseña debe tener al menos ${AccessControlConfig.MIN_PASSWORD_LENGTH} caracteres."
        )
      )
    }
    if (newRawPassword.length > AccessControlConfig.MAX_PASSWORD_LENGTH) {
      return Result.failure(
        IllegalArgumentException(
          "La contraseña no puede superar los ${AccessControlConfig.MAX_PASSWORD_LENGTH} caracteres."
        )
      )
    }
    val nextVersion = (currentVersion.coerceAtLeast(1L) + 1L).coerceAtMost(1_000_000L)
    val docRef = db.collection(COLLECTION_NAME).document(accountId)
    return try {
      val hashResult =
        withContext(Dispatchers.Default) { PasswordHasher.hashPassword(newRawPassword) }
      docRef
        .update(
          mapOf(
            "passwordHash" to hashResult.hashHex,
            "passwordSalt" to hashResult.saltHex,
            "passwordVersion" to nextVersion,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(nextVersion)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun unbindAuthorizedUser(
    rawAccountId: String,
    currentVersion: Long,
  ): Result<Long> {
    requireCurrentUid()
    val accountId = AccessControlConfig.normalizeAccountId(rawAccountId)
    if (!AccessControlConfig.isValidAccountId(accountId)) {
      return Result.failure(IllegalArgumentException("Usuario inválido."))
    }
    val nextVersion = (currentVersion.coerceAtLeast(1L) + 1L).coerceAtMost(1_000_000L)
    val docRef = db.collection(COLLECTION_NAME).document(accountId)
    return try {
      docRef
        .update(
          mapOf(
            "boundUid" to "",
            "passwordVersion" to nextVersion,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(nextVersion)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteAuthorizedUser(rawEmail: String): Result<Unit> {
    requireCurrentUid()
    val accountId = AccessControlConfig.normalizeAccountId(rawEmail)
    val docRef = db.collection(COLLECTION_NAME).document(accountId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun recordSelfAccess(
    rawAccountId: String,
    displayName: String = "",
  ): Result<Unit> {
    val uid = requireCurrentUid()
    val accountId = AccessControlConfig.normalizeAccountId(rawAccountId)
    if (!AccessControlConfig.isValidAccountId(accountId)) {
      return Result.failure(IllegalArgumentException("Usuario inválido."))
    }
    val docRef = db.collection(COLLECTION_NAME).document(accountId)
    val bindingRef = db.collection(USER_BINDINGS_COLLECTION).document(uid)
    return try {
      docRef
        .update(
          mapOf(
            "boundUid" to uid,
            "lastAccessAt" to System.currentTimeMillis(),
            "lastDeviceInfo" to AccessControlConfig.currentDeviceLabel(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      bindingRef
        .set(
          mapOf(
            "uid" to uid,
            "accountId" to accountId,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      appendAccessLog(
        accountId = accountId,
        displayName = displayName.ifBlank { accountId },
        eventType = AccessLogEntry.EVENT_SESSION_ACCESS,
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  /**
   * Creates a 6-digit random PIN verification challenge with current device metadata:
   * - Writes challenge (with deviceInfo, pinCode, and PBKDF2 hash/salt) to `/pinChallenges/{challengeId}`.
   * - Writes Admin inbox alert (with deviceInfo and pinCode) to `/adminPinInbox/{challengeId}` (Admin-read ONLY).
   * - Returns (challengeId to pinCode) so the user sees the random PIN above the PIN input immediately.
   */
  suspend fun createPinChallengeForUser(
    rawAccountId: String,
    displayName: String,
    requesterEmail: String?,
  ): Result<Pair<String, String>> {
    val uid = requireCurrentUid()
    val accountId = AccessControlConfig.normalizeAccountId(rawAccountId)
    if (!AccessControlConfig.isValidAccountId(accountId)) {
      return Result.failure(IllegalArgumentException("Usuario inválido."))
    }
    val cleanName = displayName.trim().ifBlank { accountId }.take(80)
    val cleanEmail = requesterEmail.orEmpty().trim().take(128)
    val deviceInfo = AccessControlConfig.currentDeviceLabel()
    val challengeId = "pin_${UUID.randomUUID().toString().replace("-", "")}"
    val expiresAt = System.currentTimeMillis() + AccessControlConfig.PIN_TTL_MILLIS

    val challengeRef = db.collection(PIN_CHALLENGES_COLLECTION).document(challengeId)
    val inboxRef = db.collection(ADMIN_PIN_INBOX_COLLECTION).document(challengeId)

    return try {
      val (pinCode, hashResult) =
        withContext(Dispatchers.Default) {
          val generatedPin = PasswordHasher.generateRandomPin(AccessControlConfig.PIN_LENGTH)
          val hashed = PasswordHasher.hashPassword(generatedPin)
          generatedPin to hashed
        }

      val challengePayload =
        mapOf(
          "challengeId" to challengeId,
          "accountId" to accountId,
          "displayName" to cleanName,
          "requesterUid" to uid,
          "deviceInfo" to deviceInfo,
          "pinCode" to pinCode,
          "pinHash" to hashResult.hashHex,
          "pinSalt" to hashResult.saltHex,
          "status" to PinChallenge.STATUS_PENDING,
          "expiresAt" to expiresAt,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      val inboxPayload =
        mapOf(
          "challengeId" to challengeId,
          "accountId" to accountId,
          "displayName" to cleanName,
          "requesterUid" to uid,
          "requesterEmail" to cleanEmail,
          "deviceInfo" to deviceInfo,
          "pinCode" to pinCode,
          "status" to PinChallenge.STATUS_PENDING,
          "expiresAt" to expiresAt,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )

      challengeRef.set(challengePayload).await()
      inboxRef.set(inboxPayload).await()
      Result.success(challengeId to pinCode)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, challengeRef.path, auth)
      Result.failure(e)
    }
  }

  fun observePinChallenge(challengeId: String): Flow<PinChallenge?> = flow {
    if (challengeId.isBlank()) {
      emit(null)
      return@flow
    }
    val docRef = db.collection(PIN_CHALLENGES_COLLECTION).document(challengeId)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(PinChallenge::class.java, ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          logStreamError(error, OperationType.GET, docRef.path)
          throw error
        }
    )
  }

  suspend fun completePinVerification(
    challengeId: String,
    rawAccountId: String,
    displayName: String = "",
  ): Result<Unit> {
    val uid = requireCurrentUid()
    val accountId = AccessControlConfig.normalizeAccountId(rawAccountId)
    if (challengeId.isBlank() || !AccessControlConfig.isValidAccountId(accountId)) {
      return Result.failure(IllegalArgumentException("Datos de verificación inválidos."))
    }
    val challengeRef = db.collection(PIN_CHALLENGES_COLLECTION).document(challengeId)
    val inboxRef = db.collection(ADMIN_PIN_INBOX_COLLECTION).document(challengeId)
    val userRef = db.collection(COLLECTION_NAME).document(accountId)
    val bindingRef = db.collection(USER_BINDINGS_COLLECTION).document(uid)

    return try {
      challengeRef
        .update(
          mapOf(
            "status" to PinChallenge.STATUS_VERIFIED,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      userRef
        .update(
          mapOf(
            "boundUid" to uid,
            "lastAccessAt" to System.currentTimeMillis(),
            "lastDeviceInfo" to AccessControlConfig.currentDeviceLabel(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      bindingRef
        .set(
          mapOf(
            "uid" to uid,
            "accountId" to accountId,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      appendAccessLog(
        accountId = accountId,
        displayName = displayName.ifBlank { accountId },
        eventType = AccessLogEntry.EVENT_PIN_LOGIN,
      )

      // Best-effort cleanup of Admin's pending inbox entry now that the user verified
      runCatching { inboxRef.delete().await() }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, challengeRef.path, auth)
      Result.failure(e)
    }
  }

  // --- Immutable Access History Logs (Admin-Only Read) ---

  private suspend fun appendAccessLog(
    accountId: String,
    displayName: String,
    eventType: String,
  ) {
    val uid = auth.currentUser?.uid ?: return
    val safeName = displayName.trim().ifBlank { accountId }.take(80)
    val safeDevice = AccessControlConfig.currentDeviceLabel().trim().ifBlank { "Android" }.take(120)
    val safeEvent =
      if (eventType == AccessLogEntry.EVENT_SESSION_ACCESS) {
        AccessLogEntry.EVENT_SESSION_ACCESS
      } else {
        AccessLogEntry.EVENT_PIN_LOGIN
      }
    val logId = "log_${UUID.randomUUID().toString().replace("-", "")}"
    val payload =
      mapOf(
        "accountId" to accountId,
        "displayName" to safeName,
        "uid" to uid,
        "deviceInfo" to safeDevice,
        "eventType" to safeEvent,
        "timestampMillis" to System.currentTimeMillis(),
        "createdAt" to FieldValue.serverTimestamp(),
      )
    runCatching {
      db.collection(ACCESS_LOGS_COLLECTION).document(logId).set(payload).await()
    }
  }

  fun observeAccessLogs(limitCount: Long = 100L): Flow<List<AccessLogEntry>> = flow {
    val path = ACCESS_LOGS_COLLECTION
    emitAll(
      db.collection(ACCESS_LOGS_COLLECTION)
        .orderBy("timestampMillis", Query.Direction.DESCENDING)
        .limit(limitCount.coerceIn(1L, 250L))
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(AccessLogEntry::class.java, ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.timestampMillis }
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, path)
          emit(emptyList())
        }
    )
  }

  suspend fun deleteAccessLog(logId: String): Result<Unit> {
    requireCurrentUid()
    if (logId.isBlank()) return Result.failure(IllegalArgumentException("Registro inválido."))
    val docRef = db.collection(ACCESS_LOGS_COLLECTION).document(logId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun cancelPinChallenge(challengeId: String) {
    if (challengeId.isBlank()) return
    runCatching { db.collection(PIN_CHALLENGES_COLLECTION).document(challengeId).delete().await() }
    runCatching { db.collection(ADMIN_PIN_INBOX_COLLECTION).document(challengeId).delete().await() }
  }

  // --- Admin-only PIN Inbox Operations ---

  fun observePendingAdminPins(): Flow<List<AdminPinInboxItem>> = flow {
    val path = ADMIN_PIN_INBOX_COLLECTION
    emitAll(
      db.collection(ADMIN_PIN_INBOX_COLLECTION)
        .snapshots()
        .map { snapshot ->
          val now = System.currentTimeMillis()
          snapshot
            .toObjects(AdminPinInboxItem::class.java, ServerTimestampBehavior.ESTIMATE)
            .filter { item ->
              item.status == PinChallenge.STATUS_PENDING &&
                item.pinCode.length == AccessControlConfig.PIN_LENGTH &&
                (item.expiresAt == 0L || item.expiresAt > now)
            }
            .sortedByDescending { it.createdAt?.toDate()?.time ?: it.expiresAt }
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, path)
          emit(emptyList())
        }
    )
  }

  suspend fun regenerateAdminPin(item: AdminPinInboxItem): Result<String> {
    requireCurrentUid()
    val challengeId = item.challengeId.ifBlank { item.id }
    if (challengeId.isBlank()) {
      return Result.failure(IllegalArgumentException("Solicitud de PIN inválida."))
    }
    val newExpiresAt = System.currentTimeMillis() + AccessControlConfig.PIN_TTL_MILLIS
    val challengeRef = db.collection(PIN_CHALLENGES_COLLECTION).document(challengeId)
    val inboxRef = db.collection(ADMIN_PIN_INBOX_COLLECTION).document(challengeId)

    return try {
      val (newPin, hashResult) =
        withContext(Dispatchers.Default) {
          val generatedPin = PasswordHasher.generateRandomPin(AccessControlConfig.PIN_LENGTH)
          val hashed = PasswordHasher.hashPassword(generatedPin)
          generatedPin to hashed
        }

      challengeRef
        .update(
          mapOf(
            "pinCode" to newPin,
            "pinHash" to hashResult.hashHex,
            "pinSalt" to hashResult.saltHex,
            "status" to PinChallenge.STATUS_PENDING,
            "expiresAt" to newExpiresAt,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      inboxRef
        .update(
          mapOf(
            "pinCode" to newPin,
            "status" to PinChallenge.STATUS_PENDING,
            "expiresAt" to newExpiresAt,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      Result.success(newPin)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, inboxRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun rejectAdminPin(challengeId: String): Result<Unit> {
    requireCurrentUid()
    if (challengeId.isBlank()) {
      return Result.failure(IllegalArgumentException("Solicitud de PIN inválida."))
    }
    val challengeRef = db.collection(PIN_CHALLENGES_COLLECTION).document(challengeId)
    val inboxRef = db.collection(ADMIN_PIN_INBOX_COLLECTION).document(challengeId)
    return try {
      runCatching {
        challengeRef
          .update(
            mapOf(
              "status" to PinChallenge.STATUS_REJECTED,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      inboxRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, inboxRef.path, auth)
      Result.failure(e)
    }
  }

  fun observeAdminWhatsAppPhone(): Flow<String> = flow {
    val docRef = db.collection(APP_CONFIG_COLLECTION).document(SUPPORT_DOC_ID)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.getString("whatsappPhone").orEmpty().trim()
          } else {
            ""
          }
        }
        .catch { emit("") }
    )
  }

  suspend fun saveAdminWhatsAppPhone(rawPhone: String): Result<Unit> {
    val uid = requireCurrentUid()
    val sanitized = rawPhone.trim().take(30)
    val docRef = db.collection(APP_CONFIG_COLLECTION).document(SUPPORT_DOC_ID)
    return try {
      docRef
        .set(
          mapOf(
            "whatsappPhone" to sanitized,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      Result.failure(e)
    }
  }

  fun observeGitHubUpdateRepo(): Flow<String> = flow {
    val docRef = db.collection(APP_CONFIG_COLLECTION).document(UPDATES_DOC_ID)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          val remote =
            if (snapshot.exists()) {
              snapshot.getString("githubRepo").orEmpty().trim()
            } else {
              ""
            }
          remote.ifBlank { com.example.util.GitHubReleaseUpdater.DEFAULT_GITHUB_REPO }
        }
        .catch { emit(com.example.util.GitHubReleaseUpdater.DEFAULT_GITHUB_REPO) }
    )
  }

  suspend fun saveGitHubUpdateRepo(rawRepo: String): Result<Unit> {
    val uid = requireCurrentUid()
    val trimmed = rawRepo.trim()
    val normalized =
      if (trimmed.isEmpty()) {
        ""
      } else {
        com.example.util.GitHubReleaseUpdater.normalizeRepoSlug(trimmed)
          ?: return Result.failure(
            IllegalArgumentException(
              "Formato inválido. Usá usuario/repositorio o pegá la URL de tu repo en GitHub."
            )
          )
      }
    val docRef = db.collection(APP_CONFIG_COLLECTION).document(UPDATES_DOC_ID)
    return try {
      docRef
        .set(
          mapOf(
            "githubRepo" to normalized,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      Result.failure(e)
    }
  }

  private fun logStreamError(
    error: Throwable,
    operationType: OperationType,
    path: String,
  ) {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val toLog = firestoreError ?: (error as? Exception) ?: return
    if (auth.currentUser != null) {
      handleFirestoreError(toLog, operationType, path, auth)
    }
  }

  companion object {
    const val COLLECTION_NAME = "authorizedUsers"
    const val USER_BINDINGS_COLLECTION = "userBindings"
    const val PIN_CHALLENGES_COLLECTION = "pinChallenges"
    const val ADMIN_PIN_INBOX_COLLECTION = "adminPinInbox"
    const val ACCESS_LOGS_COLLECTION = "accessLogs"
    const val APP_CONFIG_COLLECTION = "appConfig"
    const val SUPPORT_DOC_ID = "support"
    const val UPDATES_DOC_ID = "updates"
  }
}
