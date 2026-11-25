package com.example.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

data class AppLockConfig(
  val enabled: Boolean = false,
  val biometricEnabled: Boolean = false,
  val hasPinConfigured: Boolean = false,
  val pinLength: Int = 4,
  val lockOnBackgroundSeconds: Int = 0,
)

/**
 * Stores app-open PIN lock settings locally on device.
 * PIN is never stored in plaintext — always hashed with salted PBKDF2-HMAC-SHA256 via PasswordHasher.
 */
object AppLockStore {
  private const val PREFS_NAME = "malvinas_app_lock_prefs"
  private const val KEY_ENABLED = "lock_enabled"
  private const val KEY_BIOMETRIC = "biometric_enabled"
  private const val KEY_PIN_HASH = "pin_hash"
  private const val KEY_PIN_SALT = "pin_salt"
  private const val KEY_PIN_LENGTH = "pin_length"
  private const val KEY_BG_SECONDS = "bg_lock_seconds"
  private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
  private const val KEY_LOCKOUT_UNTIL = "lockout_until_millis"

  const val MIN_APP_PIN_LENGTH = 4
  const val MAX_APP_PIN_LENGTH = 6
  const val MAX_FAILED_ATTEMPTS = 5
  const val LOCKOUT_DURATION_MILLIS = 30_000L

  fun isValidAppPin(rawPin: String): Boolean {
    return rawPin.length in MIN_APP_PIN_LENGTH..MAX_APP_PIN_LENGTH && rawPin.all { it.isDigit() }
  }

  fun getConfig(context: Context): AppLockConfig {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val hash = prefs.getString(KEY_PIN_HASH, null).orEmpty()
    val salt = prefs.getString(KEY_PIN_SALT, null).orEmpty()
    val hasPin = hash.length == 64 && salt.length == 32
    val enabled = prefs.getBoolean(KEY_ENABLED, false) && hasPin
    val bio = prefs.getBoolean(KEY_BIOMETRIC, false) && isBiometricAvailable(context)
    val pinLen = prefs.getInt(KEY_PIN_LENGTH, 4).coerceIn(MIN_APP_PIN_LENGTH, MAX_APP_PIN_LENGTH)
    val bgSec = prefs.getInt(KEY_BG_SECONDS, 0).coerceAtLeast(0)
    return AppLockConfig(
      enabled = enabled,
      biometricEnabled = bio,
      hasPinConfigured = hasPin,
      pinLength = pinLen,
      lockOnBackgroundSeconds = bgSec,
    )
  }

  fun setPinAndEnable(
    context: Context,
    rawPin: String,
    enableBiometric: Boolean = false,
  ): Boolean {
    if (!isValidAppPin(rawPin)) return false
    val hashed = PasswordHasher.hashPassword(rawPin)
    val bioSupported = isBiometricAvailable(context)
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs
      .edit()
      .putString(KEY_PIN_HASH, hashed.hashHex)
      .putString(KEY_PIN_SALT, hashed.saltHex)
      .putInt(KEY_PIN_LENGTH, rawPin.length)
      .putBoolean(KEY_ENABLED, true)
      .putBoolean(KEY_BIOMETRIC, enableBiometric && bioSupported)
      .putInt(KEY_FAILED_ATTEMPTS, 0)
      .putLong(KEY_LOCKOUT_UNTIL, 0L)
      .apply()
    return true
  }

  fun setBiometricEnabled(context: Context, enabled: Boolean) {
    val bioSupported = isBiometricAvailable(context)
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_BIOMETRIC, enabled && bioSupported).apply()
  }

  fun setBackgroundLockSeconds(context: Context, seconds: Int) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(KEY_BG_SECONDS, seconds.coerceIn(0, 600)).apply()
  }

  fun disableLock(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs
      .edit()
      .putBoolean(KEY_ENABLED, false)
      .putBoolean(KEY_BIOMETRIC, false)
      .remove(KEY_PIN_HASH)
      .remove(KEY_PIN_SALT)
      .putInt(KEY_FAILED_ATTEMPTS, 0)
      .putLong(KEY_LOCKOUT_UNTIL, 0L)
      .apply()
  }

  fun getRemainingLockoutMillis(context: Context, nowMillis: Long = System.currentTimeMillis()): Long {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
    return (until - nowMillis).coerceAtLeast(0L)
  }

  fun verifyPin(context: Context, rawPin: String): Boolean {
    if (getRemainingLockoutMillis(context) > 0L) return false
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
    val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
    val ok = PasswordHasher.verifyPassword(rawPin, salt, hash)
    if (ok) {
      prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply()
    } else {
      val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
      val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts)
      if (attempts >= MAX_FAILED_ATTEMPTS) {
        editor.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_DURATION_MILLIS)
        editor.putInt(KEY_FAILED_ATTEMPTS, 0)
      }
      editor.apply()
    }
    return ok
  }

  fun isBiometricAvailable(context: Context): Boolean {
    val manager = BiometricManager.from(context.applicationContext)
    val status = manager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
    return status == BiometricManager.BIOMETRIC_SUCCESS
  }

  fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String = "Desbloquear Malvinas Pro Service",
    subtitle: String = "Usá tu huella o rostro para acceder",
    negativeButtonText: String = "Usar PIN",
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
  ) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback =
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          super.onAuthenticationError(errorCode, errString)
          if (
            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
              errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
              errorCode != BiometricPrompt.ERROR_CANCELED
          ) {
            onError(errString.toString())
          }
        }
      }

    val promptInfo =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(negativeButtonText)
        .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        .build()

    BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
  }
}
