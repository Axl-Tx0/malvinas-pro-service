package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FinancialTransaction
import com.example.data.model.ProfileType
import com.example.data.model.RemoteAppConfig
import com.example.data.model.Reseller
import com.example.data.model.TransactionType
import com.example.data.model.UserSettings
import com.example.data.model.VpnAccount
import com.example.data.repository.MalvinasRepository
import com.example.util.CloudBackupHelper
import com.example.util.CloudBackupPayload
import com.example.util.Formatters
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

data class MonthlyChartPoint(
  val monthKey: String,
  val shortLabel: String,
  val fullLabel: String,
  val totalIncome: Double,
  val totalExpenses: Double,
  val netBalance: Double,
)

data class MonthlySummary(
  val totalIncome: Double = 0.0,
  val totalExpenses: Double = 0.0,
  val netBalance: Double = 0.0,
  val vpnPaidCount: Int = 0,
  val vpnUnpaidCount: Int = 0,
  val vpnDueSoonCount: Int = 0,
  val vpnPromiseCount: Int = 0,
  val vpnPromiseDueTodayCount: Int = 0,
  val vpnPendingAmount: Double = 0.0,
  val resellerCount: Int = 0,
  val resellerDueSoonCount: Int = 0,
  val totalResellerCredits: Long = 0L,
  val totalResellerAmount: Double = 0.0,
)

data class DashboardData(
  val activeProfile: ProfileType,
  val selectedMonthKey: String,
  val transactions: List<FinancialTransaction>,
  val allTransactions: List<FinancialTransaction>,
  val profileMonthlyHistory: List<MonthlyChartPoint>,
  val combinedMonthlyHistory: List<MonthlyChartPoint>,
  val vpnAccounts: List<VpnAccount>,
  val allVpnAccounts: List<VpnAccount>,
  val resellers: List<Reseller> = emptyList(),
  val allResellers: List<Reseller> = emptyList(),
  val summary: MonthlySummary,
  val settings: UserSettings = UserSettings(),
)

data class CloudSyncUiState(
  val isSyncing: Boolean = false,
  val lastReport: MalvinasRepository.CloudSyncReport? = null,
  val lastError: String? = null,
  val isCheckingLiveConfig: Boolean = false,
  val liveRemoteConfig: RemoteAppConfig? = null,
  val lastLiveCheckMillis: Long = 0L,
)

class MainViewModel(
  private val repository: MalvinasRepository,
  private val currentUserId: String,
) : ViewModel() {

  private val _activeProfile = MutableStateFlow(ProfileType.PREMIUM)
  val activeProfile: StateFlow<ProfileType> = _activeProfile.asStateFlow()

  private val _selectedMonthKey = MutableStateFlow(Formatters.currentMonthKey())
  val selectedMonthKey: StateFlow<String> = _selectedMonthKey.asStateFlow()

  private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val snackbarEvents = _snackbarEvents.asSharedFlow()

  private val _cloudSyncState = MutableStateFlow(CloudSyncUiState())
  val cloudSyncState: StateFlow<CloudSyncUiState> = _cloudSyncState.asStateFlow()

  private val transactionsState: StateFlow<UiState<List<FinancialTransaction>>> =
    repository
      .observeTransactions(currentUserId)
      .map<List<FinancialTransaction>, UiState<List<FinancialTransaction>>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "movimientos"))) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val vpnState: StateFlow<UiState<List<VpnAccount>>> =
    repository
      .observeVpnAccounts(currentUserId)
      .map<List<VpnAccount>, UiState<List<VpnAccount>>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "clientes"))) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val resellersState: StateFlow<UiState<List<Reseller>>> =
    repository
      .observeResellers(currentUserId)
      .map<List<Reseller>, UiState<List<Reseller>>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "revendedores"))) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val settingsState: StateFlow<UiState<UserSettings>> =
    repository
      .observeUserSettings(currentUserId)
      .map<UserSettings, UiState<UserSettings>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "configuración"))) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val filterState = combine(_activeProfile, _selectedMonthKey, ::Pair)
  private val catalogPairState = combine(vpnState, resellersState, ::Pair)

  val dashboardState: StateFlow<UiState<DashboardData>> =
    combine(
        filterState,
        transactionsState,
        catalogPairState,
        settingsState,
      ) { (profile, monthKey), txUi, (vpnUi, resellersUi), userSettingsUi ->
        val firstError =
          listOfNotNull(
              (txUi as? UiState.Error)?.message,
              (vpnUi as? UiState.Error)?.message,
              (resellersUi as? UiState.Error)?.message,
              (userSettingsUi as? UiState.Error)?.message,
            )
            .firstOrNull()

        if (firstError != null) {
          UiState.Error(firstError)
        } else if (
          txUi is UiState.Success &&
            vpnUi is UiState.Success &&
            resellersUi is UiState.Success &&
            userSettingsUi is UiState.Success
        ) {
          val profileAllTx = txUi.data
          val profileTx = profileAllTx.filter { it.monthKey == monthKey }
          val profileVpns = vpnUi.data
          val profileResellers = resellersUi.data

          val income =
            profileTx
              .filter { it.type == TransactionType.INCOME.id }
              .sumOf { it.amount }
          val expenses =
            profileTx
              .filter { it.type == TransactionType.EXPENSE.id }
              .sumOf { it.amount }

          val vpnPaidCount = profileVpns.count { it.isPaid }
          val vpnUnpaidCount = profileVpns.count { !it.isPaid }
          val vpnDueSoonCount =
            profileVpns.count { vpn ->
              !vpn.isPaid && Formatters.daysUntilLocalDay(vpn.expirationMillis) <= 1
            }
          val vpnPromiseCount = profileVpns.count { !it.isPaid && it.paymentPromiseMillis > 0L }
          val vpnPromiseDueTodayCount =
            profileVpns.count { vpn ->
              !vpn.isPaid &&
                vpn.paymentPromiseMillis > 0L &&
                Formatters.daysUntilLocalDay(vpn.paymentPromiseMillis) <= 0
            }
          val vpnPendingAmount = profileVpns.filterNot { it.isPaid }.sumOf { it.amountToCharge }
          val totalResellerCredits = profileResellers.sumOf { it.creditsCount.coerceAtLeast(0L) }
          val totalResellerAmount = profileResellers.sumOf { it.computedTotal }
          val resellerDueSoonCount =
            profileResellers.count { reseller ->
              reseller.expirationMillis > 0L &&
                Formatters.daysUntilLocalDay(reseller.expirationMillis) <= 3
            }

          val profileHistory =
            buildMonthlyHistory(
              transactions = profileAllTx,
              endMonthKey = monthKey,
              monthsCount = HISTORY_MONTHS_COUNT,
            )
          val combinedHistory =
            buildMonthlyHistory(
              transactions = txUi.data,
              endMonthKey = monthKey,
              monthsCount = HISTORY_MONTHS_COUNT,
            )

          UiState.Success(
            DashboardData(
              activeProfile = profile,
              selectedMonthKey = monthKey,
              transactions = profileTx,
              allTransactions = txUi.data,
              profileMonthlyHistory = profileHistory,
              combinedMonthlyHistory = combinedHistory,
              vpnAccounts = profileVpns,
              allVpnAccounts = vpnUi.data,
              resellers = profileResellers,
              allResellers = resellersUi.data,
              summary =
                MonthlySummary(
                  totalIncome = income,
                  totalExpenses = expenses,
                  netBalance = income - expenses,
                  vpnPaidCount = vpnPaidCount,
                  vpnUnpaidCount = vpnUnpaidCount,
                  vpnDueSoonCount = vpnDueSoonCount,
                  vpnPromiseCount = vpnPromiseCount,
                  vpnPromiseDueTodayCount = vpnPromiseDueTodayCount,
                  vpnPendingAmount = vpnPendingAmount,
                  resellerCount = profileResellers.size,
                  resellerDueSoonCount = resellerDueSoonCount,
                  totalResellerCredits = totalResellerCredits,
                  totalResellerAmount = totalResellerAmount,
                ),
              settings = userSettingsUi.data,
            )
          )
        } else {
          UiState.Loading
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  fun setProfile(profile: ProfileType) {
    _activeProfile.value = ProfileType.PREMIUM
  }

  fun previousMonth() {
    _selectedMonthKey.value = Formatters.shiftMonthKey(_selectedMonthKey.value, -1)
  }

  fun nextMonth() {
    _selectedMonthKey.value = Formatters.shiftMonthKey(_selectedMonthKey.value, 1)
  }

  fun saveTransaction(
    existing: FinancialTransaction?,
    profile: ProfileType,
    type: TransactionType,
    title: String,
    category: String,
    amount: Double,
    dateMillis: Long,
    notes: String,
  ) {
    val monthKey = Formatters.monthKeyFromMillis(dateMillis)
    viewModelScope.launch {
      val result =
        if (existing == null) {
          repository.addTransaction(
            FinancialTransaction(
              userId = currentUserId,
              profileType = profile.id,
              type = type.id,
              title = title,
              category = category,
              amount = amount,
              monthKey = monthKey,
              dateMillis = dateMillis,
              notes = notes,
            )
          )
        } else {
          repository.updateTransaction(
            existing.copy(
              profileType = profile.id,
              type = type.id,
              title = title,
              category = category,
              amount = amount,
              monthKey = monthKey,
              dateMillis = dateMillis,
              notes = notes,
            )
          )
        }
      if (result.isSuccess) {
        _selectedMonthKey.value = monthKey
        _activeProfile.value = ProfileType.PREMIUM
        _snackbarEvents.tryEmit("${type.label} guardado")
      } else {
        _snackbarEvents.tryEmit(
          result.exceptionOrNull()?.localizedMessage ?: "No se pudo guardar el movimiento"
        )
      }
    }
  }

  fun deleteTransaction(transactionId: String) {
    viewModelScope.launch {
      val result = repository.deleteTransaction(transactionId)
      if (result.isSuccess) {
        _snackbarEvents.tryEmit("Movimiento eliminado")
      } else {
        _snackbarEvents.tryEmit("Error al eliminar movimiento")
      }
    }
  }

  fun saveVpnAccount(
    existing: VpnAccount?,
    profile: ProfileType,
    clientName: String,
    clientPhone: String,
    vpnUsername: String,
    planName: String,
    amountToCharge: Double,
    expirationMillis: Long,
    isPaid: Boolean,
    notifyOneDayBefore: Boolean,
    paymentPromiseMillis: Long = 0L,
    paymentPromiseNote: String = "",
    notes: String,
  ) {
    val sanitizedPromiseMillis = if (isPaid) 0L else paymentPromiseMillis.coerceAtLeast(0L)
    val sanitizedPromiseNote = if (sanitizedPromiseMillis > 0L) paymentPromiseNote else ""
    viewModelScope.launch {
      val result =
        if (existing == null) {
          repository.addVpnAccount(
            VpnAccount(
              userId = currentUserId,
              profileType = profile.id,
              clientName = clientName,
              clientPhone = clientPhone,
              vpnUsername = vpnUsername,
              planName = planName,
              amountToCharge = amountToCharge,
              expirationMillis = expirationMillis,
              isPaid = isPaid,
              notifyOneDayBefore = notifyOneDayBefore,
              paymentPromiseMillis = sanitizedPromiseMillis,
              paymentPromiseNote = sanitizedPromiseNote,
              notes = notes,
            )
          )
        } else {
          repository.updateVpnAccount(
            existing.copy(
              profileType = profile.id,
              clientName = clientName,
              clientPhone = clientPhone,
              vpnUsername = vpnUsername,
              planName = planName,
              amountToCharge = amountToCharge,
              expirationMillis = expirationMillis,
              isPaid = isPaid,
              notifyOneDayBefore = notifyOneDayBefore,
              paymentPromiseMillis = sanitizedPromiseMillis,
              paymentPromiseNote = sanitizedPromiseNote,
              notes = notes,
            )
          )
        }
      if (result.isSuccess) {
        _activeProfile.value = ProfileType.PREMIUM
        _snackbarEvents.tryEmit(
          if (sanitizedPromiseMillis > 0L) {
            "Cliente guardado · Promesa el ${Formatters.formatDate(sanitizedPromiseMillis)}"
          } else {
            "Cliente guardado"
          }
        )
      } else {
        _snackbarEvents.tryEmit(
          result.exceptionOrNull()?.localizedMessage ?: "No se pudo guardar el cliente"
        )
      }
    }
  }

  fun toggleVpnPaid(vpn: VpnAccount, isPaid: Boolean) {
    viewModelScope.launch {
      val updated =
        if (isPaid) {
          vpn.copy(isPaid = true, paymentPromiseMillis = 0L, paymentPromiseNote = "")
        } else {
          vpn.copy(isPaid = false)
        }
      val result = repository.updateVpnAccount(updated)
      if (result.isSuccess) {
        _snackbarEvents.tryEmit(if (isPaid) "Cliente marcado como pagado" else "Cliente marcado como pendiente")
      } else {
        _snackbarEvents.tryEmit("No se pudo actualizar el estado del cliente")
      }
    }
  }

  fun savePaymentPromise(
    vpn: VpnAccount,
    promiseMillis: Long,
    promiseNote: String,
  ) {
    viewModelScope.launch {
      val result = repository.updatePaymentPromise(vpn, promiseMillis, promiseNote)
      if (result.isSuccess) {
        _snackbarEvents.tryEmit(
          if (promiseMillis > 0L) {
            "Promesa de pago agendada para el ${Formatters.formatDate(promiseMillis)}"
          } else {
            "Promesa de pago eliminada"
          }
        )
      } else {
        _snackbarEvents.tryEmit(
          result.exceptionOrNull()?.localizedMessage ?: "No se pudo guardar la promesa de pago"
        )
      }
    }
  }

  fun renewVpn(vpn: VpnAccount, recordIncome: Boolean? = null) {
    val activeSettings = (dashboardState.value as? UiState.Success)?.data?.settings ?: UserSettings()
    val days = activeSettings.defaultVpnDays.toInt().coerceIn(1, 365)
    val shouldRecordIncome = recordIncome ?: activeSettings.autoRecordIncomeOnPay
    val base = maxOf(vpn.expirationMillis, System.currentTimeMillis())
    val nextExp = Formatters.addDaysLocalNoon(base, days)
    val monthKey = _selectedMonthKey.value
    viewModelScope.launch {
      val result = repository.renewVpnAccount(vpn, nextExp, monthKey, shouldRecordIncome)
      if (result.isSuccess) {
        _snackbarEvents.tryEmit("Cliente renovado +$days días (${Formatters.formatDate(nextExp)})")
      } else {
        _snackbarEvents.tryEmit("Error al renovar cliente")
      }
    }
  }

  fun saveSettings(settings: UserSettings) {
    viewModelScope.launch {
      val result = repository.saveUserSettings(settings.copy(userId = currentUserId))
      if (result.isSuccess) {
        _snackbarEvents.tryEmit("Configuración guardada en la nube")
      } else {
        _snackbarEvents.tryEmit(
          result.exceptionOrNull()?.localizedMessage ?: "No se pudo guardar la configuración"
        )
      }
    }
  }

  fun syncWithGoogleCloudNow() {
    if (_cloudSyncState.value.isSyncing) return
    _cloudSyncState.value = _cloudSyncState.value.copy(isSyncing = true, lastError = null)
    viewModelScope.launch {
      val result = repository.syncCloudNow(currentUserId)
      if (result.isSuccess) {
        val report = result.getOrNull()
        _cloudSyncState.value =
          CloudSyncUiState(
            isSyncing = false,
            lastReport = report,
            lastError = null,
            isCheckingLiveConfig = _cloudSyncState.value.isCheckingLiveConfig,
            liveRemoteConfig = report?.remoteConfig ?: _cloudSyncState.value.liveRemoteConfig,
            lastLiveCheckMillis =
              if (report != null) report.syncedAtMillis else _cloudSyncState.value.lastLiveCheckMillis,
          )
        val countText = report?.totalRecords ?: 0
        _snackbarEvents.tryEmit("Nube sincronizada con tu cuenta de Google ($countText registros)")
      } else {
        val msg =
          result.exceptionOrNull()?.localizedMessage
            ?: "Sin conexión; los cambios pendientes se subirán al reconectar."
        _cloudSyncState.value =
          _cloudSyncState.value.copy(
            isSyncing = false,
            lastError = msg,
          )
        _snackbarEvents.tryEmit(msg)
      }
    }
  }

  fun checkLiveInternetUpdates(installedVersionCode: Long) {
    if (_cloudSyncState.value.isCheckingLiveConfig) return
    _cloudSyncState.value =
      _cloudSyncState.value.copy(isCheckingLiveConfig = true, lastError = null)
    viewModelScope.launch {
      val syncResult = repository.syncCloudNow(currentUserId)
      val remoteResult = repository.fetchRemoteAppConfig()
      val report = syncResult.getOrNull()
      val remoteConfig = remoteResult.getOrNull() ?: report?.remoteConfig
      val now = System.currentTimeMillis()
      val errorMsg =
        remoteResult.exceptionOrNull()?.localizedMessage
          ?: syncResult.exceptionOrNull()?.localizedMessage

      _cloudSyncState.value =
        _cloudSyncState.value.copy(
          isCheckingLiveConfig = false,
          lastReport = report ?: _cloudSyncState.value.lastReport,
          liveRemoteConfig = remoteConfig ?: _cloudSyncState.value.liveRemoteConfig,
          lastLiveCheckMillis = if (syncResult.isSuccess || remoteResult.isSuccess) now else _cloudSyncState.value.lastLiveCheckMillis,
          lastError = errorMsg,
        )

      when {
        remoteConfig != null && remoteConfig.latestVersionCode > installedVersionCode -> {
          _snackbarEvents.tryEmit(
            "Hay cambios publicados en la nube (v${remoteConfig.latestVersionName.ifBlank { remoteConfig.latestVersionCode.toString() }})"
          )
        }
        syncResult.isSuccess -> {
          _snackbarEvents.tryEmit(
            "Configuración y datos actualizados en vivo por internet (sin reinstalar)"
          )
        }
        errorMsg != null -> {
          _snackbarEvents.tryEmit(errorMsg)
        }
      }
    }
  }

  fun applyRemoteRecommendedTemplates(remoteConfig: RemoteAppConfig) {
    val currentSettings =
      (dashboardState.value as? UiState.Success)?.data?.settings ?: UserSettings(userId = currentUserId)
    val nextVpn =
      remoteConfig.recommendedVpnTemplate.trim().ifBlank { currentSettings.whatsappVpnTemplate }
    saveSettings(
      currentSettings.copy(
        whatsappVpnTemplate = nextVpn,
      )
    )
  }

  fun buildBackupJson(userEmail: String?): String? {
    val data = (dashboardState.value as? UiState.Success)?.data ?: return null
    val payload =
      CloudBackupPayload(
        exportedAtMillis = System.currentTimeMillis(),
        sourceEmail = userEmail.orEmpty(),
        settings = data.settings,
        transactions = data.allTransactions,
        vpnAccounts = data.allVpnAccounts,
        resellers = data.allResellers,
      )
    return CloudBackupHelper.toJsonString(payload)
  }

  fun notifyMessage(message: String) {
    _snackbarEvents.tryEmit(message)
  }

  fun restoreBackupFromJson(rawJson: String) {
    if (_cloudSyncState.value.isSyncing) return
    val parsed = CloudBackupHelper.fromJsonString(rawJson)
    if (parsed.isFailure) {
      _snackbarEvents.tryEmit(
        parsed.exceptionOrNull()?.localizedMessage ?: "Archivo de respaldo inválido"
      )
      return
    }
    val payload = parsed.getOrNull() ?: return
    _cloudSyncState.value = _cloudSyncState.value.copy(isSyncing = true, lastError = null)
    viewModelScope.launch {
      val result = repository.importCloudBackup(payload)
      if (result.isSuccess) {
        val addedCount = result.getOrNull() ?: 0
        _cloudSyncState.value = _cloudSyncState.value.copy(isSyncing = false)
        _snackbarEvents.tryEmit(
          if (addedCount > 0) {
            "Respaldo restaurado en tu nube de Google (+$addedCount registros y ajustes)"
          } else {
            "Ajustes restaurados; tus registros ya estaban actualizados en la nube"
          }
        )
        syncWithGoogleCloudNow()
      } else {
        val msg = result.exceptionOrNull()?.localizedMessage ?: "No se pudo restaurar el respaldo"
        _cloudSyncState.value = _cloudSyncState.value.copy(isSyncing = false, lastError = msg)
        _snackbarEvents.tryEmit(msg)
      }
    }
  }

  fun deleteVpnAccount(vpnId: String) {
    viewModelScope.launch {
      val result = repository.deleteVpnAccount(vpnId)
      if (result.isSuccess) {
        _snackbarEvents.tryEmit("Cliente eliminado")
      } else {
        _snackbarEvents.tryEmit("Error al eliminar cliente")
      }
    }
  }

  fun saveReseller(
    existing: Reseller?,
    profile: ProfileType,
    name: String,
    phone: String,
    creditsCount: Long,
    pricePerCredit: Double,
    expirationMillis: Long,
    notes: String,
  ) {
    val credits = creditsCount.coerceIn(0L, 1_000_000L)
    val price = pricePerCredit.coerceIn(0.0, 1_000_000_000.0)
    val total = credits * price
    val safeExpiration = expirationMillis.coerceAtLeast(0L)
    viewModelScope.launch {
      val result =
        if (existing == null) {
          repository.addReseller(
            Reseller(
              userId = currentUserId,
              profileType = profile.id,
              name = name,
              phone = phone,
              creditsCount = credits,
              pricePerCredit = price,
              totalAmount = total,
              expirationMillis = safeExpiration,
              notes = notes,
            )
          )
        } else {
          repository.updateReseller(
            existing.copy(
              profileType = profile.id,
              name = name,
              phone = phone,
              creditsCount = credits,
              pricePerCredit = price,
              totalAmount = total,
              expirationMillis = safeExpiration,
              notes = notes,
            )
          )
        }
      if (result.isSuccess) {
        _activeProfile.value = ProfileType.PREMIUM
        _snackbarEvents.tryEmit(
          "Revendedor guardado · Total: ${Formatters.formatCurrency(total)}"
        )
      } else {
        _snackbarEvents.tryEmit(
          result.exceptionOrNull()?.localizedMessage ?: "No se pudo guardar el revendedor"
        )
      }
    }
  }

  fun renewReseller30Days(reseller: Reseller, recordIncome: Boolean? = null) {
    val activeSettings = (dashboardState.value as? UiState.Success)?.data?.settings ?: UserSettings()
    val shouldRecordIncome = recordIncome ?: activeSettings.autoRecordIncomeOnPay
    val base = maxOf(reseller.expirationMillis, System.currentTimeMillis())
    val nextExp = Formatters.addDaysLocalNoon(base, 30)
    val monthKey = _selectedMonthKey.value
    viewModelScope.launch {
      val result =
        repository.renewResellerCycle(
          reseller = reseller,
          newExpirationMillis = nextExp,
          monthKey = monthKey,
          recordIncome = shouldRecordIncome,
        )
      if (result.isSuccess) {
        _snackbarEvents.tryEmit("Revendedor renovado +30 días (${Formatters.formatDate(nextExp)})")
      } else {
        _snackbarEvents.tryEmit("Error al renovar ciclo del revendedor")
      }
    }
  }

  fun adjustResellerCredits(reseller: Reseller, newCreditsCount: Long) {
    val clamped = newCreditsCount.coerceIn(0L, 1_000_000L)
    val updated =
      reseller.copy(
        creditsCount = clamped,
        totalAmount = clamped * reseller.pricePerCredit.coerceAtLeast(0.0),
      )
    viewModelScope.launch {
      val result = repository.updateReseller(updated)
      if (result.isFailure) {
        _snackbarEvents.tryEmit("No se pudieron actualizar los créditos")
      }
    }
  }

  fun deleteReseller(resellerId: String) {
    viewModelScope.launch {
      val result = repository.deleteReseller(resellerId)
      if (result.isSuccess) {
        _snackbarEvents.tryEmit("Revendedor eliminado")
      } else {
        _snackbarEvents.tryEmit("Error al eliminar revendedor")
      }
    }
  }

  private fun unwrapFirestoreMessage(error: Throwable, resourceName: String): String {
    val firestoreError =
      generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val rootError = firestoreError ?: error
    Log.w(TAG, "Error observing $resourceName", rootError)
    return rootError.localizedMessage ?: "No se pudieron cargar los $resourceName"
  }

  companion object {
    private const val TAG = "MainViewModel"
    private const val STOP_TIMEOUT_MILLIS = 5000L
    private const val HISTORY_MONTHS_COUNT = 12

    fun buildMonthlyHistory(
      transactions: List<FinancialTransaction>,
      endMonthKey: String,
      monthsCount: Int = HISTORY_MONTHS_COUNT,
    ): List<MonthlyChartPoint> {
      val byMonth = transactions.groupBy { it.monthKey }
      return Formatters.recentMonthKeys(endMonthKey, monthsCount).map { key ->
        val monthTx = byMonth[key].orEmpty()
        val income =
          monthTx.filter { it.type == TransactionType.INCOME.id }.sumOf { it.amount }
        val expenses =
          monthTx.filter { it.type == TransactionType.EXPENSE.id }.sumOf { it.amount }
        MonthlyChartPoint(
          monthKey = key,
          shortLabel = Formatters.formatShortMonthLabel(key),
          fullLabel = Formatters.formatMonthTitle(key),
          totalIncome = income,
          totalExpenses = expenses,
          netBalance = income - expenses,
        )
      }
    }
  }
}
