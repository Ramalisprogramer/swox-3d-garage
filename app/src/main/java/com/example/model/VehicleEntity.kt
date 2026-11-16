package com.example.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

object FirestoreVehicleFields {
    const val DESCRIPTION = "haqqında"
    const val LEGACY_DESCRIPTION = "skutur haqqında"
    const val ENCODED_3D_MODEL = "skutur 3D modeli"
}

@Entity(
    tableName = "vehicles",
    indices = [
        Index(value = ["vehicleClass", "passportCode", "name"]),
        Index(value = ["name"])
    ]
)
data class VehicleEntity(
    @PrimaryKey val id: String,
    val ownerId: String = "",
    val isPublic: Boolean = true,
    val name: String,
    val brand: String,
    val vehicleClass: String,
    val passportCode: String,
    val releaseYear: Int,
    val modelAssetPath: String,
    val motorWatts: Int,
    val peakWatts: Int,
    val batteryVoltage: Int,
    val batteryCapacityAh: Double,
    val topSpeedKmh: Double,
    val rangeKm: Double,
    val weightKg: Double,
    val tireSize: String,
    val frameMaterial: String,
    val ipRating: String,
    val suspension: String,
    val brakeSystem: String,
    val hasNfcImmobilizer: Boolean,
    val hasGpsTracker: Boolean,
    val hasAlarmSystem: Boolean,
    val lockMethod: String,
    val controllerVersion: String,
    val bmsVersion: String,
    val protocol: String,
    val regenLevels: Int,
    val batteryHotspotX: Float,
    val batteryHotspotY: Float,
    val batteryHotspotZ: Float,
    val motorHotspotX: Float,
    val motorHotspotY: Float,
    val motorHotspotZ: Float,
    val controllerHotspotX: Float,
    val controllerHotspotY: Float,
    val controllerHotspotZ: Float,
    val description: String = "",
    val encoded3dModel: String = "",
    val syncedAtMillis: Long = System.currentTimeMillis()
)

fun VehicleEntity.toVehicleProfile(): VehicleProfile = VehicleProfile(
    id = id,
    name = name,
    brand = brand,
    vehicleClass = runCatching { VehicleClass.valueOf(vehicleClass) }.getOrDefault(VehicleClass.SCOOTER),
    passportCode = passportCode,
    releaseYear = releaseYear,
    modelAssetPath = modelAssetPath,
    motorWatts = motorWatts,
    peakWatts = peakWatts,
    batteryVoltage = batteryVoltage,
    batteryCapacityAh = batteryCapacityAh,
    topSpeedKmh = topSpeedKmh,
    rangeKm = rangeKm,
    weightKg = weightKg,
    tireSize = tireSize,
    frameMaterial = frameMaterial,
    ipRating = ipRating,
    suspension = suspension,
    brakeSystem = brakeSystem,
    hasNfcImmobilizer = hasNfcImmobilizer,
    hasGpsTracker = hasGpsTracker,
    hasAlarmSystem = hasAlarmSystem,
    lockMethod = runCatching { LockMethod.valueOf(lockMethod) }.getOrDefault(LockMethod.KEY_IGNITION),
    controllerVersion = controllerVersion,
    bmsVersion = bmsVersion,
    protocol = protocol,
    regenLevels = regenLevels,
    hotspots = VehicleHotspots(
        battery = HotspotPoint(batteryHotspotX, batteryHotspotY, batteryHotspotZ),
        motor = HotspotPoint(motorHotspotX, motorHotspotY, motorHotspotZ),
        controller = HotspotPoint(controllerHotspotX, controllerHotspotY, controllerHotspotZ)
    ),
    description = description,
    encoded3dModel = encoded3dModel
)

fun VehicleProfile.toEntity(
    ownerId: String = "catalog_seed",
    isPublic: Boolean = true,
    syncedAtMillis: Long = System.currentTimeMillis()
): VehicleEntity = VehicleEntity(
    id = id,
    ownerId = ownerId,
    isPublic = isPublic,
    name = name,
    brand = brand,
    vehicleClass = vehicleClass.name,
    passportCode = passportCode,
    releaseYear = releaseYear,
    modelAssetPath = modelAssetPath,
    motorWatts = motorWatts,
    peakWatts = peakWatts,
    batteryVoltage = batteryVoltage,
    batteryCapacityAh = batteryCapacityAh,
    topSpeedKmh = topSpeedKmh,
    rangeKm = rangeKm,
    weightKg = weightKg,
    tireSize = tireSize,
    frameMaterial = frameMaterial,
    ipRating = ipRating,
    suspension = suspension,
    brakeSystem = brakeSystem,
    hasNfcImmobilizer = hasNfcImmobilizer,
    hasGpsTracker = hasGpsTracker,
    hasAlarmSystem = hasAlarmSystem,
    lockMethod = lockMethod.name,
    controllerVersion = controllerVersion,
    bmsVersion = bmsVersion,
    protocol = protocol,
    regenLevels = regenLevels,
    batteryHotspotX = hotspots.battery.x,
    batteryHotspotY = hotspots.battery.y,
    batteryHotspotZ = hotspots.battery.z,
    motorHotspotX = hotspots.motor.x,
    motorHotspotY = hotspots.motor.y,
    motorHotspotZ = hotspots.motor.z,
    controllerHotspotX = hotspots.controller.x,
    controllerHotspotY = hotspots.controller.y,
    controllerHotspotZ = hotspots.controller.z,
    description = description,
    encoded3dModel = encoded3dModel,
    syncedAtMillis = syncedAtMillis
)

/**
 * Decodes a Firestore DocumentSnapshot from `[Category Name]/{vehicleId}`,
 * mapping the localized schema keys `haqqında` (with fallback to `skutur haqqında`)
 * and `skutur 3D modeli`.
 */
fun DocumentSnapshot.toVehicleEntityOrNull(
    syncedAtMillis: Long = System.currentTimeMillis(),
    fallbackVehicleClass: VehicleClass? = null
): VehicleEntity? {
    if (!exists()) return null
    val docName = getString("name").orEmpty().trim()
    if (docName.isEmpty()) return null

    fun numLong(key: String, default: Long): Long =
        (get(key) as? Number)?.toLong() ?: default

    fun numDouble(key: String, default: Double): Double =
        (get(key) as? Number)?.toDouble() ?: default

    val resolvedDescription = getString(FirestoreVehicleFields.DESCRIPTION)
        ?: getString(FirestoreVehicleFields.LEGACY_DESCRIPTION)
        ?: getString("description")
        ?: ""
    val resolved3dModel = getString(FirestoreVehicleFields.ENCODED_3D_MODEL)
        ?: getString("encoded3dModel")
        ?: ""
    val defaultClass = fallbackVehicleClass ?: VehicleClass.SCOOTER

    return VehicleEntity(
        id = id,
        ownerId = getString("ownerId").orEmpty(),
        isPublic = getBoolean("isPublic") ?: false,
        name = docName,
        brand = getString("brand").orEmpty().ifBlank { "Swox Rideables" },
        vehicleClass = getString("vehicleClass").orEmpty().ifBlank { defaultClass.name },
        passportCode = getString("passportCode").orEmpty().ifBlank { "SWX-$id" },
        releaseYear = numLong("releaseYear", 2026L).toInt(),
        modelAssetPath = getString("modelAssetPath").orEmpty(),
        motorWatts = numLong("motorWatts", 0L).toInt(),
        peakWatts = numLong("peakWatts", 0L).toInt(),
        batteryVoltage = numLong("batteryVoltage", 0L).toInt(),
        batteryCapacityAh = numDouble("batteryCapacityAh", 0.0),
        topSpeedKmh = numDouble("topSpeedKmh", 25.0),
        rangeKm = numDouble("rangeKm", 40.0),
        weightKg = numDouble("weightKg", 15.0),
        tireSize = getString("tireSize").orEmpty(),
        frameMaterial = getString("frameMaterial").orEmpty(),
        ipRating = getString("ipRating").orEmpty().ifBlank { "IPX4" },
        suspension = getString("suspension").orEmpty(),
        brakeSystem = getString("brakeSystem").orEmpty(),
        hasNfcImmobilizer = getBoolean("hasNfcImmobilizer") ?: false,
        hasGpsTracker = getBoolean("hasGpsTracker") ?: false,
        hasAlarmSystem = getBoolean("hasAlarmSystem") ?: false,
        lockMethod = getString("lockMethod").orEmpty().ifBlank { LockMethod.KEY_IGNITION.name },
        controllerVersion = getString("controllerVersion").orEmpty().ifBlank { "N/A" },
        bmsVersion = getString("bmsVersion").orEmpty().ifBlank { "N/A" },
        protocol = getString("protocol").orEmpty().ifBlank { "Standard" },
        regenLevels = numLong("regenLevels", 0L).toInt(),
        batteryHotspotX = numDouble("batteryHotspotX", 0.0).toFloat(),
        batteryHotspotY = numDouble("batteryHotspotY", 0.22).toFloat(),
        batteryHotspotZ = numDouble("batteryHotspotZ", 0.0).toFloat(),
        motorHotspotX = numDouble("motorHotspotX", -0.58).toFloat(),
        motorHotspotY = numDouble("motorHotspotY", 0.19).toFloat(),
        motorHotspotZ = numDouble("motorHotspotZ", 0.0).toFloat(),
        controllerHotspotX = numDouble("controllerHotspotX", 0.44).toFloat(),
        controllerHotspotY = numDouble("controllerHotspotY", 0.86).toFloat(),
        controllerHotspotZ = numDouble("controllerHotspotZ", 0.0).toFloat(),
        description = resolvedDescription,
        encoded3dModel = resolved3dModel,
        syncedAtMillis = syncedAtMillis
    )
}

fun VehicleProfile.toFirestoreCreatePayload(
    ownerId: String,
    isPublic: Boolean = false
): Map<String, Any> = mapOf(
    "ownerId" to ownerId,
    "isPublic" to isPublic,
    "name" to name,
    "brand" to brand,
    "vehicleClass" to vehicleClass.name,
    "passportCode" to passportCode,
    "releaseYear" to releaseYear,
    "modelAssetPath" to modelAssetPath,
    "motorWatts" to motorWatts,
    "peakWatts" to peakWatts,
    "batteryVoltage" to batteryVoltage,
    "batteryCapacityAh" to batteryCapacityAh,
    "topSpeedKmh" to topSpeedKmh,
    "rangeKm" to rangeKm,
    "weightKg" to weightKg,
    "tireSize" to tireSize,
    "frameMaterial" to frameMaterial,
    "ipRating" to ipRating,
    "suspension" to suspension,
    "brakeSystem" to brakeSystem,
    "hasNfcImmobilizer" to hasNfcImmobilizer,
    "hasGpsTracker" to hasGpsTracker,
    "hasAlarmSystem" to hasAlarmSystem,
    "lockMethod" to lockMethod.name,
    "controllerVersion" to controllerVersion,
    "bmsVersion" to bmsVersion,
    "protocol" to protocol,
    "regenLevels" to regenLevels,
    "batteryHotspotX" to hotspots.battery.x.toDouble(),
    "batteryHotspotY" to hotspots.battery.y.toDouble(),
    "batteryHotspotZ" to hotspots.battery.z.toDouble(),
    "motorHotspotX" to hotspots.motor.x.toDouble(),
    "motorHotspotY" to hotspots.motor.y.toDouble(),
    "motorHotspotZ" to hotspots.motor.z.toDouble(),
    "controllerHotspotX" to hotspots.controller.x.toDouble(),
    "controllerHotspotY" to hotspots.controller.y.toDouble(),
    "controllerHotspotZ" to hotspots.controller.z.toDouble(),
    FirestoreVehicleFields.DESCRIPTION to description.ifBlank { "$brand $name ($releaseYear) passport specification." },
    FirestoreVehicleFields.ENCODED_3D_MODEL to encoded3dModel,
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)
