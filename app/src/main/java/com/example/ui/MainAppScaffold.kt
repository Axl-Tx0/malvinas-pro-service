package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.model.FinancialTransaction
import com.example.data.model.Reseller
import com.example.data.model.UserSettings
import com.example.data.model.VpnAccount
import com.example.data.repository.MalvinasRepository
import com.example.notifications.VpnNotificationManager
import com.example.ui.auth.signOut
import com.example.ui.components.AccountAndNotificationDialog
import com.example.ui.components.AlbicelesteRibbonStripe
import com.example.ui.components.MonthSelectorBar
import com.example.ui.components.PaymentPromiseSheet
import com.example.ui.components.ResellerEditorSheet
import com.example.ui.components.TransactionEditorSheet
import com.example.ui.components.UserAccessAdminDialog
import com.example.ui.components.VpnEditorSheet
import com.example.ui.screens.FinancialSummaryScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.ResellersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VpnScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.UiState
import com.example.data.repository.AccessControlRepository
import com.example.util.AppUpdateHelper
import com.example.util.CloudBackupHelper
import com.example.util.GitHubReleaseUpdater
import com.example.util.GitHubUpdateUiState
import com.example.util.WhatsAppHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ROUTE_OVERVIEW = "resumen"
const val ROUTE_FINANCIAL_SUMMARY = "finanzas"
const val ROUTE_VPN = "vpn"
const val ROUTE_RESELLERS = "revendedores"
const val ROUTE_SETTINGS = "ajustes"

private data class NavTab(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val fabLabel: String,
)

private val mainTabs =
  listOf(
    NavTab(
      route = ROUTE_OVERVIEW,
      label = "Resumen",
      selectedIcon = Icons.Filled.AccountBalanceWallet,
      unselectedIcon = Icons.Outlined.AccountBalanceWallet,
      fabLabel = "Movimiento",
    ),
    NavTab(
      route = ROUTE_FINANCIAL_SUMMARY,
      label = "Finanzas",
      selectedIcon = Icons.Filled.BarChart,
      unselectedIcon = Icons.Outlined.BarChart,
      fabLabel = "Movimiento",
    ),
    NavTab(
      route = ROUTE_VPN,
      label = "Clientes",
      selectedIcon = Icons.Filled.People,
      unselectedIcon = Icons.Outlined.People,
      fabLabel = "Cliente",
    ),
    NavTab(
      route = ROUTE_RESELLERS,
      label = "Revend.",
      selectedIcon = Icons.Filled.Storefront,
      unselectedIcon = Icons.Outlined.Storefront,
      fabLabel = "Revendedor",
    ),
    NavTab(
      route = ROUTE_SETTINGS,
      label = "Ajustes",
      selectedIcon = Icons.Filled.Settings,
      unselectedIcon = Icons.Outlined.Settings,
      fabLabel = "",
    ),
  )

private sealed interface DeleteTarget {
  data class Transaction(val item: FinancialTransaction) : DeleteTarget

  data class Vpn(val item: VpnAccount) : DeleteTarget

  data class ResellerItem(val item: Reseller) : DeleteTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
  currentUserId: String,
  isAdmin: Boolean = false,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  val viewModel: MainViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            MainViewModel(MalvinasRepository(db), currentUserId)
          }
        },
    )

  val dashboardUi by viewModel.dashboardState.collectAsStateWithLifecycle()
  val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
  val selectedMonthKey by viewModel.selectedMonthKey.collectAsStateWithLifecycle()
  val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(viewModel) {
    viewModel.snackbarEvents.collect { msg -> snackbarHostState.showSnackbar(msg) }
  }

  // Installed app version & GitHub Releases updater
  val installedVersionInfo = remember(context) { AppUpdateHelper.getInstalledVersionInfo(context) }
  val accessControlRepo =
    remember(context) {
      val databaseId = context.getString(R.string.firestore_database_id)
      AccessControlRepository(FirebaseFirestore.getInstance(databaseId))
    }
  val githubRepo by
    accessControlRepo
      .observeGitHubUpdateRepo()
      .collectAsStateWithLifecycle(initialValue = GitHubReleaseUpdater.DEFAULT_GITHUB_REPO)
  var githubUpdateState by remember { mutableStateOf<GitHubUpdateUiState>(GitHubUpdateUiState.Idle) }
  var isSavingGitHubRepo by remember { mutableStateOf(false) }
  var dismissedGitHubTag by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(githubRepo) {
    if (GitHubReleaseUpdater.normalizeRepoSlug(githubRepo) != null) {
      val result =
        GitHubReleaseUpdater.checkLatestRelease(
          repoSlug = githubRepo,
          installedVersionName = installedVersionInfo.versionName,
        )
      // Update silently on launch only if we found a new version or confirmed up-to-date
      if (result is GitHubUpdateUiState.UpdateAvailable || result is GitHubUpdateUiState.UpToDate) {
        githubUpdateState = result
      }
    }
  }

  val exportBackupLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      if (uri != null) {
        val json = viewModel.buildBackupJson(Firebase.auth.currentUser?.email)
        if (json == null) {
          viewModel.notifyMessage("Esperá a que carguen tus datos antes de exportar")
        } else {
          coroutineScope.launch {
            val writeOk =
              withContext(Dispatchers.IO) {
                runCatching {
                  context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                  } ?: error("No se pudo abrir destino")
                }.isSuccess
              }
            viewModel.notifyMessage(
              if (writeOk) {
                "Copia de seguridad guardada correctamente"
              } else {
                "No se pudo escribir el archivo de respaldo"
              }
            )
          }
        }
      }
    }

  val importBackupLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        coroutineScope.launch {
          val rawText =
            withContext(Dispatchers.IO) {
              runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                  it.readText()
                }
              }.getOrNull()
            }
          if (rawText.isNullOrBlank()) {
            viewModel.notifyMessage("No se pudo leer el archivo seleccionado")
          } else {
            viewModel.restoreBackupFromJson(rawText)
          }
        }
      }
    }

  var hasNotificationPermission by remember {
    mutableStateOf(VpnNotificationManager.hasNotificationPermission(context))
  }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotificationPermission = granted
    }
  val requestNotificationPermission: () -> Unit = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      hasNotificationPermission = true
    }
  }

  // Sync 1-day-before VPN local alarms whenever VPN accounts or settings update
  val currentSettings = (dashboardUi as? UiState.Success)?.data?.settings ?: UserSettings()
  val allVpns = (dashboardUi as? UiState.Success)?.data?.allVpnAccounts.orEmpty()
  LaunchedEffect(allVpns, currentSettings) {
    if (allVpns.isNotEmpty()) {
      VpnNotificationManager.syncReminders(context, allVpns, currentSettings)
    }
  }

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: ROUTE_OVERVIEW
  val currentTab = mainTabs.firstOrNull { it.route == currentRoute } ?: mainTabs.first()

  var showAccountDialog by remember { mutableStateOf(false) }
  var showUserAccessAdminDialog by remember { mutableStateOf(false) }
  var editingTransaction by remember { mutableStateOf<FinancialTransaction?>(null) }
  var showTransactionSheet by remember { mutableStateOf(false) }
  var editingVpn by remember { mutableStateOf<VpnAccount?>(null) }
  var showVpnSheet by remember { mutableStateOf(false) }
  var promiseTargetVpn by remember { mutableStateOf<VpnAccount?>(null) }
  var editingReseller by remember { mutableStateOf<Reseller?>(null) }
  var showResellerSheet by remember { mutableStateOf(false) }
  var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }

  val summary = (dashboardUi as? UiState.Success)?.data?.summary
  val vpnDueBadgeCount = (summary?.vpnDueSoonCount ?: 0) + (summary?.vpnPromiseDueTodayCount ?: 0)
  val resellerDueBadgeCount = summary?.resellerDueSoonCount ?: 0

  val navigateToTab: (String) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        Column {
          CenterAlignedTopAppBar(
            title = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                  painter = painterResource(id = R.drawable.malvinas_logo),
                  contentDescription = "Logo Malvinas Pro Service",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(34.dp).clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.Start) {
                  Text(
                    text = "Malvinas Pro Service",
                    style = MaterialTheme.typography.titleLarge,
                  )
                  Text(
                    text =
                      if (isAdmin) {
                        "${activeProfile.label} · Admin"
                      } else {
                        "${activeProfile.label} · Argentina"
                      },
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      if (activeProfile.id == "premium" || isAdmin) {
                        MaterialTheme.colorScheme.secondary
                      } else {
                        MaterialTheme.colorScheme.primary
                      },
                  )
                }
              }
            },
            actions = {
              IconButton(
                onClick = { showAccountDialog = true },
                modifier = Modifier.testTag("account_menu_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.AccountCircle,
                  contentDescription = "Cuenta y notificaciones",
                )
              }
            },
            colors =
              TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
              ),
          )
          AlbicelesteRibbonStripe(height = 4.dp, showSunBadge = true)
        }
      },
      bottomBar = {
        if (!useRail) {
          NavigationBar {
            mainTabs.forEach { tab ->
              val selected = currentRoute == tab.route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  val badgeCount =
                    when (tab.route) {
                      ROUTE_VPN -> vpnDueBadgeCount
                      ROUTE_RESELLERS -> resellerDueBadgeCount
                      else -> 0
                    }
                  if (badgeCount > 0) {
                    BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                      Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                      )
                    }
                  } else {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = tab.label,
                    )
                  }
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("nav_tab_${tab.route}"),
              )
            }
          }
        }
      },
      floatingActionButton = {
        if (currentRoute != ROUTE_SETTINGS) {
          ExtendedFloatingActionButton(
            onClick = {
              when (currentRoute) {
                ROUTE_OVERVIEW,
                ROUTE_FINANCIAL_SUMMARY -> {
                  editingTransaction = null
                  showTransactionSheet = true
                }
                ROUTE_VPN -> {
                  editingVpn = null
                  showVpnSheet = true
                }
                ROUTE_RESELLERS -> {
                  editingReseller = null
                  showResellerSheet = true
                }
              }
            },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(currentTab.fabLabel) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.testTag("main_fab_${currentRoute}"),
          )
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            mainTabs.forEach { tab ->
              val selected = currentRoute == tab.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  val badgeCount =
                    when (tab.route) {
                      ROUTE_VPN -> vpnDueBadgeCount
                      ROUTE_RESELLERS -> resellerDueBadgeCount
                      else -> 0
                    }
                  if (badgeCount > 0) {
                    BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                      Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                      )
                    }
                  } else {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = tab.label,
                    )
                  }
                },
                label = { Text(tab.label) },
              )
            }
          }
        }

        Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          if (currentRoute == ROUTE_OVERVIEW) {
            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .widthIn(max = 720.dp)
                  .padding(horizontal = 16.dp, vertical = 4.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              MonthSelectorBar(
                monthKey = selectedMonthKey,
                onPreviousMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
              )
            }
          }

          when (val state = dashboardUi) {
            UiState.Loading -> {
              Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator()
              }
            }
            is UiState.Error -> {
              Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = state.message,
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
            is UiState.Success -> {
              Box(modifier = Modifier.weight(1f).widthIn(max = 720.dp)) {
                NavHost(
                  navController = navController,
                  startDestination = ROUTE_OVERVIEW,
                ) {
                  composable(ROUTE_OVERVIEW) {
                    OverviewScreen(
                      data = state.data,
                      onEditTransaction = { tx ->
                        editingTransaction = tx
                        showTransactionSheet = true
                      },
                      onDeleteTransaction = { tx -> deleteTarget = DeleteTarget.Transaction(tx) },
                      onOpenVpnTab = { navigateToTab(ROUTE_VPN) },
                      onOpenResellersTab = { navigateToTab(ROUTE_RESELLERS) },
                      onOpenFinancialSummary = { navigateToTab(ROUTE_FINANCIAL_SUMMARY) },
                    )
                  }
                  composable(ROUTE_FINANCIAL_SUMMARY) {
                    FinancialSummaryScreen(
                      data = state.data,
                      onAddTransaction = {
                        editingTransaction = null
                        showTransactionSheet = true
                      },
                    )
                  }
                  composable(ROUTE_VPN) {
                    VpnScreen(
                      data = state.data,
                      hasNotificationPermission = hasNotificationPermission,
                      onRequestNotificationPermission = requestNotificationPermission,
                      onTogglePaid = viewModel::toggleVpnPaid,
                      onRenew30Days = { vpn ->
                        viewModel.renewVpn(vpn, recordIncome = state.data.settings.autoRecordIncomeOnPay)
                      },
                      onSendWhatsApp = { vpn ->
                        val msg =
                          WhatsAppHelper.buildVpnReminderMessage(
                            vpn = vpn,
                            settings = state.data.settings,
                          )
                        if (
                          !WhatsAppHelper.openWhatsApp(
                            context = context,
                            rawPhone = vpn.clientPhone,
                            message = msg,
                            defaultCountryCode = state.data.settings.defaultCountryCode,
                          )
                        ) {
                          coroutineScope.launch {
                            snackbarHostState.showSnackbar("No se pudo abrir WhatsApp en este dispositivo")
                          }
                        }
                      },
                      onEditVpn = { vpn ->
                        editingVpn = vpn
                        showVpnSheet = true
                      },
                      onOpenPromiseEditor = { vpn -> promiseTargetVpn = vpn },
                      onClearPromise = { vpn ->
                        VpnNotificationManager.cancelPaymentPromiseReminder(context, vpn.id)
                        viewModel.savePaymentPromise(vpn, 0L, "")
                      },
                      onDeleteVpn = { vpn -> deleteTarget = DeleteTarget.Vpn(vpn) },
                    )
                  }
                  composable(ROUTE_RESELLERS) {
                    ResellersScreen(
                      data = state.data,
                      onAdjustCredits = viewModel::adjustResellerCredits,
                      onRenew30Days = { reseller ->
                        viewModel.renewReseller30Days(
                          reseller = reseller,
                          recordIncome = state.data.settings.autoRecordIncomeOnPay,
                        )
                      },
                      onSendWhatsApp = { reseller ->
                        val msg =
                          WhatsAppHelper.buildResellerSummaryMessage(
                            reseller = reseller,
                            settings = state.data.settings,
                          )
                        if (
                          !WhatsAppHelper.openWhatsApp(
                            context = context,
                            rawPhone = reseller.phone,
                            message = msg,
                            defaultCountryCode = state.data.settings.defaultCountryCode,
                          )
                        ) {
                          coroutineScope.launch {
                            snackbarHostState.showSnackbar("No se pudo abrir WhatsApp en este dispositivo")
                          }
                        }
                      },
                      onEditReseller = { reseller ->
                        editingReseller = reseller
                        showResellerSheet = true
                      },
                      onDeleteReseller = { reseller -> deleteTarget = DeleteTarget.ResellerItem(reseller) },
                    )
                  }
                  composable(ROUTE_SETTINGS) {
                    val user = Firebase.auth.currentUser
                    SettingsScreen(
                      data = state.data,
                      userEmail = user?.email,
                      userName = user?.displayName,
                      hasNotificationPermission = hasNotificationPermission,
                      cloudSyncState = cloudSyncState,
                      versionInfo = installedVersionInfo,
                      githubRepo = githubRepo,
                      githubUpdateState = githubUpdateState,
                      isSavingGitHubRepo = isSavingGitHubRepo,
                      onSyncCloudNow = viewModel::syncWithGoogleCloudNow,
                      onExportCloudBackup = {
                        exportBackupLauncher.launch(CloudBackupHelper.suggestedBackupFileName())
                      },
                      onImportCloudBackup = {
                        importBackupLauncher.launch(
                          arrayOf("application/json", "text/plain", "*/*")
                        )
                      },
                      onSaveGitHubRepo = { rawRepo ->
                        coroutineScope.launch {
                          isSavingGitHubRepo = true
                          val result = accessControlRepo.saveGitHubUpdateRepo(rawRepo)
                          isSavingGitHubRepo = false
                          result.onSuccess {
                            val normalized = GitHubReleaseUpdater.normalizeRepoSlug(rawRepo).orEmpty()
                            if (normalized.isBlank()) {
                              githubUpdateState = GitHubUpdateUiState.Idle
                              viewModel.notifyMessage("Repositorio de GitHub desvinculado")
                            } else {
                              viewModel.notifyMessage("Repositorio GitHub guardado: $normalized")
                              githubUpdateState = GitHubUpdateUiState.Checking
                              githubUpdateState =
                                GitHubReleaseUpdater.checkLatestRelease(
                                  repoSlug = normalized,
                                  installedVersionName = installedVersionInfo.versionName,
                                )
                            }
                          }.onFailure { err ->
                            viewModel.notifyMessage(
                              err.localizedMessage ?: "No se pudo guardar el repositorio de GitHub"
                            )
                          }
                        }
                      },
                      onCheckGitHubUpdates = {
                        coroutineScope.launch {
                          if (GitHubReleaseUpdater.normalizeRepoSlug(githubRepo) == null) {
                            githubUpdateState = GitHubUpdateUiState.RepoNotConfigured
                          } else {
                            githubUpdateState = GitHubUpdateUiState.Checking
                            githubUpdateState =
                              GitHubReleaseUpdater.checkLatestRelease(
                                repoSlug = githubRepo,
                                installedVersionName = installedVersionInfo.versionName,
                              )
                          }
                        }
                      },
                      onOpenGitHubDownload = { trustedUrl ->
                        if (!GitHubReleaseUpdater.openTrustedUrl(context, trustedUrl)) {
                          viewModel.notifyMessage("No se pudo abrir el enlace oficial de GitHub")
                        }
                      },
                      onOpenGitHubReleases = {
                        if (!GitHubReleaseUpdater.openReleasesPage(context, githubRepo)) {
                          viewModel.notifyMessage("No se pudo abrir GitHub Releases en el navegador")
                        }
                      },
                      onSaveSettings = viewModel::saveSettings,
                      onTestWhatsAppMessage = { previewMessage, countryCode ->
                        if (
                          !WhatsAppHelper.openWhatsApp(
                            context = context,
                            rawPhone = "",
                            message = previewMessage,
                            defaultCountryCode = countryCode,
                          )
                        ) {
                          coroutineScope.launch {
                            snackbarHostState.showSnackbar("No se pudo abrir WhatsApp en este dispositivo")
                          }
                        }
                      },
                      onRequestNotificationPermission = requestNotificationPermission,
                      onSendTestNotification = { draftSettings ->
                        VpnNotificationManager.sendTestNotification(context, draftSettings)
                      },
                      onSignOut = {
                        signOut(
                          credentialManager = credentialManager,
                          scope = coroutineScope,
                          context = context,
                        )
                      },
                      isAdmin = isAdmin,
                      onOpenUserAccessManager = { showUserAccessAdminDialog = true },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showTransactionSheet) {
    TransactionEditorSheet(
      existing = editingTransaction,
      defaultProfile = activeProfile,
      onSave = { profile, type, title, category, amount, dateMillis, notes ->
        viewModel.saveTransaction(
          existing = editingTransaction,
          profile = profile,
          type = type,
          title = title,
          category = category,
          amount = amount,
          dateMillis = dateMillis,
          notes = notes,
        )
        showTransactionSheet = false
      },
      onDismiss = { showTransactionSheet = false },
    )
  }

  if (showVpnSheet) {
    VpnEditorSheet(
      existing = editingVpn,
      defaultProfile = activeProfile,
      onSave = { profile, name, phone, username, plan, amount, expMillis, isPaid, notify, promiseMillis, promiseNote, notes ->
        if ((notify || promiseMillis > 0L) && !hasNotificationPermission) {
          requestNotificationPermission()
        }
        viewModel.saveVpnAccount(
          existing = editingVpn,
          profile = profile,
          clientName = name,
          clientPhone = phone,
          vpnUsername = username,
          planName = plan,
          amountToCharge = amount,
          expirationMillis = expMillis,
          isPaid = isPaid,
          notifyOneDayBefore = notify,
          paymentPromiseMillis = promiseMillis,
          paymentPromiseNote = promiseNote,
          notes = notes,
        )
        showVpnSheet = false
      },
      onDismiss = { showVpnSheet = false },
    )
  }

  promiseTargetVpn?.let { targetVpn ->
    PaymentPromiseSheet(
      vpn = targetVpn,
      reminderHour = currentSettings.reminderHour.toInt().coerceIn(0, 23),
      onSavePromise = { promiseMillis, promiseNote ->
        if (!hasNotificationPermission) {
          requestNotificationPermission()
        }
        viewModel.savePaymentPromise(targetVpn, promiseMillis, promiseNote)
        promiseTargetVpn = null
      },
      onClearPromise = {
        VpnNotificationManager.cancelPaymentPromiseReminder(context, targetVpn.id)
        viewModel.savePaymentPromise(targetVpn, 0L, "")
        promiseTargetVpn = null
      },
      onDismiss = { promiseTargetVpn = null },
    )
  }

  if (showResellerSheet) {
    ResellerEditorSheet(
      existing = editingReseller,
      defaultProfile = activeProfile,
      onSave = { profile, name, phone, creditsCount, pricePerCredit, expirationMillis, notes ->
        viewModel.saveReseller(
          existing = editingReseller,
          profile = profile,
          name = name,
          phone = phone,
          creditsCount = creditsCount,
          pricePerCredit = pricePerCredit,
          expirationMillis = expirationMillis,
          notes = notes,
        )
        showResellerSheet = false
      },
      onDismiss = { showResellerSheet = false },
    )
  }

  if (showAccountDialog) {
    val user = Firebase.auth.currentUser
    AccountAndNotificationDialog(
      userEmail = user?.email,
      userName = user?.displayName,
      hasNotificationPermission = hasNotificationPermission,
      reminderHour = currentSettings.reminderHour.toInt().coerceIn(0, 23),
      isAdmin = isAdmin,
      onOpenUserAccessManager = {
        showAccountDialog = false
        showUserAccessAdminDialog = true
      },
      onRequestNotificationPermission = requestNotificationPermission,
      onSendTestNotification = {
        VpnNotificationManager.sendTestNotification(context, currentSettings)
        showAccountDialog = false
      },
      onOpenSettings = {
        showAccountDialog = false
        navigateToTab(ROUTE_SETTINGS)
      },
      onSignOut = {
        showAccountDialog = false
        signOut(
          credentialManager = credentialManager,
          scope = coroutineScope,
          context = context,
        )
      },
      onDismiss = { showAccountDialog = false },
    )
  }

  if (isAdmin && showUserAccessAdminDialog) {
    UserAccessAdminDialog(
      defaultCountryCode = currentSettings.defaultCountryCode,
      businessName = currentSettings.businessName,
      onDismiss = { showUserAccessAdminDialog = false },
    )
  }

  deleteTarget?.let { target ->
    val targetLabel =
      when (target) {
        is DeleteTarget.Transaction -> "el movimiento \"${target.item.title}\""
        is DeleteTarget.Vpn -> "al cliente \"${target.item.clientName}\""
        is DeleteTarget.ResellerItem -> "al revendedor \"${target.item.name}\""
      }
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text("Confirmar eliminación") },
      text = { Text("¿Seguro que querés eliminar $targetLabel?") },
      confirmButton = {
        TextButton(
          onClick = {
            when (target) {
              is DeleteTarget.Transaction -> viewModel.deleteTransaction(target.item.id)
              is DeleteTarget.Vpn -> {
                VpnNotificationManager.cancelReminder(context, target.item.id)
                VpnNotificationManager.cancelPaymentPromiseReminder(context, target.item.id)
                viewModel.deleteVpnAccount(target.item.id)
              }
              is DeleteTarget.ResellerItem -> viewModel.deleteReseller(target.item.id)
            }
            deleteTarget = null
          }
        ) {
          Text("Eliminar", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteTarget = null }) {
          Text("Cancelar")
        }
      },
    )
  }

  val availableGitHubUpdate = githubUpdateState as? GitHubUpdateUiState.UpdateAvailable
  if (
    availableGitHubUpdate != null &&
    dismissedGitHubTag != availableGitHubUpdate.release.tagName
  ) {
    val release = availableGitHubUpdate.release
    val sizeLabel = GitHubReleaseUpdater.formatSizeMb(release.apkSizeBytes)
    AlertDialog(
      onDismissRequest = { dismissedGitHubTag = release.tagName },
      title = { Text("Nueva actualización disponible") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "${release.releaseName} (${release.tagName})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text =
              buildString {
                append("Versión actual: v${availableGitHubUpdate.installedVersion}")
                if (sizeLabel.isNotBlank()) append(" · APK $sizeLabel")
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (release.notes.isNotBlank()) {
            Text(
              text = release.notes,
              style = MaterialTheme.typography.bodySmall,
            )
          }
          Text(
            text = "Se instala directamente encima sin desinstalar ni perder tus datos.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            dismissedGitHubTag = release.tagName
            if (!GitHubReleaseUpdater.openTrustedUrl(context, release.bestOpenUrl)) {
              viewModel.notifyMessage("No se pudo abrir la descarga de GitHub")
            }
          },
          modifier = Modifier.testTag("dialog_download_github_update_button"),
        ) {
          Text("Actualizar ahora")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { dismissedGitHubTag = release.tagName },
          modifier = Modifier.testTag("dialog_dismiss_github_update_button"),
        ) {
          Text("Más tarde")
        }
      },
    )
  }
}
