package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.AuthorizedUser
import com.example.data.model.PinChallenge
import com.example.data.repository.AccessControlRepository
import com.example.ui.MainAppScaffold
import com.example.ui.components.AlbicelesteRibbonStripe
import com.example.util.AccessControlConfig
import com.example.util.AccessVerificationStore
import com.example.util.PasswordHasher
import com.example.util.PendingLoginCredentials
import com.example.util.WhatsAppHelper
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface AccessGateLoadState {
  data object Loading : AccessGateLoadState

  data class Loaded(val boundAccountId: String?, val record: AuthorizedUser?) : AccessGateLoadState

  data class Error(val message: String) : AccessGateLoadState
}

private data class ActivePinVerification(
  val challengeId: String,
  val initialPin: String,
  val account: AuthorizedUser,
)

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun UserAccessGate(user: FirebaseUser) {
  val normalizedGoogleEmail = AccessControlConfig.normalizeEmail(user.email)
  val isAdmin = AccessControlConfig.isAdminEmail(normalizedGoogleEmail)

  if (isAdmin) {
    LaunchedEffect(user.uid) { PendingLoginCredentials.clear() }
    MainAppScaffold(currentUserId = user.uid, isAdmin = true)
    return
  }

  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val accessRepo = remember(context, user.uid) { AccessControlRepository(context) }
  var retryKey by remember { mutableIntStateOf(0) }

  // Tracks active random Admin PIN challenge after username + password match
  var activePinSession by remember(user.uid) { mutableStateOf<ActivePinVerification?>(null) }
  var loginError by remember(user.uid) { mutableStateOf<String?>(null) }
  var isSubmittingCredentials by remember(user.uid) { mutableStateOf(false) }
  var unlockedAccountId by remember(user.uid) { mutableStateOf<String?>(null) }
  var unlockedVersion by remember(user.uid) { mutableLongStateOf(-1L) }

  // Observe /userBindings/{uid} -> /authorizedUsers/{accountId}, with fallback to legacy Google email record
  val loadState by
    produceState<AccessGateLoadState>(
      initialValue = AccessGateLoadState.Loading,
      key1 = user.uid,
      key2 = normalizedGoogleEmail,
      key3 = retryKey,
    ) {
      accessRepo
        .observeBoundAccountId(user.uid)
        .flatMapLatest { boundId ->
          val effectiveId =
            boundId?.takeIf { it.isNotBlank() }
              ?: normalizedGoogleEmail.takeIf { AccessControlConfig.isValidEmailFormat(it) }
          if (effectiveId.isNullOrBlank()) {
            flowOf(AccessGateLoadState.Loaded(boundAccountId = null, record = null))
          } else {
            accessRepo.observeAuthorizedUserByAccountId(effectiveId).catch { err ->
              // If legacy/stale doc is now bound to someone else, fall back cleanly to login screen
              emit(null)
            }.let { recordFlow ->
              kotlinx.coroutines.flow.flow {
                recordFlow.collect { rec ->
                  emit(AccessGateLoadState.Loaded(boundAccountId = boundId, record = rec))
                }
              }
            }
          }
        }
        .catch { err ->
          value =
            AccessGateLoadState.Error(
              err.localizedMessage ?: "No se pudo verificar el estado de tu cuenta."
            )
        }
        .collect { state -> value = state }
    }

  val adminWhatsAppPhone by
    produceState(initialValue = "", key1 = accessRepo) {
      accessRepo.observeAdminWhatsAppPhone().collect { phone -> value = phone }
    }

  var whatsappError by remember { mutableStateOf<String?>(null) }

  val openAdminWhatsApp: (String) -> Unit = { customMessage ->
    val opened =
      WhatsAppHelper.openWhatsApp(
        context = context,
        rawPhone = adminWhatsAppPhone,
        message = customMessage,
      )
    whatsappError =
      if (opened) null else "No se pudo abrir WhatsApp en este dispositivo."
  }

  val handleSignOut: () -> Unit = {
    val activeId = activePinSession?.challengeId
    if (!activeId.isNullOrBlank()) {
      coroutineScope.launch { accessRepo.cancelPinChallenge(activeId) }
    }
    activePinSession = null
    unlockedAccountId = null
    unlockedVersion = -1L
    signOut(
      context = context,
      credentialManager = credentialManager,
      scope = coroutineScope,
    )
  }

  // Shared credential verification routine -> validates username + password -> triggers random Admin PIN
  val verifyCredentialsAndStartPin: (String, String) -> Unit = { rawAccount, rawPassword ->
    if (!isSubmittingCredentials) {
      val cleanAccount = AccessControlConfig.normalizeAccountId(rawAccount)
      when {
        !AccessControlConfig.isValidAccountId(cleanAccount) -> {
          loginError = "Ingresá un usuario válido (mín. 3 caracteres, sin espacios)."
        }
        rawPassword.isBlank() -> {
          loginError = "Ingresá tu contraseña de acceso."
        }
        else -> {
          loginError = null
          isSubmittingCredentials = true
          coroutineScope.launch {
            val lookupResult = accessRepo.getAuthorizedUserByAccountId(cleanAccount)
            val record =
              lookupResult.getOrElse { err ->
                isSubmittingCredentials = false
                loginError =
                  err.localizedMessage ?: "No se pudo verificar el usuario ingresado."
                return@launch
              }

            if (record == null) {
              isSubmittingCredentials = false
              loginError = "Usuario o contraseña incorrectos. Verificá los datos con el administrador."
              return@launch
            }
            if (!record.enabled) {
              isSubmittingCredentials = false
              loginError =
                "El usuario @${record.email} está deshabilitado por el administrador. Tocá 'Hablar por WhatsApp' para solicitar reactivación."
              return@launch
            }
            if (record.boundUid.isNotBlank() && record.boundUid != user.uid) {
              isSubmittingCredentials = false
              loginError =
                "El usuario @${record.email} ya está vinculado a otro dispositivo. Pedile al administrador que lo desvincule."
              return@launch
            }

            val passwordOk =
              withContext(Dispatchers.Default) {
                PasswordHasher.verifyPassword(
                  password = rawPassword,
                  saltHex = record.passwordSalt,
                  expectedHashHex = record.passwordHash,
                )
              }
            if (!passwordOk) {
              isSubmittingCredentials = false
              loginError = "Usuario o contraseña incorrectos."
              return@launch
            }

            // Password matches! Generate random 6-digit PIN shown above input & sent with device info to Admin
            val challengeRes =
              accessRepo.createPinChallengeForUser(
                rawAccountId = record.email,
                displayName = record.displayName,
                requesterEmail = user.email,
              )
            isSubmittingCredentials = false
            challengeRes.fold(
              onSuccess = { (challengeId, generatedPin) ->
                activePinSession =
                  ActivePinVerification(
                    challengeId = challengeId,
                    initialPin = generatedPin,
                    account = record,
                  )
              },
              onFailure = { err ->
                loginError =
                  err.localizedMessage
                    ?: "No se pudo generar el PIN de verificación. Intentá de nuevo."
              },
            )
          }
        }
      }
    }
  }

  // If user already typed usuario + contraseña on AuthScreen, auto-verify once loaded
  LaunchedEffect(loadState) {
    val loaded = loadState as? AccessGateLoadState.Loaded ?: return@LaunchedEffect
    val existingRecord = loaded.record
    val alreadyVerified =
      existingRecord != null &&
        existingRecord.enabled &&
        existingRecord.boundUid == user.uid &&
        AccessVerificationStore.isVerified(context, existingRecord.email, existingRecord.passwordVersion)
    if (!alreadyVerified && activePinSession == null) {
      val pending = PendingLoginCredentials.consume()
      if (pending != null) {
        verifyCredentialsAndStartPin(pending.first, pending.second)
      }
    } else if (alreadyVerified) {
      PendingLoginCredentials.clear()
    }
  }

  // If currently in the 6-digit Admin PIN step, display PIN verification screen
  val currentPinSession = activePinSession
  if (currentPinSession != null) {
    PinVerificationScreen(
      session = currentPinSession,
      accessRepo = accessRepo,
      onPinVerified = {
        val account = currentPinSession.account
        AccessVerificationStore.markVerified(context, account.email, account.passwordVersion)
        unlockedAccountId = account.email
        unlockedVersion = account.passwordVersion
        activePinSession = null
      },
      onRequestNewPin = {
        val oldChallengeId = currentPinSession.challengeId
        val account = currentPinSession.account
        coroutineScope.launch {
          accessRepo.cancelPinChallenge(oldChallengeId)
          val res =
            accessRepo.createPinChallengeForUser(
              rawAccountId = account.email,
              displayName = account.displayName,
              requesterEmail = user.email,
            )
          res.onSuccess { (newId, newPin) ->
            activePinSession =
              ActivePinVerification(
                challengeId = newId,
                initialPin = newPin,
                account = account,
              )
          }
        }
      },
      onRequestPinViaWhatsApp = {
        val account = currentPinSession.account
        openAdminWhatsApp(
          "Hola! Acabo de ingresar con el usuario @${account.email} (${account.displayName}) en Malvinas Pro Service. ¿Me pasás el PIN de 6 dígitos que te aparece?"
        )
      },
      whatsappError = whatsappError,
      onCancel = {
        val oldId = currentPinSession.challengeId
        activePinSession = null
        coroutineScope.launch { accessRepo.cancelPinChallenge(oldId) }
      },
    )
    return
  }

  when (val state = loadState) {
    AccessGateLoadState.Loading -> {
      AccessStatusShell(
        icon = Icons.Filled.Key,
        title = "Verificando acceso...",
        subtitle = "Comprobando tu sesión en Malvinas Pro Service",
        showProgress = true,
        primaryActionLabel = null,
        onPrimaryAction = null,
        onSignOut = handleSignOut,
      )
    }

    is AccessGateLoadState.Error -> {
      AccessStatusShell(
        icon = Icons.Filled.GppBad,
        title = "No se pudo validar tu acceso",
        subtitle = state.message,
        showProgress = false,
        primaryActionLabel = "Reintentar",
        onPrimaryAction = { retryKey++ },
        onSignOut = handleSignOut,
      )
    }

    is AccessGateLoadState.Loaded -> {
      val record = state.record

      // Case 1: Bound to an account that Admin DISABLED -> Instant Kill Switch screen
      if (record != null && !record.enabled && (record.boundUid == user.uid || record.email == normalizedGoogleEmail)) {
        LaunchedEffect(record.email, record.enabled) {
          AccessVerificationStore.clear(context)
          unlockedAccountId = null
          unlockedVersion = -1L
        }
        AccessStatusShell(
          icon = Icons.Filled.GppBad,
          title = "Acceso deshabilitado",
          subtitle =
            "El administrador deshabilitó temporalmente tu usuario (@${record.email}). Cuando vuelva a habilitarlo, la aplicación se desbloqueará automáticamente.",
          showProgress = false,
          primaryActionLabel = "Ingresar con otro usuario",
          onPrimaryAction = {
            coroutineScope.launch {
              accessRepo.clearCurrentUserBinding()
              retryKey++
            }
          },
          onWhatsAppAdmin = { openAdminWhatsApp(AccessControlConfig.DISABLED_WHATSAPP_MESSAGE) },
          whatsappError = whatsappError,
          onSignOut = handleSignOut,
          signOutTestTag = "access_disabled_sign_out_button",
        )
        return
      }

      // Case 2: Bound to an active account AND verified for current passwordVersion -> Enter app!
      val isLocallyVerified =
        record != null &&
          record.enabled &&
          record.boundUid == user.uid &&
          (
            (unlockedAccountId == record.email && unlockedVersion == record.passwordVersion) ||
              AccessVerificationStore.isVerified(context, record.email, record.passwordVersion)
          )

      if (isLocallyVerified) {
        LaunchedEffect(record.email, user.uid) {
          accessRepo.recordSelfAccess(
            rawAccountId = record.email,
            displayName = record.displayName,
          )
        }
        MainAppScaffold(currentUserId = user.uid, isAdmin = false)
        return
      }

      // Case 3: Not yet bound, password changed, or Admin unbound device -> Usuario + Contraseña -> Admin PIN
      val prefillAccount =
        record
          ?.takeIf { it.enabled && (it.boundUid.isBlank() || it.boundUid == user.uid) }
          ?.email
          .orEmpty()

      UsernamePasswordGateScreen(
        prefillUsername = prefillAccount,
        isSubmitting = isSubmittingCredentials,
        errorMessage = loginError,
        whatsappError = whatsappError,
        onClearError = { loginError = null },
        onSubmit = { username, password -> verifyCredentialsAndStartPin(username, password) },
        onWhatsAppAdmin = { openAdminWhatsApp(AccessControlConfig.DISABLED_WHATSAPP_MESSAGE) },
        onSignOut = handleSignOut,
      )
    }
  }
}

@Composable
private fun UsernamePasswordGateScreen(
  prefillUsername: String,
  isSubmitting: Boolean,
  errorMessage: String?,
  whatsappError: String?,
  onClearError: () -> Unit,
  onSubmit: (username: String, password: String) -> Unit,
  onWhatsAppAdmin: () -> Unit,
  onSignOut: () -> Unit,
) {
  var username by remember(prefillUsername) { mutableStateOf(prefillUsername) }
  var password by remember { mutableStateOf("") }

  MalvinasLoginShell(
    username = username,
    onUsernameChange = {
      if (it.length <= 128) {
        username = it
        onClearError()
      }
    },
    password = password,
    onPasswordChange = {
      if (it.length <= AccessControlConfig.MAX_PASSWORD_LENGTH) {
        password = it
        onClearError()
      }
    },
    isLoading = isSubmitting,
    loadingLabel = "Validando...",
    errorMessage = errorMessage,
    secondaryInfoError = whatsappError,
    onPrimarySubmit = { onSubmit(username, password) },
    onRequestAccessClick = onWhatsAppAdmin,
    usernameTestTag = "gate_username_input",
    passwordTestTag = "access_password_input",
    primaryButtonTestTag = "access_password_submit_button",
    requestAccessTestTag = "access_disabled_whatsapp_button",
    footerActionLabel = "Cerrar sesión",
    footerActionTestTag = "access_password_sign_out_button",
    onFooterAction = onSignOut,
  )
}

@Composable
private fun PinVerificationScreen(
  session: ActivePinVerification,
  accessRepo: AccessControlRepository,
  onPinVerified: () -> Unit,
  onRequestNewPin: () -> Unit,
  onRequestPinViaWhatsApp: () -> Unit,
  whatsappError: String?,
  onCancel: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var pinInput by remember(session.challengeId) { mutableStateOf("") }
  var isVerifying by remember(session.challengeId) { mutableStateOf(false) }
  var errorText by remember(session.challengeId) { mutableStateOf<String?>(null) }

  val challenge by
    produceState<PinChallenge?>(initialValue = null, key1 = session.challengeId) {
      accessRepo
        .observePinChallenge(session.challengeId)
        .catch { value = null }
        .collect { value = it }
    }

  val isRejected = challenge?.status == PinChallenge.STATUS_REJECTED
  val isExpired =
    challenge != null && challenge!!.expiresAt > 0L && System.currentTimeMillis() > challenge!!.expiresAt

  val verifyPinNow: () -> Unit = {
    val activeChallenge = challenge
    val cleanPin = pinInput.filter { it.isDigit() }
    if (!isVerifying) {
      when {
        activeChallenge == null -> {
          errorText = "Esperando confirmación del servidor. Intentá en un segundo."
        }
        activeChallenge.status == PinChallenge.STATUS_REJECTED -> {
          errorText = "El administrador rechazó esta solicitud de ingreso."
        }
        activeChallenge.expiresAt > 0L && System.currentTimeMillis() > activeChallenge.expiresAt -> {
          errorText = "El PIN venció. Tocá 'Solicitar nuevo PIN' para generar otro."
        }
        cleanPin.length != AccessControlConfig.PIN_LENGTH -> {
          errorText = "Ingresá los 6 dígitos del PIN que le apareció al administrador."
        }
        else -> {
          errorText = null
          isVerifying = true
          coroutineScope.launch {
            val matches =
              withContext(Dispatchers.Default) {
                PasswordHasher.verifyPassword(
                  password = cleanPin,
                  saltHex = activeChallenge.pinSalt,
                  expectedHashHex = activeChallenge.pinHash,
                )
              }
            if (!matches) {
              isVerifying = false
              errorText = "PIN incorrecto. Pedile al administrador que verifique el código de 6 dígitos."
              return@launch
            }

            val completeRes =
              accessRepo.completePinVerification(
                challengeId = session.challengeId,
                rawAccountId = session.account.email,
                displayName = session.account.displayName,
              )
            isVerifying = false
            completeRes.fold(
              onSuccess = { onPinVerified() },
              onFailure = { err ->
                errorText =
                  err.localizedMessage ?: "No se pudo completar la verificación del PIN."
              },
            )
          }
        }
      }
    }
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                  MaterialTheme.colorScheme.background,
                )
            )
          )
          .safeDrawingPadding()
          .padding(horizontal = 20.dp, vertical = 16.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        modifier =
          Modifier.fillMaxWidth()
            .widthIn(max = 460.dp)
            .verticalScroll(rememberScrollState()),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          AlbicelesteRibbonStripe(height = 5.dp, showSunBadge = true)
          Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp),
          ) {
            Surface(
              shape = CircleShape,
              color =
                if (isRejected) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(64.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = if (isRejected) Icons.Filled.GppBad else Icons.Filled.Pin,
                  contentDescription = null,
                  tint =
                    if (isRejected) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(32.dp),
                )
              }
            }

            Text(
              text =
                if (isRejected) "Ingreso rechazado" else "Verificación con PIN",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
            )

            Surface(
              shape = RoundedCornerShape(20.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            ) {
              Text(
                text = "${session.account.displayName} · @${session.account.email}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              )
            }

            Text(
              text =
                if (isRejected) {
                  "El administrador rechazó esta solicitud de acceso. Si fue un error, podés volver e intentar nuevamente."
                } else {
                  "Ingresá el PIN aleatorio de 6 dígitos que aparece arriba para confirmar que sos vos. Este código y tu dispositivo también se muestran al administrador."
                },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )

            if (!isRejected) {
              val displayedPin =
                challenge?.pinCode?.takeIf { it.length == AccessControlConfig.PIN_LENGTH }
                  ?: session.initialPin
              val formattedDisplayedPin = AccessControlConfig.formatPinForDisplay(displayedPin)
              val deviceLabel = remember { AccessControlConfig.currentDeviceLabel() }

              Surface(
                modifier = Modifier.fillMaxWidth().testTag("random_pin_display_box"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = "PIN ALEATORIO DE VERIFICACIÓN",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                  )
                  Text(
                    text = formattedDisplayedPin,
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("random_pin_display_value"),
                  )
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Smartphone,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text = deviceLabel,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                    )
                  }
                }
              }

              OutlinedTextField(
                value = pinInput,
                onValueChange = { raw ->
                  val digits = raw.filter { it.isDigit() }.take(AccessControlConfig.PIN_LENGTH)
                  pinInput = digits
                  errorText = null
                },
                label = { Text("Escribí el PIN de 6 dígitos") },
                placeholder = { Text("000000") },
                singleLine = true,
                enabled = !isVerifying,
                textStyle =
                  MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                  ),
                keyboardOptions =
                  KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                  ),
                keyboardActions = KeyboardActions(onDone = { verifyPinNow() }),
                modifier = Modifier.fillMaxWidth().testTag("pin_verification_input"),
              )
            }

            val displayError =
              errorText
                ?: if (isExpired) "El PIN venció. Tocá 'Solicitar nuevo PIN' para generar otro." else null
            if (displayError != null) {
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
              ) {
                Text(
                  text = displayError,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }

            if (!isRejected) {
              Button(
                onClick = verifyPinNow,
                enabled = !isVerifying && pinInput.length == AccessControlConfig.PIN_LENGTH,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(52.dp)
                    .testTag("verify_pin_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                if (isVerifying) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text("Verificando PIN...")
                } else {
                  Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Verificar PIN", fontWeight = FontWeight.SemiBold)
                }
              }

              Button(
                onClick = onRequestPinViaWhatsApp,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(48.dp)
                    .testTag("request_pin_whatsapp_button"),
                shape = RoundedCornerShape(14.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                  ),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pedir PIN por WhatsApp", fontWeight = FontWeight.SemiBold)
              }

              if (whatsappError != null) {
                Text(
                  text = whatsappError,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                  textAlign = TextAlign.Center,
                )
              }

              OutlinedButton(
                onClick = {
                  pinInput = ""
                  errorText = null
                  onRequestNewPin()
                },
                enabled = !isVerifying,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(48.dp)
                    .testTag("request_new_pin_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generar nuevo PIN")
              }
            }

            TextButton(
              onClick = onCancel,
              modifier = Modifier.testTag("cancel_pin_verification_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Volver al inicio de sesión")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AccessStatusShell(
  icon: ImageVector,
  title: String,
  subtitle: String,
  showProgress: Boolean,
  primaryActionLabel: String?,
  onPrimaryAction: (() -> Unit)?,
  onSignOut: () -> Unit,
  onWhatsAppAdmin: (() -> Unit)? = null,
  whatsappError: String? = null,
  signOutTestTag: String = "access_gate_sign_out_button",
) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                  MaterialTheme.colorScheme.background,
                )
            )
          )
          .safeDrawingPadding()
          .padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          AlbicelesteRibbonStripe(height = 5.dp, showSunBadge = true)
          Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            if (showProgress) {
              CircularProgressIndicator(modifier = Modifier.size(44.dp))
            } else {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(64.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(32.dp),
                  )
                }
              }
            }

            Text(
              text = title,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
            )
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )

            if (onWhatsAppAdmin != null) {
              Button(
                onClick = onWhatsAppAdmin,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(50.dp)
                    .testTag("access_disabled_whatsapp_button"),
                shape = RoundedCornerShape(14.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                  ),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hablar por WhatsApp", fontWeight = FontWeight.SemiBold)
              }
            }

            if (whatsappError != null) {
              Text(
                text = whatsappError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
              )
            }

            if (primaryActionLabel != null && onPrimaryAction != null) {
              Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(primaryActionLabel)
              }
            }

            OutlinedButton(
              onClick = onSignOut,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag(signOutTestTag),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Cerrar sesión")
            }
          }
        }
      }
    }
  }
}
