package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.theme.MalvinasProTheme
import com.example.util.AccessControlConfig
import com.example.util.PendingLoginCredentials
import com.example.util.WhatsAppHelper

private const val LOGIN_BANNER_ASPECT_RATIO = 756f / 250f

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  var emailInput by remember { mutableStateOf("") }
  var passwordInput by remember { mutableStateOf("") }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showRequestAccessDialog by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = coroutineScope,
    )
  }

  val startGoogleDirectSignIn: () -> Unit = {
    if (!isLoading) {
      errorMessage = null
      PendingLoginCredentials.clear()
      isLoading = true
      onGoogleSignInClicked(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {
          isLoading = false
          onAuthSuccess()
        },
        onAuthError = { msg ->
          isLoading = false
          errorMessage = msg
        },
        scope = coroutineScope,
        onAuthCancelled = { isLoading = false },
      )
    }
  }

  val startCredentialsLogin: () -> Unit = {
    if (!isLoading) {
      val normalizedAccount = AccessControlConfig.normalizeAccountId(emailInput)
      when {
        !AccessControlConfig.isValidAccountId(normalizedAccount) -> {
          errorMessage = "Ingresá un usuario válido (mín. 3 caracteres, sin espacios)."
        }
        !AccessControlConfig.isAdminEmail(normalizedAccount) && passwordInput.isBlank() -> {
          errorMessage = "Ingresá la contraseña que te compartió el administrador."
        }
        else -> {
          errorMessage = null
          if (!AccessControlConfig.isAdminEmail(normalizedAccount)) {
            PendingLoginCredentials.set(normalizedAccount, passwordInput)
          } else {
            PendingLoginCredentials.clear()
          }
          isLoading = true
          onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
              isLoading = false
              onAuthSuccess()
            },
            onAuthError = { msg ->
              PendingLoginCredentials.clear()
              isLoading = false
              errorMessage = msg
            },
            scope = coroutineScope,
            onAuthCancelled = {
              PendingLoginCredentials.clear()
              isLoading = false
            },
          )
        }
      }
    }
  }

  MalvinasLoginShell(
    username = emailInput,
    onUsernameChange = {
      if (it.length <= 128) {
        emailInput = it
        errorMessage = null
      }
    },
    password = passwordInput,
    onPasswordChange = {
      if (it.length <= AccessControlConfig.MAX_PASSWORD_LENGTH) {
        passwordInput = it
        errorMessage = null
      }
    },
    isLoading = isLoading,
    loadingLabel = "Conectando...",
    errorMessage = errorMessage,
    onPrimarySubmit = startCredentialsLogin,
    onRequestAccessClick = { showRequestAccessDialog = true },
    usernameTestTag = "login_email_input",
    passwordTestTag = "login_password_input",
    primaryButtonTestTag = "login_with_credentials_button",
    requestAccessTestTag = "login_request_access_button",
    footerActionLabel = "Acceso administrador (Google)",
    footerActionTestTag = "google_sign_in_button",
    onFooterAction = startGoogleDirectSignIn,
  )

  if (showRequestAccessDialog) {
    MalvinasProTheme(darkTheme = true) {
      RequestAccessDialog(
        initialName = emailInput,
        onSendWhatsAppRequest = { applicantName ->
          val cleanName = applicantName.trim()
          val msg =
            if (cleanName.isNotBlank()) {
              "Hola, soy $cleanName. Quisiera solicitar acceso a Malvinas Pro Service."
            } else {
              AccessControlConfig.DISABLED_WHATSAPP_MESSAGE
            }
          val opened = WhatsAppHelper.openWhatsApp(context = context, rawPhone = "", message = msg)
          if (!opened) {
            errorMessage = "No se pudo abrir WhatsApp en este dispositivo."
          }
          showRequestAccessDialog = false
        },
        onAdminGoogleSignIn = {
          showRequestAccessDialog = false
          startGoogleDirectSignIn()
        },
        onDismiss = { showRequestAccessDialog = false },
      )
    }
  }
}

@Composable
internal fun MalvinasLoginShell(
  username: String,
  onUsernameChange: (String) -> Unit,
  password: String,
  onPasswordChange: (String) -> Unit,
  isLoading: Boolean,
  loadingLabel: String,
  errorMessage: String?,
  secondaryInfoError: String? = null,
  onPrimarySubmit: () -> Unit,
  onRequestAccessClick: () -> Unit,
  usernameTestTag: String,
  passwordTestTag: String,
  primaryButtonTestTag: String,
  requestAccessTestTag: String,
  footerActionLabel: String? = null,
  footerActionTestTag: String? = null,
  onFooterAction: (() -> Unit)? = null,
) {
  var passwordVisible by remember { mutableStateOf(false) }

  MalvinasProTheme(darkTheme = true) {
    val colorScheme = MaterialTheme.colorScheme
    val fieldShape = RoundedCornerShape(14.dp)
    val buttonShape = RoundedCornerShape(16.dp)
    val fieldColors =
      OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colorScheme.background.copy(alpha = 0.52f),
        unfocusedContainerColor = colorScheme.background.copy(alpha = 0.42f),
        disabledContainerColor = colorScheme.background.copy(alpha = 0.30f),
        focusedBorderColor = colorScheme.primary,
        unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.85f),
        focusedTextColor = colorScheme.onSurface,
        unfocusedTextColor = colorScheme.onSurface,
        focusedPlaceholderColor = colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = colorScheme.onSurfaceVariant,
        focusedLeadingIconColor = colorScheme.onSurface,
        unfocusedLeadingIconColor = colorScheme.onSurface.copy(alpha = 0.90f),
        focusedTrailingIconColor = colorScheme.onSurface,
        unfocusedTrailingIconColor = colorScheme.onSurface.copy(alpha = 0.90f),
        cursorColor = colorScheme.primary,
      )

    Surface(
      modifier = Modifier.fillMaxSize(),
      color = colorScheme.background,
    ) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors =
                  listOf(
                    colorScheme.surface.copy(alpha = 0.55f),
                    colorScheme.background,
                    colorScheme.background,
                  )
              )
            )
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .widthIn(max = 460.dp)
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(22.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // Brand Hero Banner
          Image(
            painter = painterResource(id = R.drawable.malvinas_login_banner),
            contentDescription = "Malvinas Pro Service",
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.fillMaxWidth()
                .aspectRatio(LOGIN_BANNER_ASPECT_RATIO)
                .clip(RoundedCornerShape(22.dp)),
          )

          // Main Dark Navy Login Card
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = colorScheme.surface.copy(alpha = 0.62f),
              ),
            border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.65f)),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = "Iniciar sesión",
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = colorScheme.onSurface,
                )
                Text(
                  text =
                    "Ingresá tu usuario y contraseña. Para confirmar que sos vos, el administrador verá un PIN aleatorio de 6 dígitos.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colorScheme.onSurfaceVariant,
                )
              }

              OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                placeholder = { Text("Usuario") },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                  )
                },
                keyboardOptions =
                  KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                  ),
                singleLine = true,
                enabled = !isLoading,
                shape = fieldShape,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().testTag(usernameTestTag),
              )

              OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = { Text("Contraseña") },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                  )
                },
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
                visualTransformation =
                  if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions =
                  KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                  ),
                keyboardActions = KeyboardActions(onDone = { onPrimarySubmit() }),
                singleLine = true,
                enabled = !isLoading,
                shape = fieldShape,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().testTag(passwordTestTag),
              )

              if (errorMessage != null) {
                Surface(
                  modifier = Modifier.fillMaxWidth(),
                  shape = MaterialTheme.shapes.small,
                  color = colorScheme.errorContainer,
                ) {
                  Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                  )
                }
              }

              Button(
                onClick = onPrimarySubmit,
                enabled = !isLoading,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(54.dp)
                    .testTag(primaryButtonTestTag),
                shape = buttonShape,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                  ),
              ) {
                if (isLoading) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = loadingLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                } else {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Login,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = "Ingresar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }

              OutlinedButton(
                onClick = onRequestAccessClick,
                enabled = !isLoading,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(54.dp)
                    .testTag(requestAccessTestTag),
                shape = buttonShape,
                border = BorderStroke(1.5.dp, colorScheme.primary),
                colors =
                  ButtonDefaults.outlinedButtonColors(
                    contentColor = colorScheme.primary,
                  ),
              ) {
                Icon(
                  imageVector = Icons.Filled.PersonAdd,
                  contentDescription = null,
                  modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Solicitar acceso",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Medium,
                )
              }

              if (secondaryInfoError != null) {
                Text(
                  text = secondaryInfoError,
                  style = MaterialTheme.typography.bodySmall,
                  color = colorScheme.error,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text =
                "Al ingresar con tu usuario y contraseña, el administrador recibe un PIN aleatorio de 6 dígitos para verificar tu identidad.",
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )

            if (footerActionLabel != null && onFooterAction != null) {
              TextButton(
                onClick = onFooterAction,
                enabled = !isLoading,
                modifier =
                  if (footerActionTestTag != null) {
                    Modifier.testTag(footerActionTestTag)
                  } else {
                    Modifier
                  },
              ) {
                Text(
                  text = footerActionLabel,
                  style = MaterialTheme.typography.labelMedium,
                  color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RequestAccessDialog(
  initialName: String,
  onSendWhatsAppRequest: (String) -> Unit,
  onAdminGoogleSignIn: () -> Unit,
  onDismiss: () -> Unit,
) {
  var nameInput by remember { mutableStateOf(initialName) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Solicitar acceso",
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text =
            "Pedile al administrador que te cree un usuario y contraseña para ingresar a Malvinas Pro Service.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = nameInput,
          onValueChange = { if (it.length <= 60) nameInput = it },
          label = { Text("Tu nombre o usuario deseado (opcional)") },
          placeholder = { Text("Ej: Carlos") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("request_access_name_input"),
        )
        Button(
          onClick = { onSendWhatsAppRequest(nameInput) },
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("request_access_whatsapp_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Pedir acceso por WhatsApp")
        }
        TextButton(
          onClick = onAdminGoogleSignIn,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Soy administrador · Entrar con Google",
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cerrar")
      }
    },
  )
}
