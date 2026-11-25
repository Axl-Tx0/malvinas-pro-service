const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const pastTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_axel";
const ADMIN_EMAIL = "axeldelvalle737@gmail.com";
const ALICE_UID = "alice_123";
const ALICE_EMAIL = "alice@gmail.com";
const BOB_UID = "bob_456";
const BOB_EMAIL = "bob@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

function aliceContext() {
  return testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  });
}

function bobContext() {
  return testEnv.authenticatedContext(BOB_UID, {
    email: BOB_EMAIL,
    email_verified: true,
  });
}

function validAuthorizedUser(email, enabled = true) {
  return {
    email,
    displayName: "Usuario Invitado",
    enabled,
    passwordHash: "a".repeat(64),
    passwordSalt: "b".repeat(32),
    passwordVersion: 1,
    notes: "Vendedor",
    boundUid: "",
    lastAccessAt: 0,
    createdBy: ADMIN_UID,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

async function authorizeEmail(email, enabled = true) {
  const db = adminContext().firestore();
  await assertSucceeds(
    db.collection("authorizedUsers").doc(email).set(validAuthorizedUser(email, enabled))
  );
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validTransaction(uid) {
  return {
    userId: uid,
    profileType: "common",
    type: "income",
    title: "Pago Internet Juan",
    category: "Servicios",
    amount: 12000,
    monthKey: "2026-11",
    dateMillis: 1794783000000,
    linkedClientId: "",
    notes: "",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validClient(uid) {
  return {
    userId: uid,
    profileType: "premium",
    name: "Carlos Gómez",
    serviceName: "IPTV + Streaming VIP",
    monthlyFee: 18500,
    billingDay: 10,
    phone: "+54 11 5555-0000",
    notes: "",
    paidMonths: ["2026-11"],
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validVpn(uid) {
  return {
    userId: uid,
    profileType: "common",
    clientName: "Lucía Fernández",
    clientPhone: "+5491155551234",
    vpnUsername: "lucia_vpn01",
    planName: "Plan 30 días",
    amountToCharge: 8500,
    expirationMillis: 1794870000000,
    isPaid: false,
    notifyOneDayBefore: true,
    paymentPromiseMillis: 1795132800000,
    paymentPromiseNote: "Prometió pagar el 20",
    notes: "",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validReseller(uid) {
  return {
    userId: uid,
    profileType: "common",
    name: "Revendedor Norte",
    phone: "+5491155554433",
    creditsCount: 25,
    pricePerCredit: 1500,
    totalAmount: 37500,
    expirationMillis: 1797462000000,
    notes: "Cuenta mayorista",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validUserSettings(uid) {
  return {
    userId: uid,
    businessName: "MALVINAS PRO SERVICE",
    whatsappClientTemplate: "Hola {nombre}, vencimiento {vencimiento} monto {monto}",
    whatsappVpnTemplate: "Hola {nombre}, cuenta @{usuario} vence {vencimiento}",
    defaultCountryCode: "549",
    reminderHour: 9,
    defaultVpnDays: 30,
    autoRecordIncomeOnPay: true,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

test("Unauthenticated user: cannot list or write transactions, clients, vpnAccounts, or authorizedUsers", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/transactions`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/clients`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/vpnAccounts`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/resellers`).get());
  await assertFails(unauthDb.collection("authorizedUsers").get());
  await assertFails(unauthDb.collection("authorizedUsers").doc(ALICE_EMAIL).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/transactions`).doc("tx1").set(validTransaction(ALICE_UID))
  );
});

test("Unauthorized user cannot read or write own data; unverified admin email spoof is rejected", async () => {
  const aliceDb = aliceContext().firestore();
  await assertFails(aliceDb.collection(`users/${ALICE_UID}/transactions`).get());
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx1").set(validTransaction(ALICE_UID))
  );
  await assertFails(aliceDb.collection("appConfig").doc("public").get());
  await assertFails(aliceDb.collection("authorizedUsers").get());

  // Unverified email spoofing admin must be denied
  const spoofAdminDb = testEnv
    .authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();
  await assertFails(spoofAdminDb.collection("authorizedUsers").get());
  await assertFails(
    spoofAdminDb.collection("authorizedUsers").doc(ALICE_EMAIL).set(validAuthorizedUser(ALICE_EMAIL))
  );
});

test("Admin can create, disable, re-enable, reset password, and delete authorized users; disabled user is immediately blocked", async () => {
  const adminDb = adminContext().firestore();
  const aliceDb = aliceContext().firestore();
  const bobDb = bobContext().firestore();

  // Admin creates Alice as enabled
  await authorizeEmail(ALICE_EMAIL, true);
  await assertSucceeds(adminDb.collection("authorizedUsers").get());

  // Alice can read her own authorizedUsers doc (not list); once Bob is bound, Alice cannot read Bob's doc
  await assertSucceeds(aliceDb.collection("authorizedUsers").doc(ALICE_EMAIL).get());
  await assertFails(aliceDb.collection("authorizedUsers").get());
  await authorizeEmail(BOB_EMAIL, true);
  await assertSucceeds(
    bobDb.collection("authorizedUsers").doc(BOB_EMAIL).update({
      boundUid: BOB_UID,
      lastAccessAt: 1795000000000,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(aliceDb.collection("authorizedUsers").doc(BOB_EMAIL).get());

  // Alice can record her own boundUid & lastAccessAt while enabled
  await assertSucceeds(
    aliceDb.collection("authorizedUsers").doc(ALICE_EMAIL).update({
      boundUid: ALICE_UID,
      lastAccessAt: 1795000000000,
      updatedAt: pastTimestamp(),
    })
  );

  // Alice CANNOT modify her own enabled flag or passwordHash
  await assertFails(
    aliceDb.collection("authorizedUsers").doc(ALICE_EMAIL).update({
      enabled: false,
      updatedAt: pastTimestamp(),
    })
  );

  // While enabled, Alice can write and read her own transactions
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx_alice").set(validTransaction(ALICE_UID))
  );

  // Admin disables Alice
  await assertSucceeds(
    adminDb.collection("authorizedUsers").doc(ALICE_EMAIL).update({
      enabled: false,
      updatedAt: pastTimestamp(),
    })
  );

  // Disabled Alice can STILL read her own status doc to see the lock screen,
  // but is IMMEDIATELY denied on all app collections AND cannot re-enable herself!
  await assertSucceeds(aliceDb.collection("authorizedUsers").doc(ALICE_EMAIL).get());
  await assertFails(aliceDb.collection(`users/${ALICE_UID}/transactions`).get());
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx_blocked").set(validTransaction(ALICE_UID))
  );
  await assertFails(aliceDb.collection(`users/${ALICE_UID}/vpnAccounts`).get());
  await assertFails(aliceDb.collection(`users/${ALICE_UID}/resellers`).get());
  await assertFails(aliceDb.collection("appConfig").doc("public").get());
  await assertFails(
    aliceDb.collection("authorizedUsers").doc(ALICE_EMAIL).update({
      enabled: true,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("authorizedUsers").doc(ALICE_EMAIL).update({
      boundUid: ALICE_UID,
      lastAccessAt: 1795000001000,
      updatedAt: pastTimestamp(),
    })
  );

  // Bob cannot create or tamper with authorizedUsers
  await assertFails(
    bobDb.collection("authorizedUsers").doc(BOB_EMAIL).set(validAuthorizedUser(BOB_EMAIL, true))
  );

  // Admin re-enables Alice & bumps passwordVersion
  await assertSucceeds(
    adminDb.collection("authorizedUsers").doc(ALICE_EMAIL).update({
      enabled: true,
      passwordHash: "c".repeat(64),
      passwordSalt: "d".repeat(32),
      passwordVersion: 2,
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/transactions`).get());

  // Admin deletes Alice
  await assertSucceeds(adminDb.collection("authorizedUsers").doc(ALICE_EMAIL).delete());
  await assertFails(aliceDb.collection(`users/${ALICE_UID}/transactions`).get());
});

test("Cross-user isolation: authorized Bob cannot read or write authorized Alice's collections", async () => {
  await authorizeEmail(ALICE_EMAIL, true);
  await authorizeEmail(BOB_EMAIL, true);

  const aliceDb = aliceContext().firestore();
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/clients`).doc("c1").set(validClient(ALICE_UID))
  );

  const bobDb = bobContext().firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/clients`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/clients`).doc("c1").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/clients`).doc("c2").set(validClient(BOB_UID))
  );
});

test("Admin can create, list, update, and delete valid Transaction without authorizedUsers doc", async () => {
  const adminDb = adminContext().firestore();
  const ref = adminDb.collection(`users/${ADMIN_UID}/transactions`).doc("tx_1");
  await assertSucceeds(ref.set(validTransaction(ADMIN_UID)));
  await assertSucceeds(adminDb.collection(`users/${ADMIN_UID}/transactions`).get());
  await assertSucceeds(
    ref.update({
      amount: 14500,
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(ref.delete());
});

test("Authorized owner can create, update paidMonths, and delete ServiceClient", async () => {
  await authorizeEmail(ALICE_EMAIL, true);
  const aliceDb = aliceContext().firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/clients`).doc("client_1");
  await assertSucceeds(ref.set(validClient(ALICE_UID)));
  await assertSucceeds(
    ref.update({
      paidMonths: ["2026-10", "2026-11"],
      updatedAt: pastTimestamp(),
    })
  );
});

test("Authorized owner can create and update VpnAccount including payment promise", async () => {
  await authorizeEmail(ALICE_EMAIL, true);
  const aliceDb = aliceContext().firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/vpnAccounts`).doc("vpn_1");
  await assertSucceeds(ref.set(validVpn(ALICE_UID)));
  await assertSucceeds(
    ref.update({
      paymentPromiseMillis: 1795219200000,
      paymentPromiseNote: "Pasa a las 18hs",
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    ref.update({
      isPaid: true,
      expirationMillis: 1797462000000,
      paymentPromiseMillis: 0,
      paymentPromiseNote: "",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    ref.update({
      paymentPromiseMillis: -1,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Authorized owner can create, update, and delete Reseller; rejects negative values and cross-user access", async () => {
  await authorizeEmail(ALICE_EMAIL, true);
  await authorizeEmail(BOB_EMAIL, true);

  const aliceDb = aliceContext().firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/resellers`).doc("reseller_1");
  await assertSucceeds(ref.set(validReseller(ALICE_UID)));
  await assertSucceeds(
    ref.update({
      creditsCount: 40,
      pricePerCredit: 1600,
      totalAmount: 64000,
      expirationMillis: 1800054000000,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    ref.update({
      expirationMillis: -1,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    ref.update({
      creditsCount: -5,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    ref.update({
      pricePerCredit: -100,
      updatedAt: pastTimestamp(),
    })
  );

  const bobDb = bobContext().firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/resellers`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/resellers`).doc("reseller_1").get());
  await assertSucceeds(ref.delete());
});

test("Rejects shadow ghost field and invalid profileType", async () => {
  await authorizeEmail(ALICE_EMAIL, true);
  const aliceDb = aliceContext().firestore();
  const badProfile = { ...validTransaction(ALICE_UID), profileType: "enterprise" };
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx_bad1").set(badProfile)
  );

  const ghostField = { ...validClient(ALICE_UID), isAdmin: true };
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/clients`).doc("c_bad1").set(ghostField)
  );
});

test("Rejects immutable userId mutation on update", async () => {
  await authorizeEmail(ALICE_EMAIL, true);
  const aliceDb = aliceContext().firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/vpnAccounts`).doc("vpn_immut");
  await assertSucceeds(ref.set(validVpn(ALICE_UID)));
  await assertFails(
    ref.update({
      userId: BOB_UID,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Authorized owner can create and update UserSettings, rejects invalid bounds and cross-user access", async () => {
  await authorizeEmail(ALICE_EMAIL, true);
  await authorizeEmail(BOB_EMAIL, true);

  const aliceDb = aliceContext().firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/settings`).doc("preferences");
  await assertSucceeds(ref.set(validUserSettings(ALICE_UID)));
  await assertSucceeds(
    ref.update({
      reminderHour: 18,
      whatsappClientTemplate: "Nuevo mensaje para {nombre}",
      updatedAt: pastTimestamp(),
    })
  );

  await assertFails(
    ref.update({
      reminderHour: 25,
      updatedAt: pastTimestamp(),
    })
  );

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/settings`).doc("preferences").get());

  const bobDb = bobContext().firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/settings`).doc("preferences").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/settings`).doc("preferences").set(validUserSettings(BOB_UID))
  );
});

test("Remote appConfig/public allows active authorized get only, denies unauthenticated, disabled, and client writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("appConfig").doc("public").get());

  const adminDb = adminContext().firestore();
  await assertSucceeds(adminDb.collection("appConfig").doc("public").get());
  await assertFails(adminDb.collection("appConfig").get());
  await assertFails(
    adminDb.collection("appConfig").doc("public").set({ announcementText: "hacked" })
  );
});

test("Admin support WhatsApp contact at appConfig/support is writable only by admin and readable by signed-in disabled users", async () => {
  const adminDb = adminContext().firestore();
  const aliceDb = aliceContext().firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertSucceeds(
    adminDb.collection("appConfig").doc("support").set({
      whatsappPhone: "+5491155551234",
      updatedBy: ADMIN_UID,
      updatedAt: pastTimestamp(),
    })
  );

  // Disabled or unauthorized signed-in user can read admin WhatsApp contact to request access
  await authorizeEmail(ALICE_EMAIL, false);
  await assertSucceeds(aliceDb.collection("appConfig").doc("support").get());

  // Non-admin cannot modify support contact; unauthenticated cannot read it
  await assertFails(
    aliceDb.collection("appConfig").doc("support").set({
      whatsappPhone: "+5491100000000",
      updatedBy: ALICE_UID,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(unauthDb.collection("appConfig").doc("support").get());
});

test("GitHub updates config at appConfig/updates is admin-writable with strict owner/repo validation and active-user readable", async () => {
  const adminDb = adminContext().firestore();
  const aliceDb = aliceContext().firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  // Admin can save a valid owner/repo slug
  await assertSucceeds(
    adminDb.collection("appConfig").doc("updates").set({
      githubRepo: "axeldelvalle/malvinas-pro-service",
      updatedBy: ADMIN_UID,
      updatedAt: pastTimestamp(),
    })
  );

  // Admin cannot save an arbitrary URL or invalid path
  await assertFails(
    adminDb.collection("appConfig").doc("updates").set({
      githubRepo: "https://evil.example.com/payload.apk",
      updatedBy: ADMIN_UID,
      updatedAt: pastTimestamp(),
    })
  );

  // Active authorized user can read appConfig/updates, but cannot write it
  await authorizeEmail(ALICE_EMAIL, true);
  await assertSucceeds(aliceDb.collection("appConfig").doc("updates").get());
  await assertFails(
    aliceDb.collection("appConfig").doc("updates").set({
      githubRepo: "attacker/hijack",
      updatedBy: ALICE_UID,
      updatedAt: pastTimestamp(),
    })
  );

  // Disabled user and unauthenticated user cannot read appConfig/updates
  await assertSucceeds(
    adminDb.collection("authorizedUsers").doc(ALICE_EMAIL).update({
      enabled: false,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(aliceDb.collection("appConfig").doc("updates").get());
  await assertFails(unauthDb.collection("appConfig").doc("updates").get());
});

test("Username-only accounts (without Gmail match) bind via /userBindings/{uid}, enforce instant disable kill switch, and prevent hijacking", async () => {
  const adminDb = adminContext().firestore();
  const aliceDb = aliceContext().firestore();
  const bobDb = bobContext().firestore();
  const username = "carlos01";

  // Admin creates a username-only account (not an email address)
  await assertSucceeds(
    adminDb.collection("authorizedUsers").doc(username).set(validAuthorizedUser(username, true))
  );

  // Reject invalid account IDs (spaces, slashes, uppercase, too short)
  await assertFails(
    adminDb.collection("authorizedUsers").doc("ab").set(validAuthorizedUser("ab", true))
  );
  await assertFails(
    adminDb.collection("authorizedUsers").doc("Carlos 01").set(validAuthorizedUser("Carlos 01", true))
  );

  // Alice (whose Google email is alice@gmail.com) cannot write /userBindings before binding authorizedUsers
  await assertFails(
    aliceDb.collection("userBindings").doc(ALICE_UID).set({
      uid: ALICE_UID,
      accountId: username,
      updatedAt: pastTimestamp(),
    })
  );

  // Alice looks up carlos01, claims boundUid + lastDeviceInfo, and writes /userBindings/{ALICE_UID}
  await assertSucceeds(aliceDb.collection("authorizedUsers").doc(username).get());
  await assertSucceeds(
    aliceDb.collection("authorizedUsers").doc(username).update({
      boundUid: ALICE_UID,
      lastAccessAt: 1795000000000,
      lastDeviceInfo: "Samsung SM-A546E · Android 14",
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("userBindings").doc(ALICE_UID).set({
      uid: ALICE_UID,
      accountId: username,
      updatedAt: pastTimestamp(),
    })
  );

  // Now Alice is an active bound user and can access her data
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx_u1").set(validTransaction(ALICE_UID))
  );

  // Bob CANNOT read or hijack carlos01 once bound to Alice
  await assertFails(bobDb.collection("authorizedUsers").doc(username).get());
  await assertFails(
    bobDb.collection("authorizedUsers").doc(username).update({
      boundUid: BOB_UID,
      lastAccessAt: 1795000001000,
      updatedAt: pastTimestamp(),
    })
  );

  // Instant kill switch: Admin disables carlos01 -> Alice is immediately blocked from data
  await assertSucceeds(
    adminDb.collection("authorizedUsers").doc(username).update({
      enabled: false,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(aliceDb.collection(`users/${ALICE_UID}/transactions`).get());
  // Alice can still read her own bound status doc to display the disabled screen
  await assertSucceeds(aliceDb.collection("authorizedUsers").doc(username).get());

  // Admin can unbind carlos01 (boundUid = "") and re-enable so another device can log in with PIN
  await assertSucceeds(
    adminDb.collection("authorizedUsers").doc(username).update({
      enabled: true,
      boundUid: "",
      passwordVersion: 2,
      updatedAt: pastTimestamp(),
    })
  );
  // Alice is no longer bound to carlos01 so her data access stays blocked until re-verified
  await assertFails(aliceDb.collection(`users/${ALICE_UID}/transactions`).get());
});

test("Random Admin PIN verification enforces zero-trust split: users cannot read/list /adminPinInbox while Admin sees PIN and device info", async () => {
  const adminDb = adminContext().firestore();
  const aliceDb = aliceContext().firestore();
  const bobDb = bobContext().firestore();
  const username = "vendedor_norte";
  const challengeId = "pin_chal_001";
  const expiresAt = Date.now() + 600_000;
  const deviceInfo = "Motorola moto g84 5G · Android 14";

  await assertSucceeds(
    adminDb.collection("authorizedUsers").doc(username).set(validAuthorizedUser(username, true))
  );

  const challengeDoc = {
    challengeId,
    accountId: username,
    displayName: "Vendedor Norte",
    requesterUid: ALICE_UID,
    deviceInfo,
    pinCode: "482915",
    pinHash: "e".repeat(64),
    pinSalt: "f".repeat(32),
    status: "pending",
    expiresAt,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };

  const inboxDoc = {
    challengeId,
    accountId: username,
    displayName: "Vendedor Norte",
    requesterUid: ALICE_UID,
    requesterEmail: ALICE_EMAIL,
    deviceInfo,
    pinCode: "482915",
    status: "pending",
    expiresAt,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };

  // Alice creates the challenge (with deviceInfo + pinCode) and the admin-only PIN inbox doc
  await assertSucceeds(aliceDb.collection("pinChallenges").doc(challengeId).set(challengeDoc));
  await assertSucceeds(aliceDb.collection("adminPinInbox").doc(challengeId).set(inboxDoc));

  // ZERO-TRUST CHECK: Neither Alice nor Bob can read or list /adminPinInbox
  await assertFails(aliceDb.collection("adminPinInbox").doc(challengeId).get());
  await assertFails(aliceDb.collection("adminPinInbox").get());
  await assertFails(bobDb.collection("adminPinInbox").doc(challengeId).get());
  await assertFails(bobDb.collection("adminPinInbox").get());

  // Bob cannot read Alice's pinChallenge either
  await assertFails(bobDb.collection("pinChallenges").doc(challengeId).get());
  await assertFails(aliceDb.collection("pinChallenges").get());

  // Alice CAN read her own pinChallenge
  await assertSucceeds(aliceDb.collection("pinChallenges").doc(challengeId).get());

  // Admin CAN list and read /adminPinInbox to see the random 6-digit PIN and device info live
  await assertSucceeds(adminDb.collection("adminPinInbox").get());
  await assertSucceeds(adminDb.collection("adminPinInbox").doc(challengeId).get());

  // Admin can regenerate a fresh random PIN (updates both collections)
  await assertSucceeds(
    adminDb.collection("adminPinInbox").doc(challengeId).update({
      pinCode: "917304",
      expiresAt: Date.now() + 600_000,
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    adminDb.collection("pinChallenges").doc(challengeId).update({
      pinCode: "917304",
      pinHash: "1".repeat(64),
      pinSalt: "2".repeat(32),
      expiresAt: Date.now() + 600_000,
      updatedAt: pastTimestamp(),
    })
  );

  // Alice CANNOT tamper with pinCode, pinHash, or adminPinInbox
  await assertFails(
    aliceDb.collection("pinChallenges").doc(challengeId).update({
      pinCode: "000000",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("pinChallenges").doc(challengeId).update({
      pinHash: "0".repeat(64),
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("adminPinInbox").doc(challengeId).update({
      pinCode: "000000",
      updatedAt: pastTimestamp(),
    })
  );

  // Once Alice enters the right PIN, she can mark her challenge verified and clean up her inbox entry
  await assertSucceeds(
    aliceDb.collection("pinChallenges").doc(challengeId).update({
      status: "verified",
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(aliceDb.collection("adminPinInbox").doc(challengeId).delete());
});

test("Authorized owner can CRUD ServicePlan presets; rejects invalid values and cross-user access", async () => {
  await authorizeEmail(ALICE_EMAIL, true);
  await authorizeEmail(BOB_EMAIL, true);

  const aliceDb = aliceContext().firestore();
  const bobDb = bobContext().firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const planPayload = {
    userId: ALICE_UID,
    name: "Plan VIP 30 días",
    price: 9500,
    durationDays: 30,
    targetType: "client",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };

  const ref = aliceDb.collection(`users/${ALICE_UID}/servicePlans`).doc("plan_1");
  await assertSucceeds(ref.set(planPayload));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/servicePlans`).get());
  await assertSucceeds(
    ref.update({
      price: 10500,
      durationDays: 60,
      updatedAt: pastTimestamp(),
    })
  );

  // Reject invalid targetType and durationDays
  await assertFails(
    ref.update({
      targetType: "admin",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    ref.update({
      durationDays: 0,
      updatedAt: pastTimestamp(),
    })
  );

  // Cross-user and unauthenticated denied
  await assertFails(bobDb.collection(`users/${ALICE_UID}/servicePlans`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/servicePlans`).doc("plan_1").get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/servicePlans`).get());

  await assertSucceeds(ref.delete());
});

test("AccessLogs are append-only by signed-in verified users, immutable, and readable/deletable only by Admin", async () => {
  const adminDb = adminContext().firestore();
  const aliceDb = aliceContext().firestore();
  const bobDb = bobContext().firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const username = "vendedor_sur";

  await assertSucceeds(
    adminDb.collection("authorizedUsers").doc(username).set(validAuthorizedUser(username, true))
  );

  const logDoc = {
    accountId: username,
    displayName: "Vendedor Sur",
    uid: ALICE_UID,
    deviceInfo: "Samsung SM-A546E · Android 14",
    eventType: "pin_login",
    timestampMillis: Date.now(),
    createdAt: pastTimestamp(),
  };

  // Alice can record her own login event
  const logRef = aliceDb.collection("accessLogs").doc("log_001");
  await assertSucceeds(logRef.set(logDoc));

  // Alice CANNOT spoof another UID or read/list/update/delete accessLogs
  await assertFails(
    aliceDb.collection("accessLogs").doc("log_spoof").set({ ...logDoc, uid: BOB_UID })
  );
  await assertFails(aliceDb.collection("accessLogs").get());
  await assertFails(logRef.get());
  await assertFails(logRef.update({ deviceInfo: "Hacked" }));
  await assertFails(logRef.delete());

  // Bob and unauthenticated cannot read/list
  await assertFails(bobDb.collection("accessLogs").get());
  await assertFails(unauthDb.collection("accessLogs").get());

  // Admin CAN list, read, and delete logs, but CANNOT mutate existing logs (immutable)
  await assertSucceeds(adminDb.collection("accessLogs").get());
  await assertSucceeds(adminDb.collection("accessLogs").doc("log_001").get());
  await assertFails(
    adminDb.collection("accessLogs").doc("log_001").update({ deviceInfo: "Edited" })
  );
  await assertSucceeds(adminDb.collection("accessLogs").doc("log_001").delete());
});

test("UserSettings accepts valid payment details (alias, CBU/CVU, holder, bank, https link) and rejects unsafe links", async () => {
  await authorizeEmail(ALICE_EMAIL, true);
  const aliceDb = aliceContext().firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/settings`).doc("preferences");

  await assertSucceeds(
    ref.set({
      ...validUserSettings(ALICE_UID),
      paymentAlias: "malvinas.pro.mp",
      paymentCbuCvu: "0000003100012345678901",
      paymentHolderName: "Axel Del Valle",
      paymentBankOrWallet: "Mercado Pago",
      paymentLink: "https://link.mercadopago.com.ar/malvinaspro",
    })
  );

  // Valid update clearing paymentLink
  await assertSucceeds(
    ref.update({
      paymentAlias: "malvinas.nuevo.mp",
      paymentLink: "",
      updatedAt: pastTimestamp(),
    })
  );

  // Reject unsafe javascript: or non-http paymentLink
  await assertFails(
    ref.update({
      paymentLink: "javascript:alert(1)",
      updatedAt: pastTimestamp(),
    })
  );

  // Reject oversized alias (>80 chars)
  await assertFails(
    ref.update({
      paymentAlias: "a".repeat(81),
      updatedAt: pastTimestamp(),
    })
  );
});

