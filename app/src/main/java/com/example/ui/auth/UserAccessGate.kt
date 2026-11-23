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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.AuthorizedUser
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface AccessGateLoadState {
  data object Loading : AccessGateLoadState

  data class Loaded(val record: AuthorizedUser?) : AccessGateLoadState

  data class Error(val message: String) : AccessGateLoadState
}

@Composable
fun UserAccessGate(user: FirebaseUser) {
  val normalizedEmail = AccessControlConfig.normalizeEmail(user.email)
  val isAdmin = AccessControlConfig.isAdminEmail(normalizedEmail)

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

  val loadState by
    produceState<AccessGateLoadState>(
      initialValue = AccessGateLoadState.Loading,
      key1 = user.uid,
      key2 = normalizedEmail,
      key3 = retryKey,
    ) {
      if (!AccessControlConfig.isValidEmailFormat(normalizedEmail)) {
        value = AccessGateLoadState.Loaded(null)
        return@produceState
      }
      accessRepo
        .observeAuthorizedUserByEmail(normalizedEmail)
        .catch { err ->
          value =
            AccessGateLoadState.Error(
              err.localizedMessage ?: "No se pudo verificar el estado de tu cuenta."
            )
        }
        .collect { record -> value = AccessGateLoadState.Loaded(record) }
    }

  val adminWhatsAppPhone by
    produceState(initialValue = "", key1 = accessRepo) {
      accessRepo.observeAdminWhatsAppPhone().collect { phone -> value = phone }
    }

  var whatsappError by remember { mutableStateOf<String?>(null) }

  val openAdminWhatsApp: () -> Unit = {
    val opened =
      WhatsAppHelper.openWhatsApp(
        context = context,
        rawPhone = adminWhatsAppPhone,
        message = AccessControlConfig.DISABLED_WHATSAPP_MESSAGE,
      )
    whatsappError =
      if (opened) null else "No se pudo abrir WhatsApp en este dispositivo."
  }

  val handleSignOut: () -> Unit = {
    signOut(
      context = context,
      credentialManager = credentialManager,
      scope = coroutineScope,
    )
  }

  when (val state = loadState) {
    AccessGateLoadState.Loading -> {
      AccessStatusShell(
        icon = Icons.Filled.Key,
        title = "Verificando acceso...",
        subtitle = "Comprobando permisos de $normalizedEmail",
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
      if (record == null) {
        LaunchedEffect(normalizedEmail) { AccessVerificationStore.clear(context) }
        AccessStatusShell(
          icon = Icons.Filled.Block,
          title = "Cuenta no autorizada",
          subtitle =
            "El correo $normalizedEmail todavía no fue habilitado por el administrador. Pedile que cree tu acceso y te pase la contraseña.",
          showProgress = false,
          primaryActionLabel = "Volver a comprobar",
          onPrimaryAction = { retryKey++ },
          onWhatsAppAdmin = openAdminWhatsApp,
          whatsappError = whatsappError,
          onSignOut = handleSignOut,
          signOutTestTag = "access_not_authorized_sign_out_button",
        )
      } else if (!record.enabled) {
        LaunchedEffect(record.email, record.enabled) { AccessVerificationStore.clear(context) }
        AccessStatusShell(
          icon = Icons.Filled.GppBad,
          title = "Acceso deshabilitado",
          subtitle =
            "El administrador deshabilitó temporalmente tu usuario ($normalizedEmail). Cuando vuelva a habilitarlo, la aplicación se desbloqueará automáticamente.",
          showProgress = false,
          primaryActionLabel = null,
          onPrimaryAction = null,
          onWhatsAppAdmin = openAdminWhatsApp,
          whatsappError = whatsappError,
          onSignOut = handleSignOut,
          signOutTestTag = "access_disabled_sign_out_button",
        )
      } else {
        var verifiedVersion by
          remember(record.email) {
            mutableLongStateOf(
              if (AccessVerificationStore.isVerified(context, record.email, record.passwordVersion)) {
                record.passwordVersion
              } else {
                -1L
              }
            )
          }
        var autoVerifyError by remember(record.email, record.passwordVersion) { mutableStateOf<String?>(null) }
        var isAutoVerifying by remember(record.email, record.passwordVersion) { mutableStateOf(false) }

        LaunchedEffect(record.email, record.passwordVersion) {
          if (verifiedVersion != record.passwordVersion) {
            val (matched, candidatePassword) = PendingLoginCredentials.consumeForEmail(record.email)
            if (matched && !candidatePassword.isNullOrEmpty()) {
              isAutoVerifying = true
              val ok =
                withContext(Dispatchers.Default) {
                  PasswordHasher.verifyPassword(
                    password = candidatePassword,
                    saltHex = record.passwordSalt,
                    expectedHashHex = record.passwordHash,
                  )
                }
              isAutoVerifying = false
              if (ok) {
                AccessVerificationStore.markVerified(context, record.email, record.passwordVersion)
                verifiedVersion = record.passwordVersion
                coroutineScope.launch { accessRepo.recordSelfAccess(record.email) }
              } else {
                autoVerifyError =
                  "La contraseña que ingresaste al inicio es incorrecta. Intentá nuevamente."
              }
            }
          }
        }

        when {
          verifiedVersion == record.passwordVersion -> {
            MainAppScaffold(currentUserId = user.uid, isAdmin = false)
          }
          isAutoVerifying -> {
            AccessStatusShell(
              icon = Icons.Filled.Key,
              title = "Verificando contraseña...",
              subtitle = "Validando tu acceso para ${record.email}",
              showProgress = true,
              primaryActionLabel = null,
              onPrimaryAction = null,
              onSignOut = handleSignOut,
            )
          }
          else -> {
            PasswordUnlockScreen(
              record = record,
              initialError = autoVerifyError,
              onUnlocked = {
                AccessVerificationStore.markVerified(context, record.email, record.passwordVersion)
                verifiedVersion = record.passwordVersion
                coroutineScope.launch { accessRepo.recordSelfAccess(record.email) }
              },
              onSignOut = handleSignOut,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PasswordUnlockScreen(
  record: AuthorizedUser,
  initialError: String? = null,
  onUnlocked: () -> Unit,
  onSignOut: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var password by remember(record.email, record.passwordVersion) { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var isVerifying by remember { mutableStateOf(false) }
  var errorText by remember(record.email, record.passwordVersion, initialError) { mutableStateOf(initialError) }

  val verifyNow: () -> Unit = {
    if (!isVerifying) {
      if (password.isBlank()) {
        errorText = "Ingresá la contraseña que te entregó el administrador."
      } else {
        errorText = null
        isVerifying = true
        val candidate = password
        coroutineScope.launch {
          val ok =
            withContext(Dispatchers.Default) {
              PasswordHasher.verifyPassword(
                password = candidate,
                saltHex = record.passwordSalt,
                expectedHashHex = record.passwordHash,
              )
            }
          isVerifying = false
          if (ok) {
            onUnlocked()
          } else {
            errorText = "Contraseña incorrecta. Verificá los datos o pedí una nueva al administrador."
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
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Image(
              painter = painterResource(id = R.drawable.malvinas_logo),
              contentDescription = "Logo Malvinas Pro Service",
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(64.dp).clip(CircleShape),
            )
            Text(
              text = "¡Hola, ${record.displayName}!",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
            )
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            ) {
              Text(
                text = record.email,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              )
            }
            Text(
              text = "Ingresá la contraseña de acceso que te compartió el administrador para desbloquear la aplicación.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )

            OutlinedTextField(
              value = password,
              onValueChange = {
                if (it.length <= AccessControlConfig.MAX_PASSWORD_LENGTH) {
                  password = it
                  errorText = null
                }
              },
              label = { Text("Contraseña de acceso") },
              singleLine = true,
              visualTransformation =
                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              keyboardOptions =
                KeyboardOptions(
                  keyboardType = KeyboardType.Password,
                  imeAction = ImeAction.Done,
                ),
              keyboardActions = KeyboardActions(onDone = { verifyNow() }),
              trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                  Icon(
                    imageVector =
                      if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription =
                      if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                  )
                }
              },
              isError = errorText != null,
              supportingText =
                errorText?.let { msg ->
                  { Text(text = msg, color = MaterialTheme.colorScheme.error) }
                },
              modifier = Modifier.fillMaxWidth().testTag("access_password_input"),
            )

            Button(
              onClick = verifyNow,
              enabled = !isVerifying,
              modifier =
                Modifier.fillMaxWidth()
                  .height(50.dp)
                  .testTag("access_password_submit_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              if (isVerifying) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Verificando...")
              } else {
                Icon(
                  imageVector = Icons.Filled.LockOpen,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Desbloquear acceso")
              }
            }

            OutlinedButton(
              onClick = onSignOut,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("access_password_sign_out_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Cambiar de cuenta")
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
