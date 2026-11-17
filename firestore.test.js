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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validWalletPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Dompet Tunai",
    kind: "CASH",
    initialBalance: 500000,
    colorKey: "EMERALD",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validTransactionPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    type: "EXPENSE",
    amount: 35000,
    categoryId: "makan",
    walletId: "wallet_tunai",
    note: "Kopi susu & roti pagi",
    dateEpochMillis: Date.now() - 1000,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validBudgetPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    categoryId: "makan",
    monthKey: "2026-11",
    limitAmount: 1500000,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

// --- 1. UNAUTHENTICATED ACCESS DENIAL ---

test("Unauthenticated user cannot read or write wallets, transactions, or budgets", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/wallets`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/transactions`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/budgets`).get());

  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/wallets`)
      .doc("w1")
      .set(validWalletPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/transactions`)
      .doc("t1")
      .set(validTransactionPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/budgets`)
      .doc("2026-11_makan")
      .set(validBudgetPayload(ALICE_UID))
  );
});

// --- 2. WALLETS: OWNER CRUD, LIST & NEGATIVE BOUNDS ---

test("Owner can create, list, partially update, and delete wallets", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const walletRef = aliceDb.collection(`users/${ALICE_UID}/wallets`).doc("wallet_tunai");

  await assertSucceeds(walletRef.set(validWalletPayload(ALICE_UID)));

  // Empty or populated subcollection list succeeds without existing() crash
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/wallets`).get());

  // Partial update matching WalletRepository
  await assertSucceeds(
    walletRef.update({
      name: "Dompet Harian",
      kind: "EWALLET",
      initialBalance: 750000,
      colorKey: "AMBER",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt mutation fails
  await assertFails(
    walletRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Ghost field fails
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/wallets`)
      .doc("wallet_ghost")
      .set(validWalletPayload(ALICE_UID, { id: "wallet_ghost" }))
  );

  // Invalid kind enum fails
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/wallets`)
      .doc("wallet_bad_kind")
      .set(validWalletPayload(ALICE_UID, { kind: "CRYPTO" }))
  );

  await assertSucceeds(walletRef.delete());
});

// --- 3. TRANSACTIONS: OWNER CRUD, QUERIES & VALIDATION ---

test("Owner can create, list, update, and delete transactions; rejects invalid bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection(`users/${ALICE_UID}/transactions`).doc("tx_001");

  await assertSucceeds(txRef.set(validTransactionPayload(ALICE_UID)));

  // Mirror Kotlin repository query
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/transactions`)
      .orderBy("dateEpochMillis", "desc")
      .get()
  );

  // Valid partial update
  await assertSucceeds(
    txRef.update({
      type: "INCOME",
      amount: 50000,
      categoryId: "bonus",
      walletId: "wallet_tunai",
      note: "Cashback kopi",
      dateEpochMillis: Date.now() - 500,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject zero or negative amount
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/transactions`)
      .doc("tx_zero")
      .set(validTransactionPayload(ALICE_UID, { amount: 0 }))
  );

  // Reject tampered future timestamp
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/transactions`)
      .doc("tx_future")
      .set(
        validTransactionPayload(ALICE_UID, {
          createdAt: new Date(Date.now() + 60000),
          updatedAt: new Date(Date.now() + 60000),
        })
      )
  );

  // Reject oversized note (>120 chars)
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/transactions`)
      .doc("tx_long_note")
      .set(validTransactionPayload(ALICE_UID, { note: "x".repeat(121) }))
  );

  // Reject @DocumentId field leak ("id")
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/transactions`)
      .doc("tx_with_id")
      .set(validTransactionPayload(ALICE_UID, { id: "tx_with_id" }))
  );

  await assertSucceeds(txRef.delete());
});

// --- 4. BUDGETS: OWNER CRUD & MONTH FORMAT ---

test("Owner can create, update, list, and delete monthly budgets", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const budgetRef = aliceDb.collection(`users/${ALICE_UID}/budgets`).doc("2026-11_makan");

  await assertSucceeds(budgetRef.set(validBudgetPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/budgets`).get());

  await assertSucceeds(
    budgetRef.update({
      limitAmount: 2000000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject changing immutable monthKey on update
  await assertFails(
    budgetRef.update({
      monthKey: "2026-12",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid monthKey format
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/budgets`)
      .doc("bad_month")
      .set(validBudgetPayload(ALICE_UID, { monthKey: "2026-13" }))
  );

  await assertSucceeds(budgetRef.delete());
});

// --- 5. CROSS-USER ISOLATION ---

test("Authenticated user cannot read, list, write, or delete another user's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection(`users/${BOB_UID}/wallets`).doc("bob_w1").set(validWalletPayload(BOB_UID));
    await db.collection(`users/${BOB_UID}/transactions`).doc("bob_t1").set(validTransactionPayload(BOB_UID));
    await db.collection(`users/${BOB_UID}/budgets`).doc("2026-11_makan").set(validBudgetPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(aliceDb.collection(`users/${BOB_UID}/wallets`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/wallets`).doc("bob_w1").get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/wallets`).doc("bob_w1").update({ name: "Hacked" })
  );
  await assertFails(aliceDb.collection(`users/${BOB_UID}/wallets`).doc("bob_w1").delete());

  await assertFails(aliceDb.collection(`users/${BOB_UID}/transactions`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/transactions`).doc("bob_t1").get());
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/transactions`)
      .doc("spoof")
      .set(validTransactionPayload(BOB_UID))
  );

  await assertFails(aliceDb.collection(`users/${BOB_UID}/budgets`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/budgets`).doc("2026-11_makan").delete());
});
