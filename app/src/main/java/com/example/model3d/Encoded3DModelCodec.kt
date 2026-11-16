package com.example.model3d

import android.util.Base64
import android.util.LruCache
import com.example.model.HotspotPoint
import com.example.model.VehicleClass
import com.example.model.VehicleProfile
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class SegmentRole {
    CHASSIS,
    SUSPENSION,
    BATTERY_CORE,
    MOTOR_HUB,
    WHEEL
}

@JsonClass(generateAdapter = true)
data class Segment3D(
    val start: HotspotPoint,
    val end: HotspotPoint,
    val role: SegmentRole
)

@JsonClass(generateAdapter = true)
data class Panel3D(
    val points: List<HotspotPoint>
)

@JsonClass(generateAdapter = true)
data class WireframeMesh(
    val segments: List<Segment3D>,
    val solidPanels: List<Panel3D>
)

sealed interface Decoded3DModel {
    data class GlbBuffer(
        val modelId: String,
        val buffer: ByteBuffer,
        val byteSize: Int,
        val sourceFormat: String
    ) : Decoded3DModel

    data class ProceduralMesh(
        val modelId: String,
        val mesh: WireframeMesh,
        val sourceFormat: String
    ) : Decoded3DModel
}

/**
 * Runtime background codec that encodes/decodes text-encoded 3D models (`skutur 3D modeli`)
 * from Firestore or offline Room cache into interactive Filament GLB ByteBuffers or 3D wireframe/panel meshes.
 *
 * Supported formats:
 * - `GZ_GLB:<base64>`: GZIP-compressed binary glTF (.glb) -> decoded to direct ByteBuffer for Filament ModelLoader
 * - `GLB_B64:<base64>`: Base64-encoded binary glTF (.glb) -> decoded to direct ByteBuffer for Filament ModelLoader
 * - `SWX3D_GZ:<base64>`: GZIP-compressed JSON WireframeMesh -> decoded into 3D segments and solid panels
 * - `SWX3D:<base64>`: Base64-encoded JSON WireframeMesh
 * - Raw Base64 (auto-detects GZIP magic `1F 8B` or glTF magic `"glTF"`) or raw JSON mesh
 */
object Encoded3DModelCodec {
    const val PREFIX_GZ_GLB = "GZ_GLB:"
    const val PREFIX_GLB_B64 = "GLB_B64:"
    const val PREFIX_SWX3D_GZ = "SWX3D_GZ:"
    const val PREFIX_SWX3D = "SWX3D:"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val meshAdapter = moshi.adapter(WireframeMesh::class.java)

    private val cache = LruCache<String, Decoded3DModel>(32)

    suspend fun decode(vehicle: VehicleProfile): Decoded3DModel = withContext(Dispatchers.Default) {
        val raw = vehicle.encoded3dModel.trim()
        val cacheKey = "${vehicle.id}:${vehicle.vehicleClass.name}:${raw.hashCode()}"
        cache.get(cacheKey)?.let { return@withContext it }

        val decoded = decodeInternal(
            modelId = vehicle.id,
            vehicleClass = vehicle.vehicleClass,
            encoded = raw
        )
        cache.put(cacheKey, decoded)
        decoded
    }

    fun decodeSync(
        modelId: String,
        vehicleClass: VehicleClass,
        encoded: String
    ): Decoded3DModel {
        val raw = encoded.trim()
        val cacheKey = "$modelId:${vehicleClass.name}:${raw.hashCode()}"
        cache.get(cacheKey)?.let { return it }
        val decoded = decodeInternal(modelId, vehicleClass, raw)
        cache.put(cacheKey, decoded)
        return decoded
    }

    fun encodeWireframeMesh(mesh: WireframeMesh, compress: Boolean = true): String {
        val jsonBytes = meshAdapter.toJson(mesh).toByteArray(Charsets.UTF_8)
        return if (compress) {
            PREFIX_SWX3D_GZ + encodeBase64(gzipCompress(jsonBytes))
        } else {
            PREFIX_SWX3D + encodeBase64(jsonBytes)
        }
    }

    fun encodeGlbBytes(glbBytes: ByteArray, compress: Boolean = true): String {
        return if (compress) {
            PREFIX_GZ_GLB + encodeBase64(gzipCompress(glbBytes))
        } else {
            PREFIX_GLB_B64 + encodeBase64(glbBytes)
        }
    }

    private fun decodeInternal(
        modelId: String,
        vehicleClass: VehicleClass,
        encoded: String
    ): Decoded3DModel {
        if (encoded.isEmpty()) {
            return Decoded3DModel.ProceduralMesh(
                modelId = modelId,
                mesh = buildVehicle3DWireframe(vehicleClass),
                sourceFormat = "PARAMETRIC_FALLBACK"
            )
        }

        val parsed = runCatching {
            when {
                encoded.startsWith(PREFIX_GZ_GLB) -> {
                    val bytes = gunzipIfNeeded(decodeBase64(encoded.removePrefix(PREFIX_GZ_GLB)))
                    toGlbOrNull(modelId, bytes, "GZ_GLB")
                }

                encoded.startsWith(PREFIX_GLB_B64) -> {
                    val bytes = decodeBase64(encoded.removePrefix(PREFIX_GLB_B64))
                    toGlbOrNull(modelId, bytes, "GLB_B64")
                }

                encoded.startsWith(PREFIX_SWX3D_GZ) -> {
                    val json = gunzipIfNeeded(decodeBase64(encoded.removePrefix(PREFIX_SWX3D_GZ)))
                        .toString(Charsets.UTF_8)
                    val mesh = meshAdapter.fromJson(json)
                    mesh?.let {
                        Decoded3DModel.ProceduralMesh(modelId, it, "SWX3D_GZ")
                    }
                }

                encoded.startsWith(PREFIX_SWX3D) -> {
                    val json = decodeBase64(encoded.removePrefix(PREFIX_SWX3D)).toString(Charsets.UTF_8)
                    val mesh = meshAdapter.fromJson(json)
                    mesh?.let {
                        Decoded3DModel.ProceduralMesh(modelId, it, "SWX3D")
                    }
                }

                encoded.startsWith("{") -> {
                    val mesh = meshAdapter.fromJson(encoded)
                    mesh?.let {
                        Decoded3DModel.ProceduralMesh(modelId, it, "JSON_MESH")
                    }
                }

                else -> {
                    // Auto-detect raw Base64 payload (either GZIP-wrapped or raw GLB / JSON)
                    val rawBytes = gunzipIfNeeded(decodeBase64(encoded))
                    toGlbOrNull(modelId, rawBytes, "AUTO_GLB_B64")
                        ?: rawBytes.toString(Charsets.UTF_8).trim().let { text ->
                            if (text.startsWith("{")) {
                                meshAdapter.fromJson(text)?.let {
                                    Decoded3DModel.ProceduralMesh(modelId, it, "AUTO_MESH_B64")
                                }
                            } else {
                                null
                            }
                        }
                }
            }
        }.getOrNull()

        return parsed ?: Decoded3DModel.ProceduralMesh(
            modelId = modelId,
            mesh = buildVehicle3DWireframe(vehicleClass),
            sourceFormat = "PARAMETRIC_FALLBACK"
        )
    }

    private fun toGlbOrNull(
        modelId: String,
        bytes: ByteArray,
        format: String
    ): Decoded3DModel.GlbBuffer? {
        if (!isGlbHeader(bytes)) return null
        val direct = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put(bytes)
                rewind()
            }
        return Decoded3DModel.GlbBuffer(
            modelId = modelId,
            buffer = direct,
            byteSize = bytes.size,
            sourceFormat = format
        )
    }

    private fun isGlbHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 12 &&
            bytes[0] == 0x67.toByte() && // 'g'
            bytes[1] == 0x6C.toByte() && // 'l'
            bytes[2] == 0x54.toByte() && // 'T'
            bytes[3] == 0x46.toByte()    // 'F'
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzipIfNeeded(bytes: ByteArray): ByteArray {
        if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
                return gzip.readBytes()
            }
        }
        return bytes
    }

    private fun encodeBase64(bytes: ByteArray): String = try {
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Throwable) {
        java.util.Base64.getEncoder().encodeToString(bytes)
    }

    private fun decodeBase64(text: String): ByteArray = try {
        Base64.decode(text, Base64.DEFAULT)
    } catch (_: Throwable) {
        java.util.Base64.getDecoder().decode(text.replace("\n", "").replace("\r", ""))
    }
}

fun buildVehicle3DWireframe(vehicleClass: VehicleClass): WireframeMesh {
    val segments = mutableListOf<Segment3D>()
    val panels = mutableListOf<Panel3D>()

    fun addBox(
        minX: Float, maxX: Float,
        minY: Float, maxY: Float,
        minZ: Float, maxZ: Float,
        role: SegmentRole,
        addSolidPanel: Boolean = false
    ) {
        val p000 = HotspotPoint(minX, minY, minZ)
        val p100 = HotspotPoint(maxX, minY, minZ)
        val p110 = HotspotPoint(maxX, maxY, minZ)
        val p010 = HotspotPoint(minX, maxY, minZ)
        val p001 = HotspotPoint(minX, minY, maxZ)
        val p101 = HotspotPoint(maxX, minY, maxZ)
        val p111 = HotspotPoint(maxX, maxY, maxZ)
        val p011 = HotspotPoint(minX, maxY, maxZ)

        val edges = listOf(
            p000 to p100, p100 to p110, p110 to p010, p010 to p000,
            p001 to p101, p101 to p111, p111 to p011, p011 to p001,
            p000 to p001, p100 to p101, p110 to p111, p010 to p011
        )
        edges.forEach { (a, b) -> segments.add(Segment3D(a, b, role)) }

        if (addSolidPanel) {
            panels.add(Panel3D(listOf(p010, p110, p111, p011)))
            panels.add(Panel3D(listOf(p000, p100, p110, p010)))
            panels.add(Panel3D(listOf(p001, p101, p111, p011)))
        }
    }

    fun addWheel(
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        radius: Float,
        halfWidth: Float,
        role: SegmentRole,
        withMotorCore: Boolean = false
    ) {
        val steps = 16
        val leftPts = mutableListOf<HotspotPoint>()
        val rightPts = mutableListOf<HotspotPoint>()
        for (i in 0 until steps) {
            val angle = (2.0 * PI * i / steps).toFloat()
            val x = centerX + cos(angle) * radius
            val y = centerY + sin(angle) * radius
            leftPts.add(HotspotPoint(x, y, centerZ - halfWidth))
            rightPts.add(HotspotPoint(x, y, centerZ + halfWidth))
        }
        for (i in 0 until steps) {
            val next = (i + 1) % steps
            segments.add(Segment3D(leftPts[i], leftPts[next], role))
            segments.add(Segment3D(rightPts[i], rightPts[next], role))
            if (i % 2 == 0) {
                segments.add(Segment3D(leftPts[i], rightPts[i], role))
            }
            if (i % 4 == 0) {
                val hubL = HotspotPoint(centerX, centerY, centerZ - halfWidth * 0.5f)
                val hubR = HotspotPoint(centerX, centerY, centerZ + halfWidth * 0.5f)
                segments.add(Segment3D(hubL, leftPts[i], if (withMotorCore) SegmentRole.MOTOR_HUB else role))
                segments.add(Segment3D(hubR, rightPts[i], if (withMotorCore) SegmentRole.MOTOR_HUB else role))
            }
        }
        if (withMotorCore) {
            val coreR = radius * 0.45f
            for (i in 0 until 8) {
                val a1 = (2.0 * PI * i / 8).toFloat()
                val a2 = (2.0 * PI * ((i + 1) % 8) / 8).toFloat()
                segments.add(
                    Segment3D(
                        HotspotPoint(centerX + cos(a1) * coreR, centerY + sin(a1) * coreR, centerZ),
                        HotspotPoint(centerX + cos(a2) * coreR, centerY + sin(a2) * coreR, centerZ),
                        SegmentRole.MOTOR_HUB
                    )
                )
            }
        }
    }

    when (vehicleClass) {
        VehicleClass.SCOOTER -> {
            addBox(-0.36f, 0.34f, 0.14f, 0.25f, -0.11f, 0.11f, SegmentRole.CHASSIS, addSolidPanel = true)
            addBox(-0.26f, 0.24f, 0.16f, 0.23f, -0.07f, 0.07f, SegmentRole.BATTERY_CORE)
            segments.add(Segment3D(HotspotPoint(0.32f, 0.22f, -0.05f), HotspotPoint(0.46f, 0.42f, 0f), SegmentRole.CHASSIS))
            segments.add(Segment3D(HotspotPoint(0.32f, 0.22f, 0.05f), HotspotPoint(0.46f, 0.42f, 0f), SegmentRole.CHASSIS))
            segments.add(Segment3D(HotspotPoint(0.56f, 0.20f, -0.06f), HotspotPoint(0.46f, 0.48f, -0.04f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(0.56f, 0.20f, 0.06f), HotspotPoint(0.46f, 0.48f, 0.04f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(0.46f, 0.42f, 0f), HotspotPoint(0.40f, 0.92f, 0f), SegmentRole.CHASSIS))
            segments.add(Segment3D(HotspotPoint(0.40f, 0.92f, -0.26f), HotspotPoint(0.40f, 0.92f, 0.26f), SegmentRole.CHASSIS))
            addBox(0.38f, 0.45f, 0.90f, 0.96f, -0.06f, 0.06f, SegmentRole.BATTERY_CORE)
            segments.add(Segment3D(HotspotPoint(-0.36f, 0.20f, -0.07f), HotspotPoint(-0.58f, 0.19f, -0.07f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(-0.36f, 0.20f, 0.07f), HotspotPoint(-0.58f, 0.19f, 0.07f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(-0.34f, 0.26f, 0f), HotspotPoint(-0.54f, 0.36f, 0f), SegmentRole.CHASSIS))
            addWheel(-0.58f, 0.19f, 0f, 0.19f, 0.055f, SegmentRole.WHEEL, withMotorCore = true)
            addWheel(0.56f, 0.19f, 0f, 0.19f, 0.05f, SegmentRole.WHEEL, withMotorCore = false)
        }

        VehicleClass.E_BIKE -> {
            addWheel(-0.64f, 0.30f, 0f, 0.30f, 0.06f, SegmentRole.WHEEL, withMotorCore = true)
            addWheel(0.64f, 0.30f, 0f, 0.30f, 0.06f, SegmentRole.WHEEL, withMotorCore = false)
            val bb = HotspotPoint(-0.10f, 0.28f, 0f)
            val seatCluster = HotspotPoint(-0.24f, 0.66f, 0f)
            val headTop = HotspotPoint(0.42f, 0.68f, 0f)
            val headBot = HotspotPoint(0.45f, 0.56f, 0f)
            val rearAxleL = HotspotPoint(-0.64f, 0.30f, -0.05f)
            val rearAxleR = HotspotPoint(-0.64f, 0.30f, 0.05f)

            segments.add(Segment3D(bb, seatCluster, SegmentRole.CHASSIS))
            segments.add(Segment3D(seatCluster, headTop, SegmentRole.CHASSIS))
            segments.add(Segment3D(headTop, headBot, SegmentRole.CHASSIS))
            segments.add(Segment3D(headBot, bb, SegmentRole.CHASSIS))
            addBox(0.06f, 0.26f, 0.36f, 0.50f, -0.06f, 0.06f, SegmentRole.BATTERY_CORE, addSolidPanel = true)
            segments.add(Segment3D(bb, rearAxleL, SegmentRole.CHASSIS))
            segments.add(Segment3D(bb, rearAxleR, SegmentRole.CHASSIS))
            segments.add(Segment3D(seatCluster, rearAxleL, SegmentRole.SUSPENSION))
            segments.add(Segment3D(seatCluster, rearAxleR, SegmentRole.SUSPENSION))
            segments.add(Segment3D(headBot, HotspotPoint(0.64f, 0.30f, -0.05f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(headBot, HotspotPoint(0.64f, 0.30f, 0.05f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(headTop, HotspotPoint(0.46f, 0.85f, 0f), SegmentRole.CHASSIS))
            segments.add(Segment3D(HotspotPoint(0.46f, 0.85f, -0.28f), HotspotPoint(0.46f, 0.85f, 0.28f), SegmentRole.CHASSIS))
            addBox(-0.34f, -0.18f, 0.72f, 0.77f, -0.07f, 0.07f, SegmentRole.CHASSIS, addSolidPanel = true)
            segments.add(Segment3D(seatCluster, HotspotPoint(-0.26f, 0.73f, 0f), SegmentRole.CHASSIS))
        }

        VehicleClass.BICYCLE -> {
            addWheel(-0.63f, 0.32f, 0f, 0.32f, 0.03f, SegmentRole.WHEEL, withMotorCore = false)
            addWheel(0.63f, 0.32f, 0f, 0.32f, 0.03f, SegmentRole.WHEEL, withMotorCore = false)
            val bb = HotspotPoint(-0.08f, 0.28f, 0f)
            val seatCluster = HotspotPoint(-0.22f, 0.68f, 0f)
            val headTop = HotspotPoint(0.42f, 0.70f, 0f)
            val headBot = HotspotPoint(0.45f, 0.58f, 0f)
            val rearAxleL = HotspotPoint(-0.63f, 0.32f, -0.035f)
            val rearAxleR = HotspotPoint(-0.63f, 0.32f, 0.035f)
            segments.add(Segment3D(bb, seatCluster, SegmentRole.CHASSIS))
            segments.add(Segment3D(seatCluster, headTop, SegmentRole.CHASSIS))
            segments.add(Segment3D(headTop, headBot, SegmentRole.CHASSIS))
            segments.add(Segment3D(headBot, bb, SegmentRole.CHASSIS))
            segments.add(Segment3D(bb, rearAxleL, SegmentRole.CHASSIS))
            segments.add(Segment3D(bb, rearAxleR, SegmentRole.CHASSIS))
            segments.add(Segment3D(seatCluster, rearAxleL, SegmentRole.CHASSIS))
            segments.add(Segment3D(seatCluster, rearAxleR, SegmentRole.CHASSIS))
            segments.add(Segment3D(headBot, HotspotPoint(0.63f, 0.32f, -0.035f), SegmentRole.CHASSIS))
            segments.add(Segment3D(headBot, HotspotPoint(0.63f, 0.32f, 0.035f), SegmentRole.CHASSIS))
            segments.add(Segment3D(headTop, HotspotPoint(0.48f, 0.80f, 0f), SegmentRole.CHASSIS))
            segments.add(Segment3D(HotspotPoint(0.48f, 0.80f, -0.22f), HotspotPoint(0.48f, 0.80f, 0.22f), SegmentRole.CHASSIS))
            addBox(-0.33f, -0.17f, 0.75f, 0.79f, -0.055f, 0.055f, SegmentRole.CHASSIS, addSolidPanel = true)
            segments.add(Segment3D(seatCluster, HotspotPoint(-0.25f, 0.76f, 0f), SegmentRole.CHASSIS))
        }

        VehicleClass.MOTO -> {
            addWheel(-0.62f, 0.29f, 0f, 0.29f, 0.065f, SegmentRole.WHEEL, withMotorCore = false)
            addWheel(0.62f, 0.29f, 0f, 0.29f, 0.06f, SegmentRole.WHEEL, withMotorCore = false)
            addBox(-0.08f, 0.24f, 0.34f, 0.62f, -0.10f, 0.10f, SegmentRole.BATTERY_CORE, addSolidPanel = true)
            addBox(-0.22f, -0.04f, 0.24f, 0.38f, -0.09f, 0.09f, SegmentRole.MOTOR_HUB)
            addBox(-0.48f, 0.08f, 0.68f, 0.75f, -0.08f, 0.08f, SegmentRole.CHASSIS, addSolidPanel = true)
            segments.add(Segment3D(HotspotPoint(-0.18f, 0.32f, -0.07f), HotspotPoint(-0.62f, 0.29f, -0.06f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(-0.18f, 0.32f, 0.07f), HotspotPoint(-0.62f, 0.29f, 0.06f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(-0.12f, 0.66f, 0f), HotspotPoint(-0.44f, 0.44f, 0f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(0.36f, 0.74f, -0.07f), HotspotPoint(0.62f, 0.29f, -0.06f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(0.36f, 0.74f, 0.07f), HotspotPoint(0.62f, 0.29f, 0.06f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(0.36f, 0.74f, 0f), HotspotPoint(0.42f, 0.88f, 0f), SegmentRole.CHASSIS))
            segments.add(Segment3D(HotspotPoint(0.42f, 0.88f, -0.32f), HotspotPoint(0.42f, 0.88f, 0.32f), SegmentRole.CHASSIS))
        }

        VehicleClass.E_SKATE -> {
            addWheel(-0.46f, 0.10f, -0.18f, 0.10f, 0.04f, SegmentRole.WHEEL, withMotorCore = true)
            addWheel(-0.46f, 0.10f, 0.18f, 0.10f, 0.04f, SegmentRole.WHEEL, withMotorCore = true)
            addWheel(0.46f, 0.10f, -0.18f, 0.10f, 0.04f, SegmentRole.WHEEL, withMotorCore = false)
            addWheel(0.46f, 0.10f, 0.18f, 0.10f, 0.04f, SegmentRole.WHEEL, withMotorCore = false)
            // Sculpted longboard carbon deck + under-deck battery enclosure
            addBox(-0.60f, 0.60f, 0.18f, 0.23f, -0.16f, 0.16f, SegmentRole.CHASSIS, addSolidPanel = true)
            addBox(-0.36f, 0.32f, 0.11f, 0.18f, -0.11f, 0.11f, SegmentRole.BATTERY_CORE, addSolidPanel = true)
            addBox(-0.50f, -0.38f, 0.10f, 0.17f, -0.10f, 0.10f, SegmentRole.MOTOR_HUB)
            segments.add(Segment3D(HotspotPoint(-0.46f, 0.10f, -0.18f), HotspotPoint(-0.46f, 0.10f, 0.18f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(0.46f, 0.10f, -0.18f), HotspotPoint(0.46f, 0.10f, 0.18f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(-0.46f, 0.10f, 0f), HotspotPoint(-0.42f, 0.18f, 0f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(0.46f, 0.10f, 0f), HotspotPoint(0.42f, 0.18f, 0f), SegmentRole.SUSPENSION))
        }

        VehicleClass.ONEWHEEL -> {
            // Central wide high-torque hub wheel + front/rear footpads & fender rails
            addWheel(0f, 0.24f, 0f, 0.24f, 0.11f, SegmentRole.WHEEL, withMotorCore = true)
            addBox(-0.56f, -0.22f, 0.24f, 0.30f, -0.14f, 0.14f, SegmentRole.CHASSIS, addSolidPanel = true)
            addBox(0.22f, 0.56f, 0.24f, 0.30f, -0.14f, 0.14f, SegmentRole.CHASSIS, addSolidPanel = true)
            addBox(-0.52f, -0.24f, 0.18f, 0.24f, -0.10f, 0.10f, SegmentRole.BATTERY_CORE)
            addBox(0.24f, 0.50f, 0.18f, 0.24f, -0.10f, 0.10f, SegmentRole.MOTOR_HUB)
            segments.add(Segment3D(HotspotPoint(-0.56f, 0.27f, -0.14f), HotspotPoint(0.56f, 0.27f, -0.14f), SegmentRole.CHASSIS))
            segments.add(Segment3D(HotspotPoint(-0.56f, 0.27f, 0.14f), HotspotPoint(0.56f, 0.27f, 0.14f), SegmentRole.CHASSIS))
            segments.add(Segment3D(HotspotPoint(-0.22f, 0.30f, 0f), HotspotPoint(0f, 0.52f, 0f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(0.22f, 0.30f, 0f), HotspotPoint(0f, 0.52f, 0f), SegmentRole.SUSPENSION))
        }

        VehicleClass.MICRO_EV -> {
            addWheel(-0.56f, 0.20f, -0.34f, 0.20f, 0.05f, SegmentRole.WHEEL, withMotorCore = false)
            addWheel(-0.56f, 0.20f, 0.34f, 0.20f, 0.05f, SegmentRole.WHEEL, withMotorCore = false)
            addWheel(0.56f, 0.20f, -0.34f, 0.20f, 0.05f, SegmentRole.WHEEL, withMotorCore = true)
            addWheel(0.56f, 0.20f, 0.34f, 0.20f, 0.05f, SegmentRole.WHEEL, withMotorCore = true)
            addBox(-0.66f, 0.66f, 0.15f, 0.26f, -0.32f, 0.32f, SegmentRole.CHASSIS, addSolidPanel = true)
            addBox(-0.38f, 0.38f, 0.14f, 0.22f, -0.24f, 0.24f, SegmentRole.BATTERY_CORE)
            addBox(-0.66f, 0.66f, 0.26f, 0.56f, -0.34f, 0.34f, SegmentRole.CHASSIS, addSolidPanel = true)
            addBox(-0.46f, 0.42f, 0.92f, 0.96f, -0.30f, 0.30f, SegmentRole.CHASSIS)
            segments.add(Segment3D(HotspotPoint(-0.64f, 0.56f, -0.33f), HotspotPoint(-0.46f, 0.94f, -0.30f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(-0.64f, 0.56f, 0.33f), HotspotPoint(-0.46f, 0.94f, 0.30f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(0.64f, 0.56f, -0.33f), HotspotPoint(0.42f, 0.94f, -0.30f), SegmentRole.SUSPENSION))
            segments.add(Segment3D(HotspotPoint(0.64f, 0.56f, 0.33f), HotspotPoint(0.42f, 0.94f, 0.30f), SegmentRole.SUSPENSION))
        }
    }

    return WireframeMesh(segments = segments, solidPanels = panels)
}
