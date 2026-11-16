package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.VehicleRepository
import com.example.data.local.GarageDatabase
import com.example.model.HotspotPoint
import com.example.model.LockMethod
import com.example.model.VehicleClass
import com.example.model.VehicleHotspots
import com.example.model.VehicleProfile
import com.example.model3d.Decoded3DModel
import com.example.model3d.Encoded3DModelCodec
import com.example.model3d.buildVehicle3DWireframe
import com.example.ui.theme.createSwoxTypography
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read localized strings and unified typography across en az tr`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Swox 3D Garage", baseContext.getString(R.string.app_name))
        assertEquals("Peak 1000 W", baseContext.getString(R.string.format_peak_watts, 1000))
        assertEquals("[ Standard PBR ]", baseContext.getString(R.string.format_render_mode_pill, "Standard PBR"))
        assertEquals("Scooters (1000)", baseContext.getString(R.string.category_with_count, baseContext.getString(R.string.category_scooter), 1000))
        assertEquals("%.1f", baseContext.getString(R.string.format_decimal_1))

        val azConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("az"))
        }
        val azContext = baseContext.createConfigurationContext(azConfig)
        assertEquals("ƏSAS GÖSTƏRİCİLƏR", azContext.getString(R.string.section_core_specs))
        assertEquals("NƏQLİYYAT HAQQINDA", azContext.getString(R.string.spec_vehicle_about))
        assertEquals("Pik 1000 W", azContext.getString(R.string.format_peak_watts, 1000))
        assertEquals("Nəqliyyat kataloqu yüklənə bilmədi.", azContext.getString(R.string.error_catalog_load_failed))

        val trConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("tr"))
        }
        val trContext = baseContext.createConfigurationContext(trConfig)
        assertEquals("TEMEL ÖZELLİKLER", trContext.getString(R.string.section_core_specs))
        assertEquals("ARAÇ HAKKINDA", trContext.getString(R.string.spec_vehicle_about))
        assertEquals("Tepe 1000 W", trContext.getString(R.string.format_peak_watts, 1000))
        assertEquals("Araç kataloğu yüklenemedi.", trContext.getString(R.string.error_catalog_load_failed))

        val typography = createSwoxTypography(baseContext)
        assertNotNull(typography.headlineSmall.fontFamily)
        assertNotNull(typography.bodyMedium.fontFamily)
    }

    @Test
    fun `offline Room seed loads 1000 items per category across all 7 categories excluding Kugoo M2 and Velostrim`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbId = context.getString(R.string.firestore_database_id)
        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.swoxgarage.vxqmkr")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key-for-test")
                    .build()
            )
        } else {
            FirebaseApp.getInstance()
        }
        val roomDb = Room.inMemoryDatabaseBuilder(context, GarageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = VehicleRepository(
                context = context,
                vehicleDao = roomDb.vehicleDao(),
                db = FirebaseFirestore.getInstance(app, dbId),
                auth = FirebaseAuth.getInstance(app)
            )
            repository.ensureLocalSeeded()

            val totalCount = repository.observeTotalCachedCount().first()
            assertEquals(7000, totalCount)

            val categoryCounts = repository.observeCategoryCounts().first()
            VehicleClass.entries.forEach { cls ->
                assertEquals(
                    "Category ${cls.name} must contain exactly 1000 items",
                    1000,
                    categoryCounts[cls]
                )
            }

            val allSummaries = repository.observeLocalVehicleSummaries(query = "", category = null, limit = 7000).first()
            assertEquals(7000, allSummaries.size)
            assertTrue(allSummaries.any { it.id == "kugoo_m4_pro" })
            assertTrue(allSummaries.any { it.id == "segway_gt2_superscooter" })
            assertFalse(
                "Velostrim must be excluded across all 7000 items",
                allSummaries.any { it.id.contains("velostrim", ignoreCase = true) || it.name.contains("velostrim", ignoreCase = true) }
            )
            assertFalse(
                "Kugoo M2 must be excluded across all 7000 items",
                allSummaries.any { it.id.contains("kugoo_m2", ignoreCase = true) || it.name.contains("kugoo m2", ignoreCase = true) }
            )

            val scootersPage = repository.observeLocalVehicleSummaries(query = "", category = VehicleClass.SCOOTER, limit = 1000).first()
            assertEquals(1000, scootersPage.size)
            val bicycleSample = repository.getCachedVehicleById("bicycle_cat_0500")
            assertNotNull(bicycleSample)
            assertTrue(bicycleSample!!.encoded3dModel.isNotBlank())
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun `text-encoded 3D codec decodes both SWX3D_GZ meshes and GZ_GLB binary buffers`() = runBlocking {
        val mesh = buildVehicle3DWireframe(VehicleClass.E_SKATE)
        val encodedMesh = Encoded3DModelCodec.encodeWireframeMesh(mesh)
        val profileMesh = sampleProfile(id = "skate_1", encoded = encodedMesh)
        val decodedMesh = Encoded3DModelCodec.decode(profileMesh)
        assertTrue(decodedMesh is Decoded3DModel.ProceduralMesh)
        assertEquals(mesh.segments.size, (decodedMesh as Decoded3DModel.ProceduralMesh).mesh.segments.size)

        // Minimal 12-byte GLB header ('glTF' magic + version 2 + length 12)
        val minimalGlb = byteArrayOf(
            0x67, 0x6C, 0x54, 0x46,
            0x02, 0x00, 0x00, 0x00,
            0x0C, 0x00, 0x00, 0x00
        )
        val encodedGlb = Encoded3DModelCodec.encodeGlbBytes(minimalGlb)
        val profileGlb = sampleProfile(id = "glb_1", encoded = encodedGlb)
        val decodedGlb = Encoded3DModelCodec.decode(profileGlb)
        assertTrue(decodedGlb is Decoded3DModel.GlbBuffer)
        assertEquals(12, (decodedGlb as Decoded3DModel.GlbBuffer).byteSize)
    }

    private fun sampleProfile(id: String, encoded: String) = VehicleProfile(
        id = id,
        name = "Test Rideable",
        brand = "Swox",
        vehicleClass = VehicleClass.E_SKATE,
        passportCode = "SWX-TEST",
        releaseYear = 2026,
        modelAssetPath = "",
        motorWatts = 1500,
        peakWatts = 3000,
        batteryVoltage = 48,
        batteryCapacityAh = 12.0,
        topSpeedKmh = 48.0,
        rangeKm = 40.0,
        weightKg = 11.5,
        tireSize = "105mm PU",
        frameMaterial = "Carbon Fiber",
        ipRating = "IPX5",
        suspension = "DKP Trucks",
        brakeSystem = "Regenerative ESC",
        hasNfcImmobilizer = false,
        hasGpsTracker = false,
        hasAlarmSystem = false,
        lockMethod = LockMethod.APP_BLE,
        controllerVersion = "VESC-6",
        bmsVersion = "BMS-12S",
        protocol = "BLE-5.2",
        regenLevels = 4,
        hotspots = VehicleHotspots(
            battery = HotspotPoint(0f, 0.14f, 0f),
            motor = HotspotPoint(-0.46f, 0.12f, 0f),
            controller = HotspotPoint(0.32f, 0.18f, 0f)
        ),
        description = "Test carbon deck e-skate",
        encoded3dModel = encoded
    )
}
