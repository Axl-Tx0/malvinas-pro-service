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
  }
}
