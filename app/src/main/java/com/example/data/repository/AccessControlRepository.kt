package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AuthorizedUser
import com.example.util.AccessControlConfig
import com.example.util.PasswordHasher
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
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
      ?: throw IllegalStateException("Debés iniciar sesión con Google primero.")

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

  fun observeAuthorizedUserByEmail(rawEmail: String): Flow<AuthorizedUser?> = flow {
    val emailId = AccessControlConfig.normalizeEmail(rawEmail)
    if (!AccessControlConfig.isValidEmailFormat(emailId)) {
      emit(null)
      return@flow
    }
    val docRef = db.collection(COLLECTION_NAME).document(emailId)
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

  suspend fun createAuthorizedUser(
    displayName: String,
    rawEmail: String,
    rawPassword: String,
    notes: String = "",
  ): Result<AuthorizedUser> {
    val adminUid = requireCurrentUid()
    val emailId = AccessControlConfig.normalizeEmail(rawEmail)
    val cleanName = displayName.trim().take(80)
    val cleanNotes = notes.trim().take(200)

    if (cleanName.isBlank()) {
      return Result.failure(IllegalArgumentException("Ingresá un nombre para el usuario."))
    }
    if (!AccessControlConfig.isValidEmailFormat(emailId)) {
      return Result.failure(IllegalArgumentException("Ingresá un correo electrónico válido."))
    }
    if (AccessControlConfig.isAdminEmail(emailId)) {
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

    val docRef = db.collection(COLLECTION_NAME).document(emailId)
    return try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        return Result.failure(
          IllegalStateException(
            "Ya existe un usuario con ese correo. Podés editarlo o cambiarle la contraseña."
          )
        )
      }

      val hashResult = withContext(Dispatchers.Default) { PasswordHasher.hashPassword(rawPassword) }
      val payload =
        mapOf(
          "email" to emailId,
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
          id = emailId,
          email = emailId,
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
    val emailId = AccessControlConfig.normalizeEmail(rawEmail)
    if (!AccessControlConfig.isValidEmailFormat(emailId)) {
      return Result.failure(IllegalArgumentException("Correo inválido."))
    }
    val docRef = db.collection(COLLECTION_NAME).document(emailId)
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
    val emailId = AccessControlConfig.normalizeEmail(rawEmail)
    val cleanName = displayName.trim().take(80)
    if (cleanName.isBlank()) {
      return Result.failure(IllegalArgumentException("El nombre no puede quedar vacío."))
    }
    val docRef = db.collection(COLLECTION_NAME).document(emailId)
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
    val emailId = AccessControlConfig.normalizeEmail(rawEmail)
    if (!AccessControlConfig.isValidEmailFormat(emailId)) {
      return Result.failure(IllegalArgumentException("Correo inválido."))
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
    val docRef = db.collection(COLLECTION_NAME).document(emailId)
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

  suspend fun deleteAuthorizedUser(rawEmail: String): Result<Unit> {
    requireCurrentUid()
    val emailId = AccessControlConfig.normalizeEmail(rawEmail)
    val docRef = db.collection(COLLECTION_NAME).document(emailId)
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

  suspend fun recordSelfAccess(rawEmail: String): Result<Unit> {
    val uid = requireCurrentUid()
    val emailId = AccessControlConfig.normalizeEmail(rawEmail)
    if (!AccessControlConfig.isValidEmailFormat(emailId)) {
      return Result.failure(IllegalArgumentException("Correo inválido."))
    }
    val docRef = db.collection(COLLECTION_NAME).document(emailId)
    return try {
      docRef
        .update(
          mapOf(
            "boundUid" to uid,
            "lastAccessAt" to System.currentTimeMillis(),
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
    const val APP_CONFIG_COLLECTION = "appConfig"
    const val SUPPORT_DOC_ID = "support"
    const val UPDATES_DOC_ID = "updates"
  }
}
