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
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.AlbicelesteRibbonStripe
import com.example.util.AccessControlConfig
import com.example.util.PendingLoginCredentials

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  var emailInput by remember { mutableStateOf("") }
  var passwordInput by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = coroutineScope,
    )
  }

  val startCredentialsLogin: () -> Unit = {
    if (!isLoading) {
      val normalizedEmail = AccessControlConfig.normalizeEmail(emailInput)
      when {
        !AccessControlConfig.isValidEmailFormat(normalizedEmail) -> {
          errorMessage = "Ingresá un usuario (correo electrónico) válido."
        }
        !AccessControlConfig.isAdminEmail(normalizedEmail) && passwordInput.isBlank() -> {
          errorMessage = "Ingresá la contraseña que te compartió el administrador."
        }
        else -> {
          errorMessage = null
          if (!AccessControlConfig.isAdminEmail(normalizedEmail)) {
            PendingLoginCredentials.set(normalizedEmail, passwordInput)
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
          .padding(horizontal = 20.dp, vertical = 14.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .widthIn(max = 480.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Compact Argentine Brand Hero Banner
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(148.dp)
              .clip(MaterialTheme.shapes.large),
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_hero_services),
            contentDescription = "Malvinas Pro Service banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(
            modifier =
              Modifier.fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(Color(0x33061326), Color(0xD9061326))
                  )
                ),
          ) {
            AlbicelesteRibbonStripe(
              height = 5.dp,
              showSunBadge = true,
              modifier = Modifier.align(Alignment.TopCenter),
            )
            Box(
              modifier = Modifier.fillMaxSize().padding(16.dp),
              contentAlignment = Alignment.BottomStart,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Image(
                  painter = painterResource(id = R.drawable.malvinas_logo),
                  contentDescription = "Logo de Malvinas Pro Service",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(56.dp).clip(CircleShape),
                )
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Filled.Shield,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "ARGENTINA · SERVICIOS Y FINANZAS",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.secondary,
                    )
                  }
                  Text(
                    text = "Malvinas Pro Service",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }
            }
          }
        }

        // Usuario y Contraseña Login Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = "Iniciar sesión",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Ingresá tu usuario (correo) y contraseña para entrar a la aplicación.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
              value = emailInput,
              onValueChange = {
                if (it.length <= 128) {
                  emailInput = it
                  errorMessage = null
                }
              },
              label = { Text("Usuario (correo)") },
              placeholder = { Text("ejemplo@gmail.com") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.AlternateEmail,
                  contentDescription = null,
                )
              },
              keyboardOptions =
                KeyboardOptions(
                  keyboardType = KeyboardType.Email,
                  imeAction = ImeAction.Next,
                ),
              singleLine = true,
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth().testTag("login_email_input"),
            )

            OutlinedTextField(
              value = passwordInput,
              onValueChange = {
                if (it.length <= AccessControlConfig.MAX_PASSWORD_LENGTH) {
                  passwordInput = it
                  errorMessage = null
                }
              },
              label = { Text("Contraseña") },
              placeholder = { Text("Ingresá tu contraseña") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Lock,
                  contentDescription = null,
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
              keyboardActions = KeyboardActions(onDone = { startCredentialsLogin() }),
              singleLine = true,
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth().testTag("login_password_input"),
            )

            if (errorMessage != null) {
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
              ) {
                Text(
                  text = errorMessage!!,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }

            Button(
              onClick = startCredentialsLogin,
              enabled = !isLoading,
              modifier =
                Modifier.fillMaxWidth()
                  .height(52.dp)
                  .testTag("login_with_credentials_button"),
              shape = RoundedCornerShape(14.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
              if (isLoading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Conectando...", style = MaterialTheme.typography.titleMedium)
              } else {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Login,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Ingresar",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              HorizontalDivider(modifier = Modifier.weight(1f))
              Text(
                text = "o",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              HorizontalDivider(modifier = Modifier.weight(1f))
            }

            OutlinedButton(
              onClick = {
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
              },
              enabled = !isLoading,
              modifier =
                Modifier.fillMaxWidth()
                  .height(48.dp)
                  .testTag("google_sign_in_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text(
                text = "Continuar con Google (Admin)",
                style = MaterialTheme.typography.titleSmall,
              )
            }
          }
        }

        Text(
          text =
            "Al ingresar se valida tu cuenta de Google autorizada junto con tu contraseña de acceso.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
      }
    }
  }
}
