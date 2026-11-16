const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const COLLECTION_SCOOTERS = "E-Scooters";
const COLLECTION_BICYCLES = "Bicycles";
const COLLECTION_ESKATES = "E-Skates";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validVehiclePayload(ownerId, isPublic = false, overrides = {}) {
  const now = new Date();
  return {
    ownerId,
    isPublic,
    name: "Kugoo M4 Pro",
    brand: "Kugoo Kirin",
    vehicleClass: "SCOOTER",
    passportCode: "SWX-2026-KM4P-01",
    releaseYear: 2025,
    modelAssetPath: "models/kugoo_m4_pro.glb",
    motorWatts: 500,
    peakWatts: 1000,
    batteryVoltage: 48,
    batteryCapacityAh: 18.0,
    topSpeedKmh: 45.0,
    rangeKm: 65.0,
    weightKg: 22.5,
    tireSize: "10\" Pneumatic Off-Road",
    frameMaterial: "6061-T6 Aluminum Alloy",
    ipRating: "IPX4",
    suspension: "Dual Spring Hydraulic",
    brakeSystem: "Dual Disc + E-ABS",
    hasNfcImmobilizer: false,
    hasGpsTracker: false,
    hasAlarmSystem: true,
    lockMethod: "KEY_IGNITION",
    controllerVersion: "SWX-KM4-FOC v4.2.1",
    bmsVersion: "JBD-48S13-v2.9",
    protocol: "UART-2.0",
    regenLevels: 3,
    batteryHotspotX: 0.0,
    batteryHotspotY: 0.22,
    batteryHotspotZ: 0.02,
    motorHotspotX: -0.58,
    motorHotspotY: 0.19,
    motorHotspotZ: 0.0,
    controllerHotspotX: 0.44,
    controllerHotspotY: 0.86,
    controllerHotspotZ: 0.0,
    "haqqında": "Yüksək torklu şəhər və yolsuzluq elektrik skuteri.",
    "skutur 3D modeli": "SWX3D:e30=",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
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

test("Login-free user can query and get public rideables across category collections", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection(COLLECTION_SCOOTERS).doc("kugoo_m4_pro").set(
      validVehiclePayload(ALICE_UID, true)
    );
    await context.firestore().collection(COLLECTION_ESKATES).doc("boosted_stealth").set(
      validVehiclePayload(ALICE_UID, true, { name: "Boosted Stealth", vehicleClass: "E_SKATE" })
    );
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection(COLLECTION_SCOOTERS).where("isPublic", "==", true).get());
  await assertSucceeds(unauthDb.collection(COLLECTION_ESKATES).where("isPublic", "==", true).get());
  await assertSucceeds(unauthDb.collection(COLLECTION_SCOOTERS).doc("kugoo_m4_pro").get());
});

test("Unauthenticated user cannot run unfiltered list, read private vehicles, or write", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection(COLLECTION_SCOOTERS).doc("alice_private").set(
      validVehiclePayload(ALICE_UID, false)
    );
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(COLLECTION_SCOOTERS).get());
  await assertFails(unauthDb.collection(COLLECTION_SCOOTERS).doc("alice_private").get());
  await assertFails(unauthDb.collection(COLLECTION_SCOOTERS).doc("unauth_write").set(validVehiclePayload(ALICE_UID, true)));
  await assertFails(unauthDb.collection(COLLECTION_SCOOTERS).doc("alice_private").delete());
});

test("Authenticated user can create mechanical Bicycles & electric rideables with haqqında & skutur 3D modeli", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection(COLLECTION_BICYCLES).doc("trek_domane_sl7").set(
      validVehiclePayload(ALICE_UID, true, {
        name: "Trek Domane SL 7",
        vehicleClass: "BICYCLE",
        motorWatts: 0,
        peakWatts: 0,
        batteryVoltage: 0,
        batteryCapacityAh: 0.0,
        rangeKm: 0.0,
        regenLevels: 0,
      })
    )
  );
  await assertSucceeds(
    aliceDb.collection(COLLECTION_BICYCLES).where("ownerId", "==", ALICE_UID).get()
  );
  // Unknown collection outside the 7 category collections is denied
  await assertFails(
    aliceDb.collection("UnknownVehicles").doc("bad_col_doc").set(validVehiclePayload(ALICE_UID, true))
  );
});

test("Cross-user access: Bob cannot read Alice's private vehicle or modify/delete Alice's vehicle", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection(COLLECTION_SCOOTERS).doc("alice_private").set(
      validVehiclePayload(ALICE_UID, false)
    );
    await context.firestore().collection(COLLECTION_SCOOTERS).doc("alice_public").set(
      validVehiclePayload(ALICE_UID, true)
    );
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(COLLECTION_SCOOTERS).doc("alice_private").get());
  await assertFails(
    bobDb.collection(COLLECTION_SCOOTERS).doc("alice_public").update({ name: "Hacked", updatedAt: new Date() })
  );
  await assertFails(bobDb.collection(COLLECTION_SCOOTERS).doc("alice_public").delete());
});

test("Shadow update & value poisoning are rejected by schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const shadowPayload = { ...validVehiclePayload(ALICE_UID, false), isAdminOverride: true };
  await assertFails(aliceDb.collection(COLLECTION_SCOOTERS).doc("shadow_doc").set(shadowPayload));

  const badEnumPayload = { ...validVehiclePayload(ALICE_UID, false), vehicleClass: "SPACESHIP" };
  await assertFails(aliceDb.collection(COLLECTION_SCOOTERS).doc("bad_enum_doc").set(badEnumPayload));
});
