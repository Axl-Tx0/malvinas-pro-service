package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.FinancialTransaction
import com.example.data.model.ProfileType
import com.example.data.model.Reseller
import com.example.data.model.TransactionType
import com.example.data.model.UserSettings
import com.example.data.model.VpnAccount
import com.example.util.AccessControlConfig
import com.example.util.PasswordHasher
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalvinasRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private suspend fun signInAuthorizedTestUser(email: String, displayName: String = "Usuario Test"): String {
    signInTestUser(AccessControlConfig.ADMIN_EMAIL)
    val accessRepo = AccessControlRepository(firestore, auth)
    val createRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        accessRepo.createAuthorizedUser(
          displayName = displayName,
          rawEmail = email,
          rawPassword = "TestPassword123",
          notes = "Autorizado para test",
        )
      }
    if (createRes.isFailure) {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        accessRepo.setUserEnabled(email, true).getOrThrow()
      }
    }
    return signInTestUser(email)
  }

  @Test
  fun transactions_crudAndRealtimeObservation_succeedsForOwner() = runBlocking {
    val uid = signInAuthorizedTestUser(ALICE_EMAIL, "Alice")
    val repo = MalvinasRepository(firestore, auth)

    val tx =
      FinancialTransaction(
        userId = uid,
        profileType = ProfileType.COMMON.id,
        type = TransactionType.INCOME.id,
        title = "Cobro Soporte",
        category = "Servicios",
        amount = 15000.0,
        monthKey = "2026-11",
        dateMillis = 1794783000000L,
      )
    val txId = withTimeout(DEFAULT_TIMEOUT_MS) { repo.addTransaction(tx).getOrThrow() }
    assertTrue(txId.isNotBlank())

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeTransactions(uid).first { list -> list.any { it.id == txId } }
      }
    assertEquals("Cobro Soporte", observed.first { it.id == txId }.title)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.deleteTransaction(txId).getOrThrow()
    }
  }

  @Test
  fun vpnAccounts_createAndRenew_succeedsForOwner() = runBlocking {
    val uid = signInAuthorizedTestUser(ALICE_EMAIL, "Alice")
    val repo = MalvinasRepository(firestore, auth)

    val vpn =
      VpnAccount(
        userId = uid,
        profileType = ProfileType.COMMON.id,
        clientName = "Sofía Ruiz",
        clientPhone = "+5491155559988",
        vpnUsername = "sofia_vpn",
        planName = "VPN Mensual",
        amountToCharge = 9000.0,
        expirationMillis = 1794870000000L,
        isPaid = false,
        notifyOneDayBefore = true,
      )
    val vpnId = withTimeout(DEFAULT_TIMEOUT_MS) { repo.addVpnAccount(vpn).getOrThrow() }
    val list =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeVpnAccounts(uid).first { items -> items.any { it.id == vpnId } }
      }
    val saved = list.first { it.id == vpnId }

    val promiseMillis = 1795100000000L
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updatePaymentPromise(saved, promiseMillis, "Paga el 20").getOrThrow()
    }
    val updatedWithPromise =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeVpnAccounts(uid).first { items ->
          items.firstOrNull { it.id == vpnId }?.paymentPromiseMillis == promiseMillis
        }.first { it.id == vpnId }
      }
    assertEquals("Paga el 20", updatedWithPromise.paymentPromiseNote)
    assertEquals(false, updatedWithPromise.isPaid)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.renewVpnAccount(
        vpn = updatedWithPromise,
        newExpirationMillis = 1797462000000L,
        monthKey = "2026-11",
        recordIncome = true,
      ).getOrThrow()
    }
    val afterRenew =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeVpnAccounts(uid).first { items ->
          items.firstOrNull { it.id == vpnId }?.expirationMillis == 1797462000000L
        }.first { it.id == vpnId }
      }
    assertEquals(0L, afterRenew.paymentPromiseMillis)
    assertEquals("", afterRenew.paymentPromiseNote)
  }

  @Test
  fun crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInAuthorizedTestUser(ALICE_EMAIL, "Alice")
    val aliceRepo = MalvinasRepository(firestore, auth)
    val vpnId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .addVpnAccount(
            VpnAccount(
              userId = aliceUid,
              profileType = ProfileType.COMMON.id,
              clientName = "Usuario Privado",
              vpnUsername = "priv_vpn01",
              planName = "VPN Mensual",
              amountToCharge = 10000.0,
              expirationMillis = 1794870000000L,
            )
          )
          .getOrThrow()
      }

    signInAuthorizedTestUser(BOB_EMAIL, "Bob")
    val bobRepo = MalvinasRepository(firestore, auth)
    val exception =
      try {
        withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeVpnAccounts(aliceUid).first() }
        null
      } catch (e: Exception) {
        e
      }
    assertNotNull("Expected PERMISSION_DENIED for cross-user read", exception)
    val firestoreException =
      checkNotNull(exception!!.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $exception (vpnId=$vpnId)"
      }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  @Test
  fun unauthenticatedObserver_failsWithPermissionDenied() = runBlocking {
    val uid = signInAuthorizedTestUser(ALICE_EMAIL, "Alice")
    auth.signOut()
    val repo = MalvinasRepository(firestore, auth)

    val exception =
      try {
        withTimeout(FLOW_TIMEOUT_MS) { repo.observeTransactions(uid).first() }
        null
      } catch (e: Exception) {
        e
      }
    assertNotNull("Expected PERMISSION_DENIED for unauthenticated read", exception)
    val firestoreException =
      checkNotNull(exception!!.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $exception"
      }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  @Test
  fun userSettings_createUpdateAndObserve_succeedsForOwner() = runBlocking {
    val uid = signInAuthorizedTestUser(ALICE_EMAIL, "Alice")
    val repo = MalvinasRepository(firestore, auth)

    val initial =
      UserSettings(
        userId = uid,
        businessName = "Malvinas VIP",
        whatsappClientTemplate = "Hola {nombre}, recordatorio {monto}",
        whatsappVpnTemplate = "Hola {nombre}, VPN @{usuario_vpn}",
        defaultCountryCode = "549",
        reminderHour = 10L,
        defaultVpnDays = 30L,
        autoRecordIncomeOnPay = false,
      )
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.saveUserSettings(initial).getOrThrow() }

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserSettings(uid).first { it.businessName == "Malvinas VIP" }
      }
    assertEquals("549", observed.defaultCountryCode)
    assertEquals(10L, observed.reminderHour)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveUserSettings(observed.copy(reminderHour = 18L, defaultVpnDays = 15L)).getOrThrow()
    }
    val updated =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserSettings(uid).first { it.reminderHour == 18L }
      }
    assertEquals(15L, updated.defaultVpnDays)
  }

  @Test
  fun syncCloudNow_and_fetchRemoteAppConfig_succeedsForOwner() = runBlocking {
    val uid = signInAuthorizedTestUser(ALICE_EMAIL, "Alice")
    val repo = MalvinasRepository(firestore, auth)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveUserSettings(UserSettings(userId = uid)).getOrThrow()
    }
    val report = withTimeout(DEFAULT_TIMEOUT_MS) { repo.syncCloudNow(uid).getOrThrow() }
    assertTrue(report.hasSettingsDoc)
    assertTrue(report.isFromServer)

    val remoteConfigResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.fetchRemoteAppConfig() }
    assertTrue(remoteConfigResult.isSuccess)
  }

  @Test
  fun resellers_crudAndTotalCalculation_succeedsForOwner() = runBlocking {
    val uid = signInAuthorizedTestUser(ALICE_EMAIL, "Alice")
    val repo = MalvinasRepository(firestore, auth)

    val initialExpiration = 1794870000000L
    val reseller =
      Reseller(
        userId = uid,
        profileType = ProfileType.COMMON.id,
        name = "Revendedor Sur",
        phone = "+5491144445566",
        creditsCount = 20L,
        pricePerCredit = 1500.0,
        expirationMillis = initialExpiration,
        notes = "Pack inicial",
      )
    val id = withTimeout(DEFAULT_TIMEOUT_MS) { repo.addReseller(reseller).getOrThrow() }
    assertTrue(id.isNotBlank())

    val createdList =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeResellers(uid).first { items -> items.any { it.id == id } }
      }
    val saved = createdList.first { it.id == id }
    assertEquals(20L, saved.creditsCount)
    assertEquals(1500.0, saved.pricePerCredit, 0.01)
    assertEquals(30000.0, saved.totalAmount, 0.01)
    assertEquals(initialExpiration, saved.expirationMillis)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateReseller(saved.copy(creditsCount = 35L, pricePerCredit = 1600.0)).getOrThrow()
    }
    val updatedList =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeResellers(uid).first { items ->
          items.firstOrNull { it.id == id }?.creditsCount == 35L
        }
      }
    val updated = updatedList.first { it.id == id }
    assertEquals(56000.0, updated.totalAmount, 0.01)

    val nextExpiration = 1797462000000L
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.renewResellerCycle(
        reseller = updated,
        newExpirationMillis = nextExpiration,
        monthKey = "2026-11",
        recordIncome = true,
      ).getOrThrow()
    }
    val renewedList =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeResellers(uid).first { items ->
          items.firstOrNull { it.id == id }?.expirationMillis == nextExpiration
        }
      }
    assertEquals(nextExpiration, renewedList.first { it.id == id }.expirationMillis)

    withTimeout(DEFAULT_TIMEOUT_MS) { repo.deleteReseller(id).getOrThrow() }
  }

  @Test
  fun accessControl_adminCreateDisableResetAndBlock_enforcedByFirestoreRules() = runBlocking {
    val invitedEmail = "invitado_control@test.com"
    signInTestUser(AccessControlConfig.ADMIN_EMAIL)
    val accessRepo = AccessControlRepository(firestore, auth)

    // Clean slate if rerun
    accessRepo.deleteAuthorizedUser(invitedEmail)

    val created =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        accessRepo
          .createAuthorizedUser(
            displayName = "Lucas Invitado",
            rawEmail = invitedEmail,
            rawPassword = "ClaveInicial2026",
            notes = "Acceso temporal",
          )
          .getOrThrow()
      }
    assertTrue(PasswordHasher.verifyPassword("ClaveInicial2026", created.passwordSalt, created.passwordHash))
    assertFalse(PasswordHasher.verifyPassword("ClaveIncorrecta", created.passwordSalt, created.passwordHash))

    // Invited user logs in, records self-access, and can write own transactions
    val invitedUid = signInTestUser(invitedEmail)
    val invitedAccessRepo = AccessControlRepository(firestore, auth)
    val selfDoc =
      withTimeout(FLOW_TIMEOUT_MS) {
        invitedAccessRepo.observeAuthorizedUserByEmail(invitedEmail).first { it != null }
      }
    assertNotNull(selfDoc)
    assertTrue(selfDoc!!.enabled)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      invitedAccessRepo.recordSelfAccess(invitedEmail).getOrThrow()
    }

    val invitedMalvinasRepo = MalvinasRepository(firestore, auth)
    val txId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        invitedMalvinasRepo
          .addTransaction(
            FinancialTransaction(
              userId = invitedUid,
              profileType = ProfileType.COMMON.id,
              type = TransactionType.INCOME.id,
              title = "Venta habilitada",
              amount = 5000.0,
              monthKey = "2026-11",
              dateMillis = 1794783000000L,
            )
          )
          .getOrThrow()
      }
    assertTrue(txId.isNotBlank())

    // Admin saves WhatsApp support phone and disables invited user
    signInTestUser(AccessControlConfig.ADMIN_EMAIL)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      accessRepo.saveAdminWhatsAppPhone("+5491155551234").getOrThrow()
      accessRepo.setUserEnabled(invitedEmail, false).getOrThrow()
    }

    // Invited user is now blocked from reading/writing own data AND cannot self-reenable,
    // but CAN read admin's WhatsApp support phone for the "Hablar por WhatsApp" button
    signInTestUser(invitedEmail)
    val supportPhone =
      withTimeout(FLOW_TIMEOUT_MS) {
        invitedAccessRepo.observeAdminWhatsAppPhone().first { it.isNotBlank() }
      }
    assertEquals("+5491155551234", supportPhone)

    val disabledWrite =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        invitedMalvinasRepo.addTransaction(
          FinancialTransaction(
            userId = invitedUid,
            profileType = ProfileType.COMMON.id,
            type = TransactionType.INCOME.id,
            title = "Bloqueado",
            amount = 1000.0,
            monthKey = "2026-11",
            dateMillis = 1794783000000L,
          )
        )
      }
    assertTrue("Disabled user write must fail", disabledWrite.isFailure)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      disabledWrite.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )

    val selfEnableAttempt =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        invitedAccessRepo.setUserEnabled(invitedEmail, true)
      }
    assertTrue("Disabled user cannot re-enable self", selfEnableAttempt.isFailure)

    // Admin resets password and deletes user cleanly
    signInTestUser(AccessControlConfig.ADMIN_EMAIL)
    val nextVersion =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        accessRepo.resetUserPassword(invitedEmail, "NuevaClave999", 1L).getOrThrow()
      }
    assertEquals(2L, nextVersion)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      accessRepo.deleteAuthorizedUser(invitedEmail).getOrThrow()
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice_malvinas@test.com"
    const val BOB_EMAIL = "bob_malvinas@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
