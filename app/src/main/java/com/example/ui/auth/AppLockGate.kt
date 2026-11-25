package com.example.ui.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.components.AlbicelesteRibbonStripe
import com.example.util.AppLockStore
import kotlinx.coroutines.delay

val LocalLockAppNow = staticCompositionLocalOf<() -> Unit> { {} }

private fun Context.findFragmentActivity(): FragmentActivity? {
  var current: Context? = this
  while (current is ContextWrapper) {
    if (current is FragmentActivity) return current
    current = current.baseContext
  }
  return current as? FragmentActivity
}

@Composable
fun AppLockGate(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var isLocked by remember { mutableStateOf(AppLockStore.getConfig(context).enabled) }
  var backgroundedAtMillis by remember { mutableLongStateOf(0L) }

  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> {
          backgroundedAtMillis = System.currentTimeMillis()
        }
        Lifecycle.Event.ON_START -> {
          val cfg = AppLockStore.getConfig(context)
          if (cfg.enabled && backgroundedAtMillis > 0L) {
            val elapsed = System.currentTimeMillis() - backgroundedAtMillis
            if (elapsed >= cfg.lockOnBackgroundSeconds * 1000L) {
              isLocked = true
            }
          }
          backgroundedAtMillis = 0L
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val lockNow: () -> Unit = {
    if (AppLockStore.getConfig(context).enabled) {
      isLocked = true
    }
  }

  CompositionLocalProvider(LocalLockAppNow provides lockNow) {
    Box(modifier = Modifier.fillMaxSize()) {
      content()
      if (isLocked) {
        BackHandler(enabled = true) { /* Prevent bypassing app lock with back button */ }
        AppLockScreen(
          onUnlocked = { isLocked = false },
        )
      }
    }
  }
}

@Composable
private fun AppLockScreen(onUnlocked: () -> Unit) {
  val context = LocalContext.current
  val config = remember { AppLockStore.getConfig(context) }
  val activity = remember(context) { context.findFragmentActivity() }

  var pinInput by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }
  var remainingLockoutMs by remember { mutableLongStateOf(AppLockStore.getRemainingLockoutMillis(context)) }

  LaunchedEffect(remainingLockoutMs) {
    while (remainingLockoutMs > 0L) {
      delay(500L)
      remainingLockoutMs = AppLockStore.getRemainingLockoutMillis(context)
    }
  }

  val triggerBiometric: () -> Unit = {
    if (activity != null && config.biometricEnabled) {
      AppLockStore.showBiometricPrompt(
        activity = activity,
        onSuccess = onUnlocked,
        onError = { msg -> errorText = msg },
      )
    }
  }

  LaunchedEffect(config.biometricEnabled, activity) {
    if (config.biometricEnabled && activity != null) {
      triggerBiometric()
    }
  }

  fun submitPin(candidate: String) {
    val lockout = AppLockStore.getRemainingLockoutMillis(context)
    if (lockout > 0L) {
      remainingLockoutMs = lockout
      pinInput = ""
      return
    }
    if (AppLockStore.verifyPin(context, candidate)) {
      errorText = null
      onUnlocked()
    } else {
      val updatedLockout = AppLockStore.getRemainingLockoutMillis(context)
      remainingLockoutMs = updatedLockout
      errorText =
        if (updatedLockout > 0L) {
          "Demasiados intentos. Esperá ${(updatedLockout / 1000L) + 1}s."
        } else {
          "PIN incorrecto. Intentá nuevamente."
        }
      pinInput = ""
    }
  }

  Surface(
    modifier =
      Modifier.fillMaxSize()
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = {},
        )
        .testTag("app_lock_screen"),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                  MaterialTheme.colorScheme.background,
                )
            )
          )
          .safeDrawingPadding()
          .padding(horizontal = 24.dp, vertical = 20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Image(
          painter = painterResource(id = R.drawable.malvinas_logo),
          contentDescription = "Logo Malvinas Pro Service",
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(64.dp).clip(CircleShape),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "Malvinas Pro Service",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Ingresá tu PIN de ${config.pinLength} dígitos para desbloquear",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }

        // PIN dots indicator
        Row(
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(vertical = 8.dp),
        ) {
          repeat(config.pinLength) { idx ->
            val filled = idx < pinInput.length
            Surface(
              shape = CircleShape,
              color =
                if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
              border =
                BorderStroke(
                  1.5.dp,
                  if (filled) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outline,
                ),
              modifier = Modifier.size(16.dp),
            ) {}
          }
        }

        if (remainingLockoutMs > 0L) {
          Text(
            text = "Bloqueo temporal: esperá ${(remainingLockoutMs / 1000L) + 1} segundos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
          )
        } else if (errorText != null) {
          Text(
            text = errorText!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
          )
        }

        // Numeric keypad
        val rows =
          listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
          )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          rows.forEach { rowDigits ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
              rowDigits.forEach { digit ->
                KeypadDigitButton(
                  label = digit,
                  enabled = remainingLockoutMs == 0L,
                  onClick = {
                    if (pinInput.length < config.pinLength) {
                      errorText = null
                      val next = pinInput + digit
                      pinInput = next
                      if (next.length == config.pinLength) {
                        submitPin(next)
                      }
                    }
                  },
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (config.biometricEnabled && activity != null) {
              Surface(
                onClick = triggerBiometric,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp).testTag("app_lock_biometric_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = "Desbloquear con huella o rostro",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                  )
                }
              }
            } else {
              Spacer(modifier = Modifier.size(64.dp))
            }

            KeypadDigitButton(
              label = "0",
              enabled = remainingLockoutMs == 0L,
              onClick = {
                if (pinInput.length < config.pinLength) {
                  errorText = null
                  val next = pinInput + "0"
                  pinInput = next
                  if (next.length == config.pinLength) {
                    submitPin(next)
                  }
                }
              },
            )

            Surface(
              onClick = {
                if (pinInput.isNotEmpty()) {
                  pinInput = pinInput.dropLast(1)
                }
              },
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.size(64.dp).testTag("app_lock_backspace_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Backspace,
                  contentDescription = "Borrar dígito",
                  modifier = Modifier.size(24.dp),
                )
              }
            }
          }
        }

        if (config.biometricEnabled && activity != null) {
          TextButton(onClick = triggerBiometric) {
            Icon(
              imageVector = Icons.Filled.Fingerprint,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Usar huella / reconocimiento facial")
          }
        }
      }
    }
  }
}

@Composable
private fun KeypadDigitButton(
  label: String,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.size(64.dp).testTag("app_lock_key_$label"),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

private val BG_DELAY_OPTIONS =
  listOf(
    0 to "Al instante",
    30 to "30 seg",
    60 to "1 min",
    300 to "5 min",
  )

@Composable
fun AppLockSettingsCard(
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lockAppNow = LocalLockAppNow.current
  var config by remember { mutableStateOf(AppLockStore.getConfig(context)) }
  val biometricSupported = remember { AppLockStore.isBiometricAvailable(context) }

  var showPinDialog by remember { mutableStateOf(false) }
  var showDisableDialog by remember { mutableStateOf(false) }

  Card(
    modifier = modifier.fillMaxWidth().testTag("settings_app_lock_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      AlbicelesteRibbonStripe(height = 4.dp, showSunBadge = false)
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = if (config.enabled) Icons.Filled.Shield else Icons.Filled.LockOpen,
            contentDescription = null,
            tint =
              if (config.enabled) MaterialTheme.colorScheme.tertiary
              else MaterialTheme.colorScheme.primary,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Bloqueo al abrir (Huella / PIN)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                if (config.enabled) {
                  "Protección activa con PIN de ${config.pinLength} dígitos" +
                    if (config.biometricEnabled) " + biometría." else "."
                } else {
                  "Protegé tus saldos y clientes pidiendo PIN o huella cada vez que abrís la app."
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (!config.enabled) {
          Button(
            onClick = { showPinDialog = true },
            modifier = Modifier.fillMaxWidth().testTag("settings_enable_app_lock_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Activar bloqueo con PIN / Huella")
          }
        } else {
          if (biometricSupported) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                  text = "Desbloquear con huella / rostro",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = "Usa el lector biométrico de tu teléfono antes de pedir el PIN.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = config.biometricEnabled,
                onCheckedChange = { enabled ->
                  AppLockStore.setBiometricEnabled(context, enabled)
                  config = AppLockStore.getConfig(context)
                  onNotify(
                    if (enabled) "Desbloqueo biométrico activado"
                    else "Desbloqueo biométrico desactivado"
                  )
                },
                modifier = Modifier.testTag("settings_biometric_switch"),
              )
            }
          }

          HorizontalDivider()

          Text(
            text = "Bloquear automáticamente al volver del fondo:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            BG_DELAY_OPTIONS.forEach { (seconds, label) ->
              FilterChip(
                selected = config.lockOnBackgroundSeconds == seconds,
                onClick = {
                  AppLockStore.setBackgroundLockSeconds(context, seconds)
                  config = AppLockStore.getConfig(context)
                },
                label = { Text(label) },
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { showPinDialog = true },
              modifier = Modifier.weight(1f),
            ) {
              Text("Cambiar PIN")
            }
            FilledTonalButton(
              onClick = lockAppNow,
              modifier = Modifier.weight(1f).testTag("settings_lock_now_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Bloquear ahora")
            }
          }

          TextButton(
            onClick = { showDisableDialog = true },
            modifier = Modifier.align(Alignment.End),
          ) {
            Text("Desactivar bloqueo", color = MaterialTheme.colorScheme.error)
          }
        }
      }
    }
  }

  if (showPinDialog) {
    ConfigureAppPinDialog(
      isChanging = config.enabled,
      biometricSupported = biometricSupported,
      initialBiometric = if (config.enabled) config.biometricEnabled else biometricSupported,
      onConfirm = { newPin, enableBio ->
        val ok = AppLockStore.setPinAndEnable(context, newPin, enableBio)
        if (ok) {
          if (!config.enabled) {
            // Default to 30s grace period so switching to WhatsApp briefly is smooth
            AppLockStore.setBackgroundLockSeconds(context, 30)
          }
          config = AppLockStore.getConfig(context)
          showPinDialog = false
          onNotify("Bloqueo de seguridad guardado")
        }
      },
      onDismiss = { showPinDialog = false },
    )
  }

  if (showDisableDialog) {
    DisableAppLockDialog(
      onConfirmDisable = { currentPin ->
        if (AppLockStore.verifyPin(context, currentPin)) {
          AppLockStore.disableLock(context)
          config = AppLockStore.getConfig(context)
          showDisableDialog = false
          onNotify("Bloqueo de aplicación desactivado")
          true
        } else {
          false
        }
      },
      onDismiss = { showDisableDialog = false },
    )
  }
}

@Composable
private fun ConfigureAppPinDialog(
  isChanging: Boolean,
  biometricSupported: Boolean,
  initialBiometric: Boolean,
  onConfirm: (pin: String, enableBiometric: Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  var pin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var enableBio by remember { mutableStateOf(initialBiometric) }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (isChanging) "Cambiar PIN de acceso" else "Crear PIN de acceso") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Elegí un PIN numérico de 4 a 6 dígitos para proteger la app en este dispositivo.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = pin,
          onValueChange = {
            pin = it.filter { ch -> ch.isDigit() }.take(AppLockStore.MAX_APP_PIN_LENGTH)
            error = null
          },
          label = { Text("Nuevo PIN (4–6 dígitos)") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("config_pin_input"),
        )
        OutlinedTextField(
          value = confirmPin,
          onValueChange = {
            confirmPin = it.filter { ch -> ch.isDigit() }.take(AppLockStore.MAX_APP_PIN_LENGTH)
            error = null
          },
          label = { Text("Repetí el PIN") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("config_pin_confirm_input"),
        )
        if (biometricSupported) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Habilitar huella / rostro",
              style = MaterialTheme.typography.bodyMedium,
            )
            Switch(checked = enableBio, onCheckedChange = { enableBio = it })
          }
        }
        if (error != null) {
          Text(
            text = error!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          when {
            !AppLockStore.isValidAppPin(pin) ->
              error = "El PIN debe tener entre 4 y 6 números."
            pin != confirmPin ->
              error = "Los PIN ingresados no coinciden."
            else -> onConfirm(pin, enableBio)
          }
        },
        modifier = Modifier.testTag("config_pin_save_button"),
      ) {
        Text("Guardar PIN")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

@Composable
private fun DisableAppLockDialog(
  onConfirmDisable: (String) -> Boolean,
  onDismiss: () -> Unit,
) {
  var currentPin by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Desactivar bloqueo") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Ingresá tu PIN actual para desactivar el bloqueo al abrir la aplicación.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = currentPin,
          onValueChange = {
            currentPin = it.filter { ch -> ch.isDigit() }.take(AppLockStore.MAX_APP_PIN_LENGTH)
            error = null
          },
          label = { Text("PIN actual") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
          Text(
            text = error!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (!onConfirmDisable(currentPin)) {
            error = "PIN incorrecto."
          }
        },
      ) {
        Text("Desactivar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
