package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.GarageDatabase
import com.example.data.local.VehicleDao
import com.example.model.HotspotPoint
import com.example.model.LockMethod
import com.example.model.VehicleClass
import com.example.model.VehicleHotspots
import com.example.model.VehicleProfile
import com.example.model.VehicleSummary
import com.example.model.toEntity
import com.example.model.toFirestoreCreatePayload
import com.example.model.toVehicleEntityOrNull
import com.example.model.toVehicleProfile
import com.example.model3d.Encoded3DModelCodec
import com.example.model3d.buildVehicle3DWireframe
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException

enum class CatalogSyncStatus {
    OFFLINE_CACHE,
    SYNCING,
    CLOUD_SYNCED
}

class VehicleRepository(
    private val context: Context,
    private val vehicleDao: VehicleDao,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // Convenience constructor resolving the auto-provisioned custom Firestore database ID
    constructor(context: Context) : this(
        context = context.applicationContext,
        vehicleDao = GarageDatabase.getInstance(context.applicationContext).vehicleDao(),
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val catalogAdapter: JsonAdapter<List<VehicleProfile>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, VehicleProfile::class.java)
    )

    private val seedMutex = Mutex()

    /**
     * Seeds local Room database with 1,000 distinct rideable models per category (7,000 total),
     * prioritizing the embedded curated catalog while strictly excluding Kugoo M2 and Velostrim.
     */
    suspend fun ensureLocalSeeded() = seedMutex.withLock {
        val currentCounts = vehicleDao.getCategoryCountsOnce()
            .associate { it.vehicleClass to it.itemCount }
        val allCategoriesReady = VehicleClass.entries.all { cls ->
            (currentCounts[cls.name] ?: 0) >= MAX_ITEMS_PER_CATEGORY
        }
        if (allCategoriesReady) return@withLock

        val entities = withContext(Dispatchers.IO) {
            val json = context.assets.open(CATALOG_ASSET_PATH).bufferedReader().use { it.readText() }
            val bundled = catalogAdapter.fromJson(json)
                .orEmpty()
                .filterNot { isExcludedModel(it.id, it.name) }

            val bundledByClass = bundled.groupBy { it.vehicleClass }
            val allProfiles = ArrayList<VehicleProfile>(VehicleClass.entries.size * MAX_ITEMS_PER_CATEGORY)
            for (vehicleClass in VehicleClass.entries) {
                val seedForClass = bundledByClass[vehicleClass].orEmpty()
                allProfiles.addAll(
                    expandCategoryToTargetCount(
                        vehicleClass = vehicleClass,
                        curatedItems = seedForClass,
                        targetCount = MAX_ITEMS_PER_CATEGORY
                    )
                )
            }
            allProfiles.map { it.toEntity(ownerId = "catalog_seed", isPublic = true) }
        }

        if (entities.isNotEmpty()) {
            vehicleDao.upsertAllChunked(entities)
        }
    }

    /**
     * Lightweight offline-first paginated query backed by covered Room SQLite indexes,
     * scaling across 1,000 items per category (7,000+ total) without loading large 3D payloads.
     */
    fun observeLocalVehicleSummaries(
        query: String,
        category: VehicleClass?,
        limit: Int
    ): Flow<List<VehicleSummary>> {
        return vehicleDao.observeFilteredSummaries(
            query = query.trim(),
            category = category?.name,
            limit = limit
        ).map { rows ->
            rows.map { it.toSummary() }
        }
    }

    fun observeLocalVehicles(
        query: String,
        category: VehicleClass?,
        limit: Int
    ): Flow<List<VehicleProfile>> {
        return vehicleDao.observeFilteredVehicles(
            query = query.trim(),
            category = category?.name,
            limit = limit
        ).map { entities ->
            entities.map { it.toVehicleProfile() }
        }
    }

    fun observeCategoryCounts(): Flow<Map<VehicleClass, Int>> {
        return vehicleDao.observeCategoryCounts().map { rows ->
            val byName = rows.associate { it.vehicleClass to it.itemCount }
            VehicleClass.entries.associateWith { cls -> byName[cls.name] ?: 0 }
        }
    }

    fun observeTotalCachedCount(): Flow<Int> = vehicleDao.observeTotalCount()

    suspend fun getCachedVehicleById(id: String): VehicleProfile? {
        return vehicleDao.getById(id)?.toVehicleProfile()
    }

    /**
     * Login-free background sync listening across all 7 category collections
     * (`[Category Name]/{vehicleId}` where `isPublic == true`), extracting
     * `haqqında` and `skutur 3D modeli` into local Room cache.
     */
    fun observeCloudCatalogSync(): Flow<CatalogSyncStatus> = flow {
        emit(CatalogSyncStatus.SYNCING)

        val categoryFlows = VehicleClass.entries.map { cls ->
            db.collection(cls.firestoreCollection)
                .whereEqualTo("isPublic", true)
                .limit(CLOUD_SYNC_BATCH_LIMIT)
                .snapshots()
                .map { snap ->
                    val now = System.currentTimeMillis()
                    snap.documents.mapNotNull { doc ->
                        doc.toVehicleEntityOrNull(
                            syncedAtMillis = now,
                            fallbackVehicleClass = cls
                        )?.takeUnless { isExcludedModel(it.id, it.name) }
                    }
                }
        }

        val combinedSyncFlow = combine(categoryFlows) { batches ->
            val merged = batches.flatMap { it }
            if (merged.isNotEmpty()) {
                vehicleDao.upsertAllChunked(merged)
            }
            CatalogSyncStatus.CLOUD_SYNCED
        }

        try {
            emitAll(combinedSyncFlow)
        } catch (error: Throwable) {
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null) {
                handleFirestoreError(
                    exceptionToLog,
                    OperationType.LIST,
                    VehicleClass.entries.joinToString(",") { it.firestoreCollection },
                    auth
                )
            }
            Log.w(TAG, "Cloud sync degraded to offline Room cache", error)
            emit(CatalogSyncStatus.OFFLINE_CACHE)
        }
    }

    /**
     * Fetches a single public rideable document from `[Category Name]/{vehicleId}`
     * without requiring user login, mirroring `haqqında` and `skutur 3D modeli` into Room.
     */
    suspend fun fetchVehicleById(
        vehicleId: String,
        vehicleClass: VehicleClass = VehicleClass.SCOOTER
    ): Result<VehicleProfile?> = runCatching {
        val docRef = db.collection(vehicleClass.firestoreCollection).document(vehicleId)
        try {
            val snapshot = docRef.get().await()
            snapshot.toVehicleEntityOrNull(fallbackVehicleClass = vehicleClass)
                ?.takeUnless { isExcludedModel(it.id, it.name) }
                ?.let { entity ->
                    vehicleDao.upsert(entity)
                    entity.toVehicleProfile()
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            throw e
        }
    }

    /**
     * Publishes or updates a rideable passport in `[Category Name]/{vehicleId}`
     * when an authenticated publisher session exists, and caches it locally in Room.
     */
    suspend fun saveVehicleToCloud(
        vehicle: VehicleProfile,
        isPublic: Boolean = true
    ): Result<String> = runCatching {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException(context.getString(R.string.error_cloud_write_auth_required))
        val collectionRef = db.collection(vehicle.vehicleClass.firestoreCollection)
        val docId = vehicle.id.ifBlank { collectionRef.document().id }
        val docRef = collectionRef.document(docId)
        val payload = vehicle.toFirestoreCreatePayload(ownerId = uid, isPublic = isPublic)
        try {
            docRef.set(payload).await()
            vehicleDao.upsert(vehicle.copy(id = docId).toEntity(ownerId = uid, isPublic = isPublic))
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun hasBundledGlbAsset(assetPath: String): Boolean = withContext(Dispatchers.IO) {
        if (assetPath.isBlank()) return@withContext false
        try {
            context.assets.open(assetPath).use { true }
        } catch (_: IOException) {
            false
        }
    }

    private fun isExcludedModel(id: String, name: String): Boolean {
        val idLower = id.lowercase()
        val nameLower = name.lowercase()
        return idLower.contains("velostrim") ||
            nameLower.contains("velostrim") ||
            idLower.contains("kugoo_m2") ||
            nameLower.contains("kugoo m2")
    }

    private fun expandCategoryToTargetCount(
        vehicleClass: VehicleClass,
        curatedItems: List<VehicleProfile>,
        targetCount: Int
    ): List<VehicleProfile> {
        if (curatedItems.size >= targetCount) return curatedItems.take(targetCount)

        val result = ArrayList<VehicleProfile>(targetCount)
        result.addAll(curatedItems)

        val template = curatedItems.firstOrNull() ?: defaultTemplateFor(vehicleClass)
        val sharedEncoded3d = template.encoded3dModel.ifBlank {
            Encoded3DModelCodec.encodeWireframeMesh(buildVehicle3DWireframe(vehicleClass))
        }
        val specPool = categorySpecPool(vehicleClass)
        val brands = specPool.brands
        val series = specPool.seriesNames
        val trims = specPool.trimNames
        val classPrefix = vehicleClass.name.lowercase()

        var index = curatedItems.size + 1
        while (result.size < targetCount) {
            val brand = brands[(index - 1) % brands.size]
            val modelFamily = series[((index - 1) / brands.size) % series.size]
            val trim = trims[((index - 1) / (brands.size * series.size)) % trims.size]
            val codeNum = index.toString().padStart(4, '0')
            val modelName = "$brand $modelFamily $trim #$codeNum"
            val id = "${classPrefix}_cat_$codeNum"

            val speedVariance = ((index * 7) % 19) - 9
            val rangeVariance = ((index * 11) % 35) - 15
            val weightVariance = ((index * 3) % 7) - 3
            val year = 2022 + (index % 5)
            val motorWatts = if (vehicleClass == VehicleClass.BICYCLE) {
                0
            } else {
                (template.motorWatts + ((index % 6) * 150)).coerceAtLeast(250)
            }
            val peakWatts = if (motorWatts == 0) 0 else (motorWatts * 1.85).toInt()
            val voltage = if (motorWatts == 0) 0 else template.batteryVoltage
            val capacityAh = if (motorWatts == 0) 0.0 else (template.batteryCapacityAh + (index % 5) * 1.5)
            val topSpeed = (template.topSpeedKmh + speedVariance * 0.8).coerceAtLeast(18.0)
            val range = if (vehicleClass == VehicleClass.BICYCLE) 0.0 else (template.rangeKm + rangeVariance).coerceAtLeast(18.0)
            val weight = (template.weightKg + weightVariance * 0.4).coerceAtLeast(6.5)

            result.add(
                template.copy(
                    id = id,
                    name = modelName,
                    brand = brand,
                    vehicleClass = vehicleClass,
                    passportCode = "SWX-${vehicleClass.name}-$codeNum",
                    releaseYear = year,
                    motorWatts = motorWatts,
                    peakWatts = peakWatts,
                    batteryVoltage = voltage,
                    batteryCapacityAh = capacityAh,
                    topSpeedKmh = topSpeed,
                    rangeKm = range,
                    weightKg = weight,
                    regenLevels = if (motorWatts == 0) 0 else (1 + (index % 5)),
                    description = "${vehicleClass.firestoreCollection} Series #$codeNum — $brand $modelFamily $trim engineered for ${template.suspension} damping and ${template.brakeSystem} stopping precision.",
                    encoded3dModel = sharedEncoded3d
                )
            )
            index++
        }
        return result
    }

    private fun defaultTemplateFor(vehicleClass: VehicleClass): VehicleProfile {
        val hotspots = when (vehicleClass) {
            VehicleClass.SCOOTER -> VehicleHotspots(HotspotPoint(0f, 0.22f, 0f), HotspotPoint(-0.58f, 0.19f, 0f), HotspotPoint(0.44f, 0.86f, 0f))
            VehicleClass.E_BIKE -> VehicleHotspots(HotspotPoint(0.05f, 0.46f, 0f), HotspotPoint(-0.56f, 0.28f, 0f), HotspotPoint(0.38f, 0.92f, 0f))
            VehicleClass.BICYCLE -> VehicleHotspots(HotspotPoint(0.02f, 0.48f, 0f), HotspotPoint(-0.56f, 0.28f, 0f), HotspotPoint(0.38f, 0.92f, 0f))
            VehicleClass.MOTO -> VehicleHotspots(HotspotPoint(0.04f, 0.48f, 0f), HotspotPoint(-0.55f, 0.30f, 0f), HotspotPoint(0.42f, 0.88f, 0f))
            VehicleClass.E_SKATE -> VehicleHotspots(HotspotPoint(0f, 0.14f, 0f), HotspotPoint(-0.46f, 0.12f, 0f), HotspotPoint(0.32f, 0.18f, 0f))
            VehicleClass.ONEWHEEL -> VehicleHotspots(HotspotPoint(-0.22f, 0.22f, 0f), HotspotPoint(0f, 0.26f, 0f), HotspotPoint(0.24f, 0.22f, 0f))
            VehicleClass.MICRO_EV -> VehicleHotspots(HotspotPoint(0f, 0.26f, 0f), HotspotPoint(-0.62f, 0.24f, 0f), HotspotPoint(0.42f, 0.72f, 0f))
        }
        val pool = categorySpecPool(vehicleClass)
        return VehicleProfile(
            id = "${vehicleClass.name.lowercase()}_template",
            name = "${pool.brands.first()} ${pool.seriesNames.first()}",
            brand = pool.brands.first(),
            vehicleClass = vehicleClass,
            passportCode = "SWX-${vehicleClass.name}-0000",
            releaseYear = 2026,
            modelAssetPath = "",
            motorWatts = pool.baseWatts,
            peakWatts = (pool.baseWatts * 1.8).toInt(),
            batteryVoltage = pool.baseVoltage,
            batteryCapacityAh = pool.baseAh,
            topSpeedKmh = pool.baseSpeed,
            rangeKm = pool.baseRange,
            weightKg = pool.baseWeight,
            tireSize = pool.tireSize,
            frameMaterial = pool.frameMaterial,
            ipRating = "IPX5",
            suspension = pool.suspension,
            brakeSystem = pool.brakes,
            hasNfcImmobilizer = true,
            hasGpsTracker = true,
            hasAlarmSystem = true,
            lockMethod = LockMethod.APP_BLE,
            controllerVersion = "SWX-FOC v4.8",
            bmsVersion = "SmartBMS v3.4",
            protocol = "CAN-FD",
            regenLevels = if (pool.baseWatts > 0) 4 else 0,
            hotspots = hotspots,
            description = "${vehicleClass.firestoreCollection} certified digital passport.",
            encoded3dModel = ""
        )
    }

    private data class CategorySpecPool(
        val brands: List<String>,
        val seriesNames: List<String>,
        val trimNames: List<String>,
        val baseWatts: Int,
        val baseVoltage: Int,
        val baseAh: Double,
        val baseSpeed: Double,
        val baseRange: Double,
        val baseWeight: Double,
        val tireSize: String,
        val frameMaterial: String,
        val suspension: String,
        val brakes: String
    )

    private fun categorySpecPool(vehicleClass: VehicleClass): CategorySpecPool = when (vehicleClass) {
        VehicleClass.SCOOTER -> CategorySpecPool(
            brands = listOf("Kugoo", "Segway", "Dualtron", "NAMI", "Kaabo", "Apollo", "VSETT", "Inokim", "Teverun", "Bronco"),
            seriesNames = listOf("Kirin", "GT", "Thunder", "Burn-E", "Mantis", "Phantom", "Fighter", "OXO", "Wolf", "Blade"),
            trimNames = listOf("Pro", "Max", "RS", "Ultra", "Carbon", "Evo", "SE", "Long Range", "Track", "Apex"),
            baseWatts = 800, baseVoltage = 52, baseAh = 18.2, baseSpeed = 48.0, baseRange = 65.0, baseWeight = 24.5,
            tireSize = "10x3.0\" Tubeless", frameMaterial = "6061-T6 Forged Alloy", suspension = "Dual Hydraulic Coil", brakes = "Dual Hydraulic Disc + E-ABS"
        )
        VehicleClass.E_BIKE -> CategorySpecPool(
            brands = listOf("Specialized", "Trek", "Canyon", "Sur-Ron", "Super73", "Rad Power", "Riese & Muller", "Aventon", "VanMoof", "Cowboy"),
            seriesNames = listOf("Turbo", "Rail", "Spectral", "Light Bee", "RX", "Aventure", "Charger", "Vado", "Cruiser", "Strive"),
            trimNames = listOf("Comp", "Expert", "SL", "GT", "Touring", "Trail", "Commuter", "X-Series", "Pro", "Factory"),
            baseWatts = 750, baseVoltage = 48, baseAh = 17.5, baseSpeed = 45.0, baseRange = 95.0, baseWeight = 23.0,
            tireSize = "27.5x2.4\" Schwalbe", frameMaterial = "Hydroformed Alloy", suspension = "Air Fork 140mm", brakes = "4-Piston Hydraulic Disc"
        )
        VehicleClass.BICYCLE -> CategorySpecPool(
            brands = listOf("Trek", "Specialized", "Cannondale", "Canyon", "Giant", "Pinarello", "Cervelo", "BMC", "Scott", "Bianchi"),
            seriesNames = listOf("Madone", "Tarmac", "SuperSix", "Aeroad", "Propel", "Dogma", "S5", "Teammachine", "Foil", "Oltre"),
            trimNames = listOf("SLR", "SL", "Team", "Ultegra", "Dura-Ace", "Force", "Comp", "Factory", "Aero", "Endurance"),
            baseWatts = 0, baseVoltage = 0, baseAh = 0.0, baseSpeed = 52.0, baseRange = 0.0, baseWeight = 7.4,
            tireSize = "700x28c Tubeless", frameMaterial = "Toray T1100 Carbon", suspension = "IsoSpeed Carbon", brakes = "Hydraulic Flat-Mount Disc"
        )
        VehicleClass.MOTO -> CategorySpecPool(
            brands = listOf("Zero", "Energica", "Stark", "Cake", "LiveWire", "Talaria", "NIU", "Super Soco", "Horwin", "Verge"),
            seriesNames = listOf("SR/F", "Eva Ribelle", "Varg", "Kalk", "Del Mar", "Sting", "RQi", "TC Max", "CR6", "TS Pro"),
            trimNames = listOf("RS", "Factory", "Rally", "Street", "Corsa", "Track", "Enduro", "SE", "GT", "Performance"),
            baseWatts = 11000, baseVoltage = 102, baseAh = 85.0, baseSpeed = 145.0, baseRange = 185.0, baseWeight = 138.0,
            tireSize = "17\" Pirelli Diablo", frameMaterial = "Chromoly Trellis", suspension = "Inverted Showa USD", brakes = "Brembo Dual Radial ABS"
        )
        VehicleClass.E_SKATE -> CategorySpecPool(
            brands = listOf("Exway", "Meepo", "Backfire", "Evolve", "Boosted", "WowGo", "Ownboard", "Acedeck", "Lacroix", "Kaly"),
            seriesNames = listOf("Atlas", "Hurricane", "Zealot", "Hadean", "Stealth", "Pioneer", "Zeus", "Nyx", "Nazare", "XL"),
            trimNames = listOf("Carbon", "AWD", "Pro", "Belt", "Gear Drive", "AT", "Street", "Bamboo", "GT", "4WD"),
            baseWatts = 2400, baseVoltage = 44, baseAh = 12.0, baseSpeed = 50.0, baseRange = 45.0, baseWeight = 11.8,
            tireSize = "105mm MadWheelz PU", frameMaterial = "Forged Carbon Composite", suspension = "CNC TKP / DKP Trucks", brakes = "Regenerative Dual FOC"
        )
        VehicleClass.ONEWHEEL -> CategorySpecPool(
            brands = listOf("InMotion", "Veteran", "Begode", "Kingsong", "LeaperKim", "Future Motion", "Floatwheel"),
            seriesNames = listOf("V14", "Sherman", "Master", "Lynx", "S22", "GTS", "ADV", "EX30", "Patton", "S16"),
            trimNames = listOf("Adventure", "Rally", "Pro", "Extreme", "High Torque", "High Speed", "Apex", "SE", "50S", "Max"),
            baseWatts = 3200, baseVoltage = 134, baseAh = 24.0, baseSpeed = 70.0, baseRange = 120.0, baseWeight = 34.0,
            tireSize = "20x3.0\" Knobby", frameMaterial = "Magnesium CNC Chassis", suspension = "85mm Progressive Air", brakes = "High-Current Vector Regen"
        )
        VehicleClass.MICRO_EV -> CategorySpecPool(
            brands = listOf("Citroen", "Renault", "Microlino", "Wuling", "XEV", "Silence", "Eli", "Squad", "Bajaj", "Triggo"),
            seriesNames = listOf("Ami", "Twizy", "Pioneer", "Nano", "Yoyo", "S04", "Zero", "Solar", "Qute", "City"),
            trimNames = listOf("Buggy", "Cargo", "Urban", "Lite", "Techno", "Dolce", "Fleet", "Vibe", "Pop", "Plus"),
            baseWatts = 6000, baseVoltage = 72, baseAh = 110.0, baseSpeed = 80.0, baseRange = 135.0, baseWeight = 425.0,
            tireSize = "145/70 R13", frameMaterial = "Tubular Steel Safety Cell", suspension = "MacPherson Strut", brakes = "4-Wheel Hydraulic Disc"
        )
    }

    companion object {
        private const val TAG = "VehicleRepository"
        const val CATALOG_ASSET_PATH = "vehicles_catalog.json"
        const val COLLECTION_SCOOTERS = "E-Scooters"
        const val MAX_ITEMS_PER_CATEGORY = 1000
        const val CLOUD_SYNC_BATCH_LIMIT = 1000L
    }
}
