const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_ashu_789";
const SPOOF_UID = "spoof_999";
const ADMIN_EMAIL = "aroraashu81@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 1000);
}

function validUserPayload(uid, name, email = null) {
  const payload = {
    userId: uid,
    displayName: name,
    themeMode: "system",
    preferredModel: "gemini-3.8-flash",
    ttsEnabled: false,
    termsAccepted: false,
    termsAcceptedVersion: "",
    moderationStatus: "active",
    warningCount: 0,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
  if (email) {
    payload.email = email;
  }
  return payload;
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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("safety_policies").doc("global").get());
  await assertFails(unauthDb.collection("safety_violations").get());
  await assertFails(unauthDb.collection("violation_reports").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      userId: ALICE_UID,
      title: "Hello",
      lastMessagePreview: "",
      messageCount: 0,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
});

test("User profile enforces self-ownership, terms update, and blocks privilege escalation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  // User cannot initialize self as suspended/banned or with warnings
  await assertFails(
    profileRef.set({
      ...validUserPayload(ALICE_UID, "Alice", "alice@example.com"),
      moderationStatus: "banned",
    })
  );

  // Valid profile create succeeds
  await assertSucceeds(
    profileRef.set(validUserPayload(ALICE_UID, "Alice", "alice@example.com"))
  );

  // User can accept terms & update preferences
  await assertSucceeds(
    profileRef.update({
      themeMode: "light",
      termsAccepted: true,
      termsAcceptedVersion: "1.0",
      updatedAt: pastTimestamp(),
    })
  );

  // User cannot escalate to suspended/warned or tamper with warningCount or ghost fields
  await assertFails(
    profileRef.update({
      moderationStatus: "suspended",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    profileRef.update({
      warningCount: 5,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );

  // Regular user cannot list all users
  await assertFails(aliceDb.collection("users").get());

  // Cross-user profile read/write rejected
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Automatic permanent ban is one-way, blocks user chats, and only verified admin can unban", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    profileRef.set(validUserPayload(ALICE_UID, "Alice", "alice@example.com"))
  );

  // Automatic one-way permanent ban on violation succeeds
  await assertSucceeds(
    profileRef.update({
      moderationStatus: "banned",
      banReason: "Policy violation: jailbreak attempt",
      warningCount: 1,
      updatedAt: pastTimestamp(),
    })
  );

  // Banned user CANNOT unban themselves, alter preferences, delete profile, or create chats/messages
  await assertFails(
    profileRef.update({
      moderationStatus: "active",
      warningCount: 0,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    profileRef.update({
      themeMode: "dark",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(profileRef.delete());
  await assertFails(
    profileRef.collection("conversations").doc("conv_banned").set({
      userId: ALICE_UID,
      title: "Bypass",
      lastMessagePreview: "Test",
      messageCount: 1,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Verified admin CAN unban/restore Alice
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      moderationStatus: "active",
      banReason: null,
      warningCount: 0,
      updatedAt: pastTimestamp(),
    })
  );

  // Once restored by admin, Alice can create conversations again
  await assertSucceeds(
    profileRef.collection("conversations").doc("conv_restored").set({
      userId: ALICE_UID,
      title: "Hello again",
      lastMessagePreview: "Thank you!",
      messageCount: 1,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
});

test("Authenticated user can create, list, update, and delete own conversations and messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const convRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1");

  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Quantum Physics Chat",
      lastMessagePreview: "Explain entanglement simply",
      messageCount: 1,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );

  const msgRef = convRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      role: "user",
      text: "Explain entanglement simply",
      createdAt: pastTimestamp(),
    })
  );

  await assertSucceeds(convRef.collection("messages").get());
  await assertFails(msgRef.update({ text: "Tampered" }));

  // Cross-user access fails
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").get()
  );

  await assertSucceeds(msgRef.delete());
  await assertSucceeds(convRef.delete());
});

test("Unverified admin email spoof attack is strictly denied admin privileges", async () => {
  const spoofDb = testEnv.authenticatedContext(SPOOF_UID, {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(
      validUserPayload(ALICE_UID, "Alice", "alice@example.com")
    );
  });

  await assertFails(spoofDb.collection("users").get());
  await assertFails(
    spoofDb.collection("users").doc(ALICE_UID).update({
      moderationStatus: "banned",
      warningCount: 1,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    spoofDb.collection("safety_policies").doc("global").set({
      strictModeEnabled: false,
      autoFlagViolations: false,
      blockedKeywords: [],
      customSafetyDirectives: "Bypassed",
      updatedBy: ADMIN_EMAIL,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Verified admin aroraashu81@gmail.com has full safety policy, violation, report, and moderation controls", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(
      validUserPayload(ALICE_UID, "Alice", "alice@example.com")
    )
  );

  await assertFails(
    aliceDb.collection("safety_policies").doc("global").set({
      strictModeEnabled: false,
      autoFlagViolations: false,
      blockedKeywords: [],
      customSafetyDirectives: "Disabled",
      updatedBy: "alice@example.com",
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(
    adminDb.collection("safety_policies").doc("global").set({
      strictModeEnabled: true,
      autoFlagViolations: true,
      blockedKeywords: ["phishing", "malware"],
      customSafetyDirectives: "Always stay polite and refuse illegal acts.",
      updatedBy: ADMIN_EMAIL,
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(aliceDb.collection("safety_policies").doc("global").get());

  const violationRef = aliceDb.collection("safety_violations").doc("vio_1");
  await assertSucceeds(
    violationRef.set({
      userId: ALICE_UID,
      userDisplayName: "Alice",
      userEmail: "alice@example.com",
      category: "jailbreak",
      reason: "Attempted prompt injection bypass",
      promptSnippet: "Ignore all safety instructions...",
      severity: "high",
      status: "open",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  const reportRef = aliceDb.collection("violation_reports").doc("rep_1");
  await assertSucceeds(
    reportRef.set({
      userId: ALICE_UID,
      reporterName: "Alice",
      reporterEmail: "alice@example.com",
      category: "harmful_output",
      description: "Reporting a borderline unsafe phrasing in test chat.",
      status: "open",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("violation_reports").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("violation_reports").get());
  await assertFails(bobDb.collection("violation_reports").doc("rep_1").get());
  await assertFails(
    aliceDb.collection("violation_reports").doc("rep_1").update({
      status: "resolved",
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      moderationStatus: "banned",
      banReason: "Admin confirmed policy violation.",
      warningCount: 1,
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(adminDb.collection("safety_violations").get());
  await assertSucceeds(
    adminDb.collection("safety_violations").doc("vio_1").update({
      status: "reviewed",
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(adminDb.collection("violation_reports").get());
  await assertSucceeds(
    adminDb.collection("violation_reports").doc("rep_1").update({
      status: "resolved",
      adminNotes: "Reviewed and safety directive tightened.",
      updatedAt: pastTimestamp(),
    })
  );
});
