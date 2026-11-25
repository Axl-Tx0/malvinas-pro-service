package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessSecurityTest {

  @Test
  fun pbkdf2HmacSha256_matchesRfcTestVector() {
    // Standard PBKDF2-HMAC-SHA256 test vector: P="password", S="salt", c=1, dkLen=32
    val derived =
      PasswordHasher.pbkdf2HmacSha256(
        passwordBytes = "password".toByteArray(Charsets.UTF_8),
        salt = "salt".toByteArray(Charsets.UTF_8),
        iterations = 1,
      )
    val hex = derived.joinToString("") { "%02x".format(it) }
    assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", hex)
  }

  @Test
  fun hashAndVerifyPassword_usesUniqueSaltsAndValidatesCorrectly() {
    val first = PasswordHasher.hashPassword("Malvinas2026!")
    val second = PasswordHasher.hashPassword("Malvinas2026!")

    assertEquals(64, first.hashHex.length)
    assertEquals(32, first.saltHex.length)
    assertNotEquals("Each hash must generate a fresh random salt", first.saltHex, second.saltHex)
    assertNotEquals(first.hashHex, second.hashHex)

    assertTrue(PasswordHasher.verifyPassword("Malvinas2026!", first.saltHex, first.hashHex))
    assertTrue(PasswordHasher.verifyPassword("Malvinas2026!", second.saltHex, second.hashHex))
    assertFalse(PasswordHasher.verifyPassword("WrongPassword", first.saltHex, first.hashHex))
    assertFalse(PasswordHasher.verifyPassword("", first.saltHex, first.hashHex))
    assertFalse(PasswordHasher.verifyPassword("Malvinas2026!", "short", first.hashHex))
    assertFalse(PasswordHasher.verifyPassword("Malvinas2026!", first.saltHex, "nothex"))
  }

  @Test
  fun generateSecurePassword_meetsLengthAndCharacterMix() {
    val pwd = PasswordHasher.generateSecurePassword(10)
    assertEquals(10, pwd.length)
    assertTrue(pwd.any { it.isUpperCase() })
    assertTrue(pwd.any { it.isLowerCase() })
    assertTrue(pwd.any { it.isDigit() })
  }

  @Test
  fun accessControlConfig_normalizesAndValidatesEmails() {
    assertEquals("axeldelvalle737@gmail.com", AccessControlConfig.normalizeEmail("  AxelDelValle737@GMAIL.com  "))
    assertTrue(AccessControlConfig.isAdminEmail("AXELDELVALLE737@gmail.com"))
    assertFalse(AccessControlConfig.isAdminEmail("otro@gmail.com"))
    assertTrue(AccessControlConfig.isValidEmailFormat("usuario.prueba@gmail.com"))
    assertFalse(AccessControlConfig.isValidEmailFormat("correo_invalido"))
    assertFalse(AccessControlConfig.isValidEmailFormat("a/b@gmail.com"))
  }

  @Test
  fun pendingLoginCredentials_consumesOnceAndWipesMemory() {
    PendingLoginCredentials.set("  Usuario@Gmail.com ", "Clave123")
    val (matched, pwd) = PendingLoginCredentials.consumeForEmail("usuario@gmail.com")
    assertTrue(matched)
    assertEquals("Clave123", pwd)

    // Second consume must be empty (single-use wipe)
    val (matchedAgain, pwdAgain) = PendingLoginCredentials.consumeForEmail("usuario@gmail.com")
    assertFalse(matchedAgain)
    assertEquals(null, pwdAgain)

    // Mismatched email wipes and returns false
    PendingLoginCredentials.set("uno@gmail.com", "Clave999")
    val (wrongMatch, wrongPwd) = PendingLoginCredentials.consumeForEmail("otro@gmail.com")
    assertFalse(wrongMatch)
    assertEquals(null, wrongPwd)

    // Username-based consume() works with @ prefix normalization and wipes after single read
    PendingLoginCredentials.set("  @Carlos_01  ", "Secreta99")
    val consumed = PendingLoginCredentials.consume()
    assertEquals("carlos_01" to "Secreta99", consumed)
    assertEquals(null, PendingLoginCredentials.consume())
  }

  @Test
  fun accessControlConfig_validatesUsernamesAndFormatsPins() {
    assertEquals("carlos01", AccessControlConfig.normalizeAccountId(" @Carlos01 "))
    assertTrue(AccessControlConfig.isValidAccountId("carlos01"))
    assertTrue(AccessControlConfig.isValidAccountId("juan_cba-2"))
    assertTrue(AccessControlConfig.isValidAccountId("ana.perez@gmail.com"))
    assertFalse(AccessControlConfig.isValidAccountId("ab"))
    assertFalse(AccessControlConfig.isValidAccountId("con espacio"))
    assertFalse(AccessControlConfig.isValidAccountId("mal/ruta"))
    assertFalse(AccessControlConfig.isValidAccountId("bad..id"))

    assertEquals("482 915", AccessControlConfig.formatPinForDisplay("482915"))
    assertEquals("001 234", AccessControlConfig.formatPinForDisplay("001-234"))
  }

  @Test
  fun generateRandomPin_producesSixNumericDigitsAndHashesCleanly() {
    val pins = (1..20).map { PasswordHasher.generateRandomPin(6) }
    pins.forEach { pin ->
      assertEquals(6, pin.length)
      assertTrue("PIN must be numeric: $pin", pin.all { it.isDigit() })
    }
    assertTrue("Random PINs should vary across calls", pins.toSet().size > 1)

    val pin = pins.first()
    val hashed = PasswordHasher.hashPassword(pin)
    assertTrue(PasswordHasher.verifyPassword(pin, hashed.saltHex, hashed.hashHex))
    assertFalse(PasswordHasher.verifyPassword("999999".takeIf { it != pin } ?: "000000", hashed.saltHex, hashed.hashHex))
  }

  @Test
  fun formatDeviceLabel_formatsCleanlyWithoutDuplicateManufacturer() {
    assertEquals(
      "Samsung SM-A546E · Android 14",
      AccessControlConfig.formatDeviceLabel("samsung", "SM-A546E", "14"),
    )
    assertEquals(
      "Google Pixel 8 · Android 15",
      AccessControlConfig.formatDeviceLabel("Google", "Google Pixel 8", "15"),
    )
    assertEquals(
      "Dispositivo Android",
      AccessControlConfig.formatDeviceLabel("", "", ""),
    )
  }
}
