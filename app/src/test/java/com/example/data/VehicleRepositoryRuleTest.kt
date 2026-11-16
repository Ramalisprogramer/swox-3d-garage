package com.example.data

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.GarageDatabase
import com.example.model.HotspotPoint
import com.example.model.LockMethod
import com.example.model.VehicleClass
import com.example.model.VehicleHotspots
import com.example.model.VehicleProfile
import com.example.model3d.Decoded3DModel
import com.example.model3d.Encoded3DModelCodec
import com.example.model3d.buildVehicle3DWireframe
import com.example.viewmodel.GarageViewModel
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class VehicleRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var roomDb: GarageDatabase
    private lateinit var repository: VehicleRepository

    @Before
    fun setUpRepository() {
        roomDb = Room.inMemoryDatabaseBuilder(appContext, GarageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VehicleRepository(
            context = appContext,
            vehicleDao = roomDb.vehicleDao(),
            db = firestore,
            auth = auth
        )
    }

    @After
    fun tearDownRoom() {
        roomDb.close()
    }

    @Test
    fun ensureLocalSeeded_loads1000ItemsPerCategoryOfflineIntoRoom() = runBlocking {
        repository.ensureLocalSeeded()

        val counts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCategoryCounts().first { map ->
                VehicleClass.entries.all { (map[it] ?: 0) == VehicleRepository.MAX_ITEMS_PER_CATEGORY }
            }
        }
        VehicleClass.entries.forEach { cls ->
            assertEquals(1000, counts[cls])
        }

        val summaries = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLocalVehicleSummaries(query = "", category = null, limit = 7000).first()
        }
        assertEquals(7000, summaries.size)
        assertTrue(summaries.any { it.id == "kugoo_m4_pro" })
        assertTrue(summaries.any { it.id == "segway_gt2_superscooter" })
        assertFalse(
            "Velostrim must be excluded",
            summaries.any { it.id.contains("velostrim", ignoreCase = true) || it.name.contains("velostrim", ignoreCase = true) }
        )
        assertFalse(
            "Kugoo M2 must be excluded",
            summaries.any { it.id.contains("kugoo_m2", ignoreCase = true) || it.name.contains("kugoo m2", ignoreCase = true) }
        )
    }

    @Test
    fun garageViewModel_opensDirectlyIntoCatalogWithCategoryCountsAndTransitionsToInspector() = runBlocking {
        val store = ViewModelStore()
        try {
            val viewModel = ViewModelProvider(
                store,
                GarageViewModel.Factory(repository, UserPreferencesRepository(appContext))
            )[GarageViewModel::class.java]

            val loadedState = withTimeout(FLOW_TIMEOUT_MS) {
                viewModel.uiState.first {
                    !it.isLoading &&
                        it.filteredVehicles.isNotEmpty() &&
                        it.totalCachedCount == 7000
                }
            }
            assertFalse(loadedState.isInspectingVehicle)
            assertEquals(1000, loadedState.categoryCounts[VehicleClass.SCOOTER])
            assertEquals(1000, loadedState.categoryCounts[VehicleClass.E_BIKE])

            viewModel.selectVehicle("kugoo_m4_pro")
            val inspectedState = withTimeout(FLOW_TIMEOUT_MS) {
                viewModel.uiState.first { it.isInspectingVehicle && it.selectedVehicle?.id == "kugoo_m4_pro" }
            }
            assertTrue(inspectedState.isInspectingVehicle)

            viewModel.closeVehicleInspector()
            val returnedState = withTimeout(FLOW_TIMEOUT_MS) {
                viewModel.uiState.first { !it.isInspectingVehicle }
            }
            assertFalse(returnedState.isInspectingVehicle)
        } finally {
            store.clear()
        }
    }

    @Test
    fun observeCloudCatalogSync_loginFreePublicRead_syncsCategoryCollectionsToRoom() = runBlocking {
        val encodedMesh = Encoded3DModelCodec.encodeWireframeMesh(
            buildVehicle3DWireframe(VehicleClass.ONEWHEEL)
        )
        // Seed a public rideable document under its category collection (`Onewheels`), then sign out
        signInTestUser(ALICE_EMAIL)
        val docId = "public_ev_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val vehicle = sampleVehicle(
            id = docId,
            name = "Swox Cloud Pulse",
            vehicleClass = VehicleClass.ONEWHEEL,
            description = "Şəhər və yolsuzluq üçün yüksək torklu ağıllı monotəkər.",
            encoded3dModel = encodedMesh
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveVehicleToCloud(vehicle = vehicle, isPublic = true).getOrThrow()
        }
        auth.signOut()

        // Clear local Room cache so we prove sync fetches directly from `Onewheels` without login
        roomDb.clearAllTables()

        val status = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCloudCatalogSync().first { it == CatalogSyncStatus.CLOUD_SYNCED }
        }
        assertEquals(CatalogSyncStatus.CLOUD_SYNCED, status)

        val syncedVehicle = repository.getCachedVehicleById(docId)
        assertNotNull(syncedVehicle)
        assertEquals("Swox Cloud Pulse", syncedVehicle?.name)
        assertEquals(
            "Şəhər və yolsuzluq üçün yüksək torklu ağıllı monotəkər.",
            syncedVehicle?.description
        )
        assertEquals(encodedMesh, syncedVehicle?.encoded3dModel)

        val decoded = Encoded3DModelCodec.decode(checkNotNull(syncedVehicle))
        assertTrue(decoded is Decoded3DModel.ProceduralMesh)
        assertTrue((decoded as Decoded3DModel.ProceduralMesh).mesh.segments.isNotEmpty())

        // Single-doc login-free public fetch from category collection also succeeds
        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchVehicleById(docId, VehicleClass.ONEWHEEL).getOrThrow()
        }
        assertEquals("Swox Cloud Pulse", fetched?.name)
    }

    @Test
    fun saveVehicleToCloud_crossUserOverwrite_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val docId = "owned_ev_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveVehicleToCloud(
                vehicle = sampleVehicle(id = docId, name = "Alice Private EV"),
                isPublic = false
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveVehicleToCloud(
                vehicle = sampleVehicle(id = docId, name = "Bob Hijack EV"),
                isPublic = true
            )
        }
        assertTrue(bobAttempt.isFailure)
        val firestoreException = checkNotNull(bobAttempt.exceptionOrNull()?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got ${bobAttempt.exceptionOrNull()}"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun unauthenticatedPrivateReadAndUnfilteredList_failWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val privateId = "priv_ev_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveVehicleToCloud(
                vehicle = sampleVehicle(id = privateId, name = "Alice Secret Prototype"),
                isPublic = false
            ).getOrThrow()
        }
        auth.signOut()

        var listCaught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection(VehicleRepository.COLLECTION_SCOOTERS)
                    .snapshots()
                    .first()
            }
        } catch (t: Throwable) {
            listCaught = t
        }
        val listFsException = checkNotNull(listCaught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for unfiltered list query, got $listCaught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, listFsException.code)

        var getCaught: Throwable? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection(VehicleRepository.COLLECTION_SCOOTERS)
                    .document(privateId)
                    .get()
                    .await()
            }
        } catch (t: Throwable) {
            getCaught = t
        }
        val getFsException = checkNotNull(getCaught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for unauthenticated private get, got $getCaught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, getFsException.code)
    }

    private fun sampleVehicle(
        id: String,
        name: String,
        vehicleClass: VehicleClass = VehicleClass.SCOOTER,
        description: String = "Swox Labs test rideable specification.",
        encoded3dModel: String = "SWX3D:e30="
    ): VehicleProfile = VehicleProfile(
        id = id,
        name = name,
        brand = "Swox Labs",
        vehicleClass = vehicleClass,
        passportCode = "SWX-TEST-01",
        releaseYear = 2026,
        modelAssetPath = "models/test.glb",
        motorWatts = 1200,
        peakWatts = 2400,
        batteryVoltage = 60,
        batteryCapacityAh = 24.0,
        topSpeedKmh = 65.0,
        rangeKm = 85.0,
        weightKg = 28.5,
        tireSize = "10x3.0 Tubeless",
        frameMaterial = "Forged 6061 Aluminum",
        ipRating = "IPX6",
        suspension = "Hydraulic Inverted",
        brakeSystem = "Dual Hydraulic Disc",
        hasNfcImmobilizer = true,
        hasGpsTracker = true,
        hasAlarmSystem = true,
        lockMethod = LockMethod.NFC_PIN,
        controllerVersion = "VESC-6.05",
        bmsVersion = "SMART-BMS-4.2",
        protocol = "CAN-FD",
        regenLevels = 4,
        hotspots = VehicleHotspots(
            battery = HotspotPoint(0.0f, 0.22f, 0.0f),
            motor = HotspotPoint(-0.58f, 0.19f, 0.0f),
            controller = HotspotPoint(0.44f, 0.86f, 0.0f)
        ),
        description = description,
        encoded3dModel = encoded3dModel
    )

    private companion object {
        const val ALICE_EMAIL = "alice@swoxgarage.test"
        const val BOB_EMAIL = "bob@swoxgarage.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
