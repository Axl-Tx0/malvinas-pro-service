package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.FinancialTransaction
import com.example.data.model.RemoteAppConfig
import com.example.data.model.Reseller
import com.example.data.model.TransactionType
import com.example.data.model.UserSettings
import com.example.data.model.VpnAccount
import com.example.util.CloudBackupPayload
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MalvinasRepository(
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

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("El usuario debe iniciar sesión con Google primero.")

  // --- Transactions (Monthly Income & Expenses) ---

  fun observeTransactions(userId: String): Flow<List<FinancialTransaction>> = flow {
    val path = "users/$userId/transactions"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("transactions")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(FinancialTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.dateMillis }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  suspend fun addTransaction(transaction: FinancialTransaction): Result<String> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("transactions").document()
    val payload =
      mapOf(
        "userId" to uid,
        "profileType" to transaction.profileType,
        "type" to transaction.type,
        "title" to transaction.title.trim(),
        "category" to transaction.category.trim(),
        "amount" to transaction.amount,
        "monthKey" to transaction.monthKey,
        "dateMillis" to transaction.dateMillis,
        "linkedClientId" to transaction.linkedClientId,
        "notes" to transaction.notes.trim(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateTransaction(transaction: FinancialTransaction): Result<Unit> {
    val uid = requireUserId()
    require(transaction.id.isNotBlank()) { "El ID del movimiento es requerido." }
    val docRef =
      db.collection("users").document(uid).collection("transactions").document(transaction.id)
    val updates =
      mapOf(
        "profileType" to transaction.profileType,
        "type" to transaction.type,
        "title" to transaction.title.trim(),
        "category" to transaction.category.trim(),
        "amount" to transaction.amount,
        "monthKey" to transaction.monthKey,
        "dateMillis" to transaction.dateMillis,
        "linkedClientId" to transaction.linkedClientId,
        "notes" to transaction.notes.trim(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteTransaction(transactionId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef =
      db.collection("users").document(uid).collection("transactions").document(transactionId)
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

  // --- VPN Accounts (Expiration Dates, Charges & 1-Day Alerts) ---

  fun observeVpnAccounts(userId: String): Flow<List<VpnAccount>> = flow {
    val path = "users/$userId/vpnAccounts"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("vpnAccounts")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(VpnAccount::class.java, ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.expirationMillis }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  suspend fun addVpnAccount(vpn: VpnAccount): Result<String> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("vpnAccounts").document()
    val payload =
      mapOf(
        "userId" to uid,
        "profileType" to vpn.profileType,
        "clientName" to vpn.clientName.trim(),
        "clientPhone" to vpn.clientPhone.trim(),
        "vpnUsername" to vpn.vpnUsername.trim(),
        "planName" to vpn.planName.trim(),
        "amountToCharge" to vpn.amountToCharge,
        "expirationMillis" to vpn.expirationMillis,
        "isPaid" to vpn.isPaid,
        "notifyOneDayBefore" to vpn.notifyOneDayBefore,
        "paymentPromiseMillis" to vpn.paymentPromiseMillis.coerceAtLeast(0L),
        "paymentPromiseNote" to vpn.paymentPromiseNote.trim().take(160),
        "notes" to vpn.notes.trim(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateVpnAccount(vpn: VpnAccount): Result<Unit> {
    val uid = requireUserId()
    require(vpn.id.isNotBlank()) { "El ID de cuenta VPN es requerido." }
    val docRef = db.collection("users").document(uid).collection("vpnAccounts").document(vpn.id)
    val updates =
      mapOf(
        "profileType" to vpn.profileType,
        "clientName" to vpn.clientName.trim(),
        "clientPhone" to vpn.clientPhone.trim(),
        "vpnUsername" to vpn.vpnUsername.trim(),
        "planName" to vpn.planName.trim(),
        "amountToCharge" to vpn.amountToCharge,
        "expirationMillis" to vpn.expirationMillis,
        "isPaid" to vpn.isPaid,
        "notifyOneDayBefore" to vpn.notifyOneDayBefore,
        "paymentPromiseMillis" to vpn.paymentPromiseMillis.coerceAtLeast(0L),
        "paymentPromiseNote" to vpn.paymentPromiseNote.trim().take(160),
        "notes" to vpn.notes.trim(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun renewVpnAccount(
    vpn: VpnAccount,
    newExpirationMillis: Long,
    monthKey: String,
    recordIncome: Boolean,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Unit> {
    val uid = requireUserId()
    val vpnRef = db.collection("users").document(uid).collection("vpnAccounts").document(vpn.id)
    return try {
      val batch = db.batch()
      batch.update(
        vpnRef,
        mapOf(
          "expirationMillis" to newExpirationMillis,
          "isPaid" to true,
          "paymentPromiseMillis" to 0L,
          "paymentPromiseNote" to "",
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      if (recordIncome && vpn.amountToCharge > 0.0) {
        val txRef = db.collection("users").document(uid).collection("transactions").document()
        batch.set(
          txRef,
          mapOf(
            "userId" to uid,
            "profileType" to vpn.profileType,
            "type" to TransactionType.INCOME.id,
            "title" to "Cliente: ${vpn.clientName} (@${vpn.vpnUsername})".take(100),
            "category" to "Clientes",
            "amount" to vpn.amountToCharge,
            "monthKey" to monthKey,
            "dateMillis" to nowMillis,
            "linkedClientId" to vpn.id,
            "notes" to "Renovación cliente",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, vpnRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updatePaymentPromise(
    vpn: VpnAccount,
    promiseMillis: Long,
    promiseNote: String,
  ): Result<Unit> {
    val uid = requireUserId()
    require(vpn.id.isNotBlank()) { "El ID de cuenta VPN es requerido." }
    val docRef = db.collection("users").document(uid).collection("vpnAccounts").document(vpn.id)
    val sanitizedMillis = promiseMillis.coerceAtLeast(0L)
    val sanitizedNote = if (sanitizedMillis > 0L) promiseNote.trim().take(160) else ""
    val updates =
      buildMap<String, Any> {
        put("paymentPromiseMillis", sanitizedMillis)
        put("paymentPromiseNote", sanitizedNote)
        if (sanitizedMillis > 0L) {
          put("isPaid", false)
        }
        put("updatedAt", FieldValue.serverTimestamp())
      }
    return try {
      docRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteVpnAccount(vpnId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("vpnAccounts").document(vpnId)
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

  // --- Resellers (Credit Counts, Unit Price & Total Value) ---

  fun observeResellers(userId: String): Flow<List<Reseller>> = flow {
    val path = "users/$userId/resellers"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("resellers")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(Reseller::class.java, ServerTimestampBehavior.ESTIMATE)
            .sortedWith(
              compareBy<Reseller> {
                if (it.expirationMillis > 0L) it.expirationMillis else Long.MAX_VALUE
              }.thenByDescending { it.computedTotal }.thenBy { it.name.lowercase() }
            )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  suspend fun addReseller(reseller: Reseller): Result<String> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("resellers").document()
    val credits = reseller.creditsCount.coerceIn(0L, 1_000_000L)
    val price = reseller.pricePerCredit.coerceIn(0.0, 1_000_000_000.0)
    val total = (credits * price).coerceIn(0.0, 1_000_000_000_000.0)
    val payload =
      mapOf(
        "userId" to uid,
        "profileType" to reseller.profileType,
        "name" to reseller.name.trim().take(80),
        "phone" to reseller.phone.trim().take(30),
        "creditsCount" to credits,
        "pricePerCredit" to price,
        "totalAmount" to total,
        "expirationMillis" to reseller.expirationMillis.coerceAtLeast(0L),
        "notes" to reseller.notes.trim().take(300),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun updateReseller(reseller: Reseller): Result<Unit> {
    val uid = requireUserId()
    require(reseller.id.isNotBlank()) { "El ID del revendedor es requerido." }
    val docRef = db.collection("users").document(uid).collection("resellers").document(reseller.id)
    val credits = reseller.creditsCount.coerceIn(0L, 1_000_000L)
    val price = reseller.pricePerCredit.coerceIn(0.0, 1_000_000_000.0)
    val total = (credits * price).coerceIn(0.0, 1_000_000_000_000.0)
    val updates =
      mapOf(
        "profileType" to reseller.profileType,
        "name" to reseller.name.trim().take(80),
        "phone" to reseller.phone.trim().take(30),
        "creditsCount" to credits,
        "pricePerCredit" to price,
        "totalAmount" to total,
        "expirationMillis" to reseller.expirationMillis.coerceAtLeast(0L),
        "notes" to reseller.notes.trim().take(300),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun renewResellerCycle(
    reseller: Reseller,
    newExpirationMillis: Long,
    monthKey: String,
    recordIncome: Boolean,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Unit> {
    val uid = requireUserId()
    require(reseller.id.isNotBlank()) { "El ID del revendedor es requerido." }
    val resellerRef = db.collection("users").document(uid).collection("resellers").document(reseller.id)
    return try {
      val batch = db.batch()
      batch.update(
        resellerRef,
        mapOf(
          "expirationMillis" to newExpirationMillis.coerceAtLeast(0L),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      val amount = reseller.computedTotal
      if (recordIncome && amount > 0.0) {
        val txRef = db.collection("users").document(uid).collection("transactions").document()
        batch.set(
          txRef,
          mapOf(
            "userId" to uid,
            "profileType" to reseller.profileType,
            "type" to TransactionType.INCOME.id,
            "title" to "Revendedor: ${reseller.name} (${reseller.creditsCount} créd.)".take(100),
            "category" to "Revendedores",
            "amount" to amount,
            "monthKey" to monthKey,
            "dateMillis" to nowMillis,
            "linkedClientId" to reseller.id,
            "notes" to "Pago ciclo 30 días revendedor",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, resellerRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun deleteReseller(resellerId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("resellers").document(resellerId)
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

  // --- User Settings (WhatsApp Templates, Reminder Hour & Defaults) ---

  fun observeUserSettings(userId: String): Flow<UserSettings> = flow {
    val docRef =
      db.collection("users")
        .document(userId)
        .collection("settings")
        .document(UserSettings.PREFERENCES_DOC_ID)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(UserSettings::class.java, ServerTimestampBehavior.ESTIMATE)
              ?: UserSettings(userId = userId)
          } else {
            UserSettings(userId = userId)
          }
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.GET, docRef.path, userId)
          throw error
        }
    )
  }

  suspend fun saveUserSettings(settings: UserSettings): Result<Unit> {
    val uid = requireUserId()
    val docRef =
      db.collection("users")
        .document(uid)
        .collection("settings")
        .document(UserSettings.PREFERENCES_DOC_ID)
    val sanitizedBusiness =
      settings.businessName.trim().ifBlank { UserSettings.DEFAULT_BUSINESS_NAME }.take(80)
    val sanitizedClientTemplate =
      settings.whatsappClientTemplate.trim().ifBlank { UserSettings.DEFAULT_CLIENT_TEMPLATE }.take(1500)
    val sanitizedVpnTemplate =
      settings.whatsappVpnTemplate.trim().ifBlank { UserSettings.DEFAULT_VPN_TEMPLATE }.take(1500)
    val sanitizedCountry = settings.defaultCountryCode.filter { it.isDigit() }.take(10)
    val clampedHour = settings.reminderHour.coerceIn(0L, 23L)
    val clampedVpnDays = settings.defaultVpnDays.coerceIn(1L, 365L)

    return try {
      val existingSnapshot = docRef.get().await()
      if (!existingSnapshot.exists()) {
        val createPayload =
          mapOf(
            "userId" to uid,
            "businessName" to sanitizedBusiness,
            "whatsappClientTemplate" to sanitizedClientTemplate,
            "whatsappVpnTemplate" to sanitizedVpnTemplate,
            "defaultCountryCode" to sanitizedCountry,
            "reminderHour" to clampedHour,
            "defaultVpnDays" to clampedVpnDays,
            "autoRecordIncomeOnPay" to settings.autoRecordIncomeOnPay,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(createPayload).await()
      } else {
        val updatePayload =
          mapOf(
            "businessName" to sanitizedBusiness,
            "whatsappClientTemplate" to sanitizedClientTemplate,
            "whatsappVpnTemplate" to sanitizedVpnTemplate,
            "defaultCountryCode" to sanitizedCountry,
            "reminderHour" to clampedHour,
            "defaultVpnDays" to clampedVpnDays,
            "autoRecordIncomeOnPay" to settings.autoRecordIncomeOnPay,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.update(updatePayload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      Result.failure(e)
    }
  }

  // --- Cloud Sync Verification & Google Account Backup Restore ---

  data class CloudSyncReport(
    val syncedAtMillis: Long,
    val transactionsCount: Int,
    val vpnAccountsCount: Int,
    val resellersCount: Int = 0,
    val hasSettingsDoc: Boolean,
    val isFromServer: Boolean,
    val remoteConfig: RemoteAppConfig? = null,
  ) {
    val totalRecords: Int
      get() = transactionsCount + vpnAccountsCount + resellersCount + if (hasSettingsDoc) 1 else 0
  }

  suspend fun fetchRemoteAppConfig(): Result<RemoteAppConfig?> {
    requireUserId()
    val docRef = db.collection("appConfig").document("public")
    return try {
      val snapshot = docRef.get(Source.SERVER).await()
      val config =
        if (snapshot.exists()) {
          snapshot.toObject(RemoteAppConfig::class.java)
        } else {
          null
        }
      Result.success(config)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun syncCloudNow(userId: String): Result<CloudSyncReport> {
    val uid = requireUserId()
    if (uid != userId) {
      return Result.failure(IllegalStateException("La sesión activa de Google no coincide."))
    }
    val userRef = db.collection("users").document(uid)
    return try {
      db.waitForPendingWrites().await()
      val txSnap = userRef.collection("transactions").get(Source.SERVER).await()
      val vpnSnap = userRef.collection("vpnAccounts").get(Source.SERVER).await()
      val resellersSnap = userRef.collection("resellers").get(Source.SERVER).await()
      val settingsSnap =
        userRef.collection("settings").document(UserSettings.PREFERENCES_DOC_ID).get(Source.SERVER).await()
      val remoteConfig =
        runCatching {
          val cfgSnap = db.collection("appConfig").document("public").get(Source.SERVER).await()
          if (cfgSnap.exists()) cfgSnap.toObject(RemoteAppConfig::class.java) else null
        }.getOrNull()
      Result.success(
        CloudSyncReport(
          syncedAtMillis = System.currentTimeMillis(),
          transactionsCount = txSnap.size(),
          vpnAccountsCount = vpnSnap.size(),
          resellersCount = resellersSnap.size(),
          hasSettingsDoc = settingsSnap.exists(),
          isFromServer = true,
          remoteConfig = remoteConfig,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, userRef.path, auth)
      Result.failure(e)
    }
  }

  suspend fun importCloudBackup(backup: CloudBackupPayload): Result<Int> {
    val uid = requireUserId()
    val userRef = db.collection("users").document(uid)
    return try {
      val settingsResult = saveUserSettings(backup.settings.copy(userId = uid))
      if (settingsResult.isFailure) {
        return Result.failure(
          settingsResult.exceptionOrNull() ?: IllegalStateException("No se pudieron restaurar los ajustes.")
        )
      }

      val existingTxKeys =
        userRef.collection("transactions").get().await().toObjects(FinancialTransaction::class.java)
          .map { "${it.profileType}|${it.type}|${it.title}|${it.amount}|${it.monthKey}|${it.dateMillis}" }
          .toHashSet()
      val existingVpnKeys =
        userRef.collection("vpnAccounts").get().await().toObjects(VpnAccount::class.java)
          .map { "${it.profileType}|${it.vpnUsername.lowercase()}|${it.expirationMillis}" }
          .toHashSet()
      val existingResellerKeys =
        userRef.collection("resellers").get().await().toObjects(Reseller::class.java)
          .map { "${it.profileType}|${it.name.lowercase()}" }
          .toHashSet()

      val ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

      backup.transactions.forEach { tx ->
        val key = "${tx.profileType}|${tx.type}|${tx.title}|${tx.amount}|${tx.monthKey}|${tx.dateMillis}"
        if (existingTxKeys.add(key)) {
          val ref = userRef.collection("transactions").document()
          ops.add(
            ref to
              mapOf(
                "userId" to uid,
                "profileType" to tx.profileType,
                "type" to tx.type,
                "title" to tx.title.trim().take(100),
                "category" to tx.category.trim().take(50),
                "amount" to tx.amount.coerceIn(0.01, 1_000_000_000.0),
                "monthKey" to tx.monthKey,
                "dateMillis" to tx.dateMillis.coerceAtLeast(0L),
                "linkedClientId" to tx.linkedClientId.take(128),
                "notes" to tx.notes.trim().take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
        }
      }

      backup.vpnAccounts.forEach { vpn ->
        val key = "${vpn.profileType}|${vpn.vpnUsername.lowercase()}|${vpn.expirationMillis}"
        if (existingVpnKeys.add(key)) {
          val ref = userRef.collection("vpnAccounts").document()
          ops.add(
            ref to
              mapOf(
                "userId" to uid,
                "profileType" to vpn.profileType,
                "clientName" to vpn.clientName.trim().take(80),
                "clientPhone" to vpn.clientPhone.trim().take(30),
                "vpnUsername" to vpn.vpnUsername.trim().take(60),
                "planName" to vpn.planName.trim().take(60),
                "amountToCharge" to vpn.amountToCharge.coerceIn(0.0, 1_000_000_000.0),
                "expirationMillis" to vpn.expirationMillis.coerceAtLeast(0L),
                "isPaid" to vpn.isPaid,
                "notifyOneDayBefore" to vpn.notifyOneDayBefore,
                "paymentPromiseMillis" to vpn.paymentPromiseMillis.coerceAtLeast(0L),
                "paymentPromiseNote" to vpn.paymentPromiseNote.trim().take(160),
                "notes" to vpn.notes.trim().take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
        }
      }

      backup.resellers.forEach { reseller ->
        val key = "${reseller.profileType}|${reseller.name.lowercase()}"
        if (existingResellerKeys.add(key)) {
          val ref = userRef.collection("resellers").document()
          val credits = reseller.creditsCount.coerceIn(0L, 1_000_000L)
          val price = reseller.pricePerCredit.coerceIn(0.0, 1_000_000_000.0)
          ops.add(
            ref to
              mapOf(
                "userId" to uid,
                "profileType" to reseller.profileType,
                "name" to reseller.name.trim().take(80),
                "phone" to reseller.phone.trim().take(30),
                "creditsCount" to credits,
                "pricePerCredit" to price,
                "totalAmount" to (credits * price).coerceIn(0.0, 1_000_000_000_000.0),
                "expirationMillis" to reseller.expirationMillis.coerceAtLeast(0L),
                "notes" to reseller.notes.trim().take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
        }
      }

      ops.chunked(400).forEach { chunk ->
        val batch = db.batch()
        chunk.forEach { (ref, payload) -> batch.set(ref, payload) }
        batch.commit().await()
      }

      Result.success(ops.size)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userRef.path, auth)
      Result.failure(e)
    }
  }

  private fun logStreamErrorIfActive(
    error: Throwable,
    operationType: OperationType,
    path: String,
    expectedUid: String,
  ) {
    val firestoreError =
      generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val toLog = firestoreError ?: (error as? Exception) ?: return
    if (auth.currentUser?.uid == expectedUid) {
      handleFirestoreError(toLog, operationType, path, auth)
    }
  }
}
