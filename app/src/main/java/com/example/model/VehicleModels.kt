package com.example.model

import com.squareup.moshi.JsonClass

enum class VehicleClass(val firestoreCollection: String) {
    SCOOTER("E-Scooters"),
    E_BIKE("E-Bikes"),
    BICYCLE("Bicycles"),
    MOTO("Moto EV"),
    E_SKATE("E-Skates"),
    ONEWHEEL("Onewheels"),
    MICRO_EV("Micro EV");

    companion object {
        fun fromFirestoreCollection(collectionName: String?): VehicleClass? =
            entries.firstOrNull { it.firestoreCollection == collectionName }
    }
}

enum class LockMethod {
    KEY_IGNITION,
    NFC_PIN,
    APP_BLE,
    SMART_FOB
}

enum class HotspotType {
    BATTERY_PACK,
    REAR_MOTOR,
    STEM_CONTROLLER
}

enum class RenderMode {
    STANDARD_PBR,
    WIREFRAME_XRAY
}

enum class AppLanguage(val code: String) {
    EN("en"),
    AZ("az"),
    TR("tr");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EN
    }
}

enum class UnitSystem {
    METRIC,
    IMPERIAL;

    companion object {
        fun fromName(name: String?): UnitSystem =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: METRIC
    }
}

enum class SpecSection {
    OVERVIEW,
    CORE_SPECS,
    PHYSICAL_DATA,
    SECURITY,
    FIRMWARE
}

enum class SpecTargetKey {
    VEHICLE_OVERVIEW,
    MOTOR_POWER,
    BATTERY_PACK,
    TOP_SPEED,
    RANGE,
    WEIGHT,
    TIRE_SIZE,
    FRAME,
    IP_RATING,
    BRAKES,
    SUSPENSION,
    LOCK_AUTH,
    GPS_ALARM,
    CONTROLLER_FW,
    BMS_PROTOCOL
}

@JsonClass(generateAdapter = true)
data class HotspotPoint(
    val x: Float,
    val y: Float,
    val z: Float
)

@JsonClass(generateAdapter = true)
data class VehicleHotspots(
    val battery: HotspotPoint,
    val motor: HotspotPoint,
    val controller: HotspotPoint
) {
    fun pointFor(type: HotspotType): HotspotPoint = when (type) {
        HotspotType.BATTERY_PACK -> battery
        HotspotType.REAR_MOTOR -> motor
        HotspotType.STEM_CONTROLLER -> controller
    }
}

data class VehicleSummary(
    val id: String,
    val name: String,
    val brand: String,
    val vehicleClass: VehicleClass,
    val passportCode: String,
    val motorWatts: Int,
    val topSpeedKmh: Double
)

@JsonClass(generateAdapter = true)
data class VehicleProfile(
    val id: String,
    val name: String,
    val brand: String,
    val vehicleClass: VehicleClass,
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
    val lockMethod: LockMethod,
    val controllerVersion: String,
    val bmsVersion: String,
    val protocol: String,
    val regenLevels: Int,
    val hotspots: VehicleHotspots,
    val description: String = "",
    val encoded3dModel: String = ""
) {
    val wattHours: Int
        get() = (batteryVoltage * batteryCapacityAh).toInt()
}
