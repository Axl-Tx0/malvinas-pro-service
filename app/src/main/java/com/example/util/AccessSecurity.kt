package com.example.util

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object AccessControlConfig {
  const val ADMIN_EMAIL = "axeldelvalle737@gmail.com"
  const val ADMIN_ROLE_LABEL = "Admin"
  const val DISABLED_WHATSAPP_MESSAGE = "Buenas Quiero usar la aplicación"
  const val MIN_PASSWORD_LENGTH = 6
  const val MAX_PASSWORD_LENGTH = 64
  const val PIN_LENGTH = 6
  const val PIN_TTL_MILLIS = 10 * 60 * 1000L

  private val EMAIL_REGEX = Regex("^[^/\\s@]+@[^/\\s@]+\\.[^/\\s@]+$")
  private val ACCOUNT_ID_REGEX = Regex("^[a-z0-9._@\\-]+$")

  fun normalizeEmail(raw: String?): String = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()

  fun normalizeAccountId(raw: String?): String =
    raw?.trim()?.removePrefix("@")?.lowercase(Locale.ROOT).orEmpty()

  fun isValidEmailFormat(raw: String): Boolean {
    val normalized = normalizeEmail(raw)
    return normalized.length in 3..128 && EMAIL_REGEX.matches(normalized)
  }

  /**
   * Accepts both simple usernames (e.g. "carlos01", "juan_cba", "ana.perez")
   * and legacy email addresses (e.g. "usuario@gmail.com").
   */
  fun isValidAccountId(raw: String): Boolean {
    val normalized = normalizeAccountId(raw)
    return normalized.length in 3..128 &&
      ACCOUNT_ID_REGEX.matches(normalized) &&
      !normalized.contains("..")
  }

  fun isAdminEmail(raw: String?): Boolean = normalizeEmail(raw) == ADMIN_EMAIL

  fun formatPinForDisplay(rawPin: String): String {
    val digits = rawPin.filter { it.isDigit() }
    return if (digits.length == 6) {
      "${digits.substring(0, 3)} ${digits.substring(3, 6)}"
    } else {
      digits
    }
  }

  fun currentDeviceLabel(): String =
    formatDeviceLabel(
      manufacturer = android.os.Build.MANUFACTURER,
      model = android.os.Build.MODEL,
      androidRelease = android.os.Build.VERSION.RELEASE,
    )

  internal fun formatDeviceLabel(
    manufacturer: String?,
    model: String?,
    androidRelease: String?,
  ): String {
    val cleanManufacturer = manufacturer?.trim().orEmpty()
    val cleanModel = model?.trim().orEmpty()
    val deviceName =
      when {
        cleanManufacturer.isBlank() && cleanModel.isBlank() -> "Dispositivo Android"
        cleanModel.isBlank() -> cleanManufacturer.replaceFirstChar { it.uppercaseChar() }
        cleanManufacturer.isBlank() -> cleanModel
        cleanModel.lowercase(Locale.ROOT).startsWith(cleanManufacturer.lowercase(Locale.ROOT)) ->
          cleanModel.replaceFirstChar { it.uppercaseChar() }
        else -> "${cleanManufacturer.replaceFirstChar { it.uppercaseChar() }} $cleanModel"
      }
    val cleanRelease = androidRelease?.trim().orEmpty()
    val combined =
      if (cleanRelease.isNotBlank()) "$deviceName · Android $cleanRelease" else deviceName
    return combined.take(120)
  }
}

data class PasswordHashResult(
  val hashHex: String,
  val saltHex: String,
)

object PasswordHasher {
  private const val HMAC_ALGORITHM = "HmacSHA256"
  private const val PBKDF2_ITERATIONS = 100_000
  private const val SALT_BYTE_LENGTH = 16
  private const val HASH_BYTE_LENGTH = 32

  private const val UPPERCASE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
  private const val LOWERCASE_CHARS = "abcdefghijkmnopqrstuvwxyz"
  private const val DIGIT_CHARS = "23456789"
  private const val ALL_CHARS = UPPERCASE_CHARS + LOWERCASE_CHARS + DIGIT_CHARS

  private val secureRandom = SecureRandom()

  fun hashPassword(password: String): PasswordHashResult {
    val salt = ByteArray(SALT_BYTE_LENGTH)
    secureRandom.nextBytes(salt)
    val derived = pbkdf2HmacSha256(password.toByteArray(Charsets.UTF_8), salt, PBKDF2_ITERATIONS)
    return PasswordHashResult(
      hashHex = derived.toHexString(),
      saltHex = salt.toHexString(),
    )
  }

  fun verifyPassword(
    password: String,
    saltHex: String,
    expectedHashHex: String,
  ): Boolean {
    if (password.isEmpty()) return false
    val saltBytes = saltHex.hexToBytesOrNull() ?: return false
    val expectedHashBytes = expectedHashHex.hexToBytesOrNull() ?: return false
    if (saltBytes.size != SALT_BYTE_LENGTH || expectedHashBytes.size != HASH_BYTE_LENGTH) {
      return false
    }
    val derived = pbkdf2HmacSha256(password.toByteArray(Charsets.UTF_8), saltBytes, PBKDF2_ITERATIONS)
    return MessageDigest.isEqual(derived, expectedHashBytes)
  }

  fun generateSecurePassword(length: Int = 10): String {
    val safeLength = length.coerceIn(AccessControlConfig.MIN_PASSWORD_LENGTH, 24)
    val chars = CharArray(safeLength)
    chars[0] = UPPERCASE_CHARS[secureRandom.nextInt(UPPERCASE_CHARS.length)]
    chars[1] = LOWERCASE_CHARS[secureRandom.nextInt(LOWERCASE_CHARS.length)]
    chars[2] = DIGIT_CHARS[secureRandom.nextInt(DIGIT_CHARS.length)]
    for (i in 3 until safeLength) {
      chars[i] = ALL_CHARS[secureRandom.nextInt(ALL_CHARS.length)]
    }
    for (i in chars.lastIndex downTo 1) {
      val j = secureRandom.nextInt(i + 1)
      val tmp = chars[i]
      chars[i] = chars[j]
      chars[j] = tmp
    }
    return String(chars)
  }

  /**
   * Generates a cryptographically random numeric verification PIN (default 6 digits, e.g. "048291").
   */
  fun generateRandomPin(length: Int = AccessControlConfig.PIN_LENGTH): String {
    val safeLength = length.coerceIn(4, 8)
    val chars = CharArray(safeLength)
    for (i in 0 until safeLength) {
      chars[i] = ('0' + secureRandom.nextInt(10))
    }
    return String(chars)
  }

  /**
   * RFC 2898 PBKDF2-HMAC-SHA256 for a 32-byte derived key (single HMAC-SHA256 block).
   * Implemented directly over javax.crypto.Mac("HmacSHA256") for full compatibility from minSdk 24.
   */
  internal fun pbkdf2HmacSha256(
    passwordBytes: ByteArray,
    salt: ByteArray,
    iterations: Int,
  ): ByteArray {
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(SecretKeySpec(passwordBytes, HMAC_ALGORITHM))

    val saltWithBlockIndex = ByteArray(salt.size + 4)
    System.arraycopy(salt, 0, saltWithBlockIndex, 0, salt.size)
    saltWithBlockIndex[salt.size] = 0
    saltWithBlockIndex[salt.size + 1] = 0
    saltWithBlockIndex[salt.size + 2] = 0
    saltWithBlockIndex[salt.size + 3] = 1

    var u = mac.doFinal(saltWithBlockIndex)
    val result = u.copyOf()
    for (i in 2..iterations) {
      u = mac.doFinal(u)
      for (b in result.indices) {
        result[b] = (result[b].toInt() xor u[b].toInt()).toByte()
      }
    }
    return result
  }

  private fun ByteArray.toHexString(): String {
    val chars = CharArray(size * 2)
    val hexDigits = "0123456789abcdef"
    for (i in indices) {
      val value = this[i].toInt() and 0xFF
      chars[i * 2] = hexDigits[value ushr 4]
      chars[i * 2 + 1] = hexDigits[value and 0x0F]
    }
    return String(chars)
  }

  private fun String.hexToBytesOrNull(): ByteArray? {
    if (length % 2 != 0) return null
    val out = ByteArray(length / 2)
    for (i in out.indices) {
      val hi = Character.digit(this[i * 2], 16)
      val lo = Character.digit(this[i * 2 + 1], 16)
      if (hi == -1 || lo == -1) return null
      out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
  }
}

object AccessVerificationStore {
  private const val PREFS_NAME = "malvinas_access_verification"
  private const val KEY_EMAIL = "verified_email"
  private const val KEY_VERSION = "verified_password_version"

  fun isVerified(context: Context, accountId: String, expectedVersion: Long): Boolean {
    if (expectedVersion < 1L) return false
    val normalized = AccessControlConfig.normalizeAccountId(accountId)
    if (normalized.isBlank()) return false
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedAccount = prefs.getString(KEY_EMAIL, null)
    val savedVersion = prefs.getLong(KEY_VERSION, -1L)
    return savedAccount == normalized && savedVersion == expectedVersion
  }

  fun markVerified(context: Context, accountId: String, passwordVersion: Long) {
    val normalized = AccessControlConfig.normalizeAccountId(accountId)
    if (normalized.isBlank() || passwordVersion < 1L) return
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs
      .edit()
      .putString(KEY_EMAIL, normalized)
      .putLong(KEY_VERSION, passwordVersion)
      .apply()
  }

  fun clear(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
  }
}

/**
 * Volatile in-memory holder used only for the handoff between the initial login screen
 * (where the user types usuario + contraseña) and UserAccessGate right after Google Sign-In.
 * Never persisted to disk and wiped immediately once consumed or on sign-out.
 */
object PendingLoginCredentials {
  @Volatile private var pendingAccountId: String? = null
  @Volatile private var pendingPassword: String? = null

  @Synchronized
  fun set(accountId: String, password: String) {
    pendingAccountId = AccessControlConfig.normalizeAccountId(accountId)
    pendingPassword = password
  }

  @Synchronized
  fun consume(): Pair<String, String>? {
    val savedAccount = pendingAccountId
    val savedPassword = pendingPassword
    pendingAccountId = null
    pendingPassword = null
    return if (!savedAccount.isNullOrBlank() && !savedPassword.isNullOrEmpty()) {
      savedAccount to savedPassword
    } else {
      null
    }
  }

  @Synchronized
  fun consumeForEmail(email: String): Pair<Boolean, String?> {
    val savedAccount = pendingAccountId ?: return false to null
    val savedPassword = pendingPassword
    pendingAccountId = null
    pendingPassword = null
    val normalizedTarget = AccessControlConfig.normalizeAccountId(email)
    return if (savedAccount == normalizedTarget && !savedPassword.isNullOrEmpty()) {
      true to savedPassword
    } else {
      false to null
    }
  }

  @Synchronized
  fun clear() {
    pendingAccountId = null
    pendingPassword = null
  }
}
