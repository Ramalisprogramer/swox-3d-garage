package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.HotspotPoint
import com.example.model.HotspotType
import com.example.model.RenderMode
import com.example.model.VehicleProfile
import com.example.model3d.Decoded3DModel
import com.example.model3d.Encoded3DModelCodec
import com.example.model3d.SegmentRole
import com.example.model3d.WireframeMesh
import com.example.model3d.buildVehicle3DWireframe
import com.example.ui.theme.CyanStroke35
import com.example.ui.theme.GlassContainerFill
import com.example.ui.theme.GlassDeepFill
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.OledCanvasBlack
import com.example.ui.theme.PurpleStroke35
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViewportGridLine
import com.example.viewmodel.CameraTransformState
import com.google.android.filament.LightManager
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val BASE_CAMERA_DISTANCE = 2.45f

@Composable
fun InteractiveVehicle3DView(
    vehicle: VehicleProfile,
    hasGlbAsset: Boolean,
    renderMode: RenderMode,
    cameraState: CameraTransformState,
    activeHotspot: HotspotType?,
    cameraResetToken: Int,
    onRenderModeSelected: (RenderMode) -> Unit,
    onCameraTransformChanged: (rotationAngleX: Float, rotationAngleY: Float, zoomScale: Float) -> Unit,
    onHotspotTapped: (HotspotType) -> Unit,
    onResetCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Independent X-axis (pitch, bounded -60°..+60°) and Y-axis (360° yaw) rotation + zoomScale (0.6f..2.2f)
    var rotationAngleX by remember(vehicle.id) { mutableFloatStateOf(cameraState.rotationAngleX) }
    var rotationAngleY by remember(vehicle.id) { mutableFloatStateOf(cameraState.rotationAngleY) }
    var zoomScale by remember(vehicle.id) { mutableFloatStateOf(cameraState.zoomScale) }

    var angularVelX by remember(vehicle.id) { mutableFloatStateOf(0f) }
    var angularVelY by remember(vehicle.id) { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Background runtime decoder for text-encoded 3D models (`skutur 3D modeli`)
    val decoded3DModel by produceState<Decoded3DModel?>(
        initialValue = null,
        key1 = vehicle.id,
        key2 = vehicle.encoded3dModel
    ) {
        value = Encoded3DModelCodec.decode(vehicle)
    }

    LaunchedEffect(cameraResetToken) {
        if (cameraResetToken > 0) {
            angularVelX = 0f
            angularVelY = 0f
            rotationAngleX = CameraTransformState.INITIAL_PITCH_DEG
            rotationAngleY = CameraTransformState.INITIAL_YAW_DEG
            zoomScale = CameraTransformState.INITIAL_ZOOM_SCALE
        }
    }

    // Smooth inertial damping loop after single-finger drag release across both X and Y axes
    LaunchedEffect(isDragging, angularVelX, angularVelY) {
        if (!isDragging && (abs(angularVelX) > 0.02f || abs(angularVelY) > 0.02f)) {
            while (!isDragging && (abs(angularVelX) > 0.02f || abs(angularVelY) > 0.02f)) {
                withFrameNanos {
                    rotationAngleY = (rotationAngleY + angularVelY) % 360f
                    rotationAngleX = (rotationAngleX + angularVelX).coerceIn(
                        CameraTransformState.MIN_PITCH_DEG,
                        CameraTransformState.MAX_PITCH_DEG
                    )
                    angularVelY *= 0.91f
                    angularVelX *= 0.91f
                }
            }
            angularVelX = 0f
            angularVelY = 0f
            onCameraTransformChanged(rotationAngleX, rotationAngleY, zoomScale)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "hotspot_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )
    val scanlinePhase by pulseTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanline_phase"
    )

    val decodedGlb = decoded3DModel as? Decoded3DModel.GlbBuffer
    val isFilamentStageActive = hasGlbAsset || decodedGlb != null
    val activeProceduralMesh = (decoded3DModel as? Decoded3DModel.ProceduralMesh)?.mesh
        ?: remember(vehicle.id, vehicle.vehicleClass) { buildVehicle3DWireframe(vehicle.vehicleClass) }

    val stageBadgeRes = when {
        hasGlbAsset -> R.string.viewport_glb_active_badge
        decodedGlb != null -> R.string.viewport_decoded_glb_badge
        vehicle.encoded3dModel.isNotBlank() && decoded3DModel is Decoded3DModel.ProceduralMesh ->
            R.string.viewport_decoded_stream_badge
        else -> R.string.viewport_wireframe_fallback_badge
    }

    GlassmorphicContainer(
        modifier = modifier.testTag("vehicle_3d_viewport"),
        shape = RoundedCornerShape(24.dp),
        containerColor = OledCanvasBlack
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0E1320),
                            OledCanvasBlack
                        )
                    )
                )
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            // Volumetric dual-rim lighting aura (#00F0FF left rim, #BD00FF right rim)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = if (renderMode == RenderMode.WIREFRAME_XRAY) 0.22f else 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.44f),
                        radius = size.maxDimension * 0.52f
                    ),
                    center = Offset(size.width * 0.12f, size.height * 0.44f),
                    radius = size.maxDimension * 0.52f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = if (renderMode == RenderMode.WIREFRAME_XRAY) 0.22f else 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.44f),
                        radius = size.maxDimension * 0.52f
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.44f),
                    radius = size.maxDimension * 0.52f
                )
            }

            if (isFilamentStageActive) {
                SceneViewGlbStage(
                    vehicle = vehicle,
                    decodedGlbBuffer = decodedGlb?.buffer,
                    renderMode = renderMode,
                    cameraResetToken = cameraResetToken,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Wireframe3DFallbackStage(
                    vehicleId = vehicle.id,
                    wireframeMesh = activeProceduralMesh,
                    renderMode = renderMode,
                    rotationAngleX = rotationAngleX,
                    rotationAngleY = rotationAngleY,
                    zoomScale = zoomScale,
                    scanlinePhase = scanlinePhase,
                    onDragStateChange = { dragging ->
                        isDragging = dragging
                        if (!dragging) {
                            onCameraTransformChanged(rotationAngleX, rotationAngleY, zoomScale)
                        }
                    },
                    onOrbitDrag = { panX, panY ->
                        val sensitivity = 0.36f
                        // Y-axis horizontal 360° yaw
                        val deltaY = panX * sensitivity
                        // X-axis vertical pitch strictly clamped to -60°..+60°
                        val deltaX = -panY * sensitivity * 0.72f
                        rotationAngleY = (rotationAngleY + deltaY) % 360f
                        rotationAngleX = (rotationAngleX + deltaX).coerceIn(
                            CameraTransformState.MIN_PITCH_DEG,
                            CameraTransformState.MAX_PITCH_DEG
                        )
                        angularVelY = deltaY * 0.65f
                        angularVelX = deltaX * 0.65f
                    },
                    onPinchZoom = { zoomChange ->
                        zoomScale = (zoomScale * zoomChange).coerceIn(
                            CameraTransformState.MIN_ZOOM_SCALE,
                            CameraTransformState.MAX_ZOOM_SCALE
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Synchronized 3D Hotspot Reprojection using both rotationAngleX and rotationAngleY
            val hotspotEntries = remember(vehicle.id) {
                listOf(
                    HotspotType.BATTERY_PACK to vehicle.hotspots.battery,
                    HotspotType.REAR_MOTOR to vehicle.hotspots.motor,
                    HotspotType.STEM_CONTROLLER to vehicle.hotspots.controller
                )
            }

            hotspotEntries.forEach { (type, point) ->
                val projected = project3DPointWithMatrix(
                    point = point,
                    rotationAngleX = rotationAngleX,
                    rotationAngleY = rotationAngleY,
                    zoomScale = zoomScale,
                    viewportWidth = widthPx,
                    viewportHeight = heightPx
                )
                if (projected != null) {
                    val isSelected = activeHotspot == type
                    val pinColor = when (type) {
                        HotspotType.BATTERY_PACK -> NeonCyan
                        HotspotType.REAR_MOTOR -> NeonPurple
                        HotspotType.STEM_CONTROLLER -> NeonCyan
                    }
                    val pinLabel = when (type) {
                        HotspotType.BATTERY_PACK -> stringResource(R.string.hotspot_battery)
                        HotspotType.REAR_MOTOR -> stringResource(R.string.hotspot_motor)
                        HotspotType.STEM_CONTROLLER -> stringResource(R.string.hotspot_controller)
                    }
                    HotspotPinOverlay(
                        screenPos = Offset(projected.screenX, projected.screenY),
                        label = pinLabel,
                        accentColor = pinColor,
                        isSelected = isSelected,
                        pulseScale = pulseScale,
                        pulseAlpha = pulseAlpha,
                        testTag = "hotspot_pin_${type.name.lowercase()}",
                        onClick = { onHotspotTapped(type) }
                    )
                }
            }

            // Top overlay: Decoded/GLB Stage badge + Camera Reset pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = GlassDeepFill,
                    border = BorderStroke(
                        1.dp,
                        if (isFilamentStageActive || vehicle.encoded3dModel.isNotBlank()) CyanStroke35 else PurpleStroke35
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ViewInAr,
                            contentDescription = null,
                            tint = if (isFilamentStageActive) NeonCyan else NeonPurple,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(stageBadgeRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontSize = 9.sp
                        )
                    }
                }

                Surface(
                    onClick = onResetCamera,
                    shape = RoundedCornerShape(50),
                    color = GlassDeepFill,
                    border = BorderStroke(1.dp, CyanStroke35),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reset_camera_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = stringResource(R.string.cd_reset_camera),
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.viewport_reset_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // Bottom overlay: Render Mode Toggles + Dual-axis telemetry hint
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GlassDeepFill)
                        .border(1.dp, Brush.horizontalGradient(listOf(CyanStroke35, PurpleStroke35)), RoundedCornerShape(50))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RenderModePill(
                        label = stringResource(R.string.mode_standard_pbr),
                        selected = renderMode == RenderMode.STANDARD_PBR,
                        activeColor = NeonCyan,
                        testTag = "render_mode_pbr",
                        onClick = { onRenderModeSelected(RenderMode.STANDARD_PBR) }
                    )
                    RenderModePill(
                        label = stringResource(R.string.mode_wireframe_xray),
                        selected = renderMode == RenderMode.WIREFRAME_XRAY,
                        activeColor = NeonPurple,
                        testTag = "render_mode_xray",
                        onClick = { onRenderModeSelected(RenderMode.WIREFRAME_XRAY) }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.viewport_hint_gestures),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.75f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun SceneViewGlbStage(
    vehicle: VehicleProfile,
    decodedGlbBuffer: ByteBuffer?,
    renderMode: RenderMode,
    cameraResetToken: Int,
    modifier: Modifier = Modifier
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val homePosition = remember { Position(x = -1.35f, y = 1.05f, z = 1.85f) }
    val targetPosition = remember { Position(x = 0.0f, y = 0.35f, z = 0.0f) }

    val cameraNode = rememberCameraNode(engine) {
        position = homePosition
        lookAt(targetPosition)
    }

    LaunchedEffect(cameraResetToken) {
        if (cameraResetToken > 0) {
            cameraNode.position = homePosition
            cameraNode.lookAt(targetPosition)
        }
    }

    val modelInstance = remember(vehicle.modelAssetPath, decodedGlbBuffer, modelLoader) {
        when {
            decodedGlbBuffer != null -> {
                modelLoader.createModelInstance(buffer = decodedGlbBuffer.duplicate().apply { rewind() })
            }
            vehicle.modelAssetPath.isNotBlank() -> {
                modelLoader.createModelInstance(assetFileLocation = vehicle.modelAssetPath)
            }
            else -> null
        }
    }

    val nodes = rememberNodes {
        add(
            LightNode(
                engine = engine,
                type = LightManager.Type.POINT,
                apply = {
                    color(0.0f, 0.94f, 1.0f)
                    intensity(75_000f)
                    falloff(6.0f)
                }
            ).apply {
                position = Position(x = -1.8f, y = 0.9f, z = -0.6f)
            }
        )
        add(
            LightNode(
                engine = engine,
                type = LightManager.Type.POINT,
                apply = {
                    color(0.74f, 0.0f, 1.0f)
                    intensity(75_000f)
                    falloff(6.0f)
                }
            ).apply {
                position = Position(x = 1.8f, y = 0.9f, z = -0.6f)
            }
        )
    }

    LaunchedEffect(modelInstance, renderMode) {
        nodes.filterIsInstance<ModelNode>().forEach { existing ->
            nodes.remove(existing)
        }
        val instance = modelInstance ?: return@LaunchedEffect
        val modelNode = ModelNode(
            modelInstance = instance,
            scaleToUnits = 1.6f,
            autoAnimate = true
        ).apply {
            centerOrigin(Position(x = 0f, y = -0.35f, z = 0f))
        }
        if (renderMode == RenderMode.WIREFRAME_XRAY) {
            modelNode.materialInstances.flatten().forEach { material ->
                material.setParameter("baseColorFactor", 0.0f, 0.85f, 1.0f, 0.72f)
            }
        }
        nodes.add(modelNode)
    }

    Scene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        isOpaque = false,
        cameraNode = cameraNode,
        cameraManipulator = rememberCameraManipulator(
            orbitHomePosition = homePosition,
            targetPosition = targetPosition
        ),
        mainLightNode = rememberMainLightNode(engine) {
            intensity = if (renderMode == RenderMode.STANDARD_PBR) 95_000f else 35_000f
        },
        childNodes = nodes
    )
}

@Composable
private fun Wireframe3DFallbackStage(
    vehicleId: String,
    wireframeMesh: WireframeMesh,
    renderMode: RenderMode,
    rotationAngleX: Float,
    rotationAngleY: Float,
    zoomScale: Float,
    scanlinePhase: Float,
    onDragStateChange: (Boolean) -> Unit,
    onOrbitDrag: (panX: Float, panY: Float) -> Unit,
    onPinchZoom: (zoomFactor: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .pointerInput(vehicleId) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onDragStateChange(true)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (abs(zoomChange - 1f) > 0.002f) {
                                onPinchZoom(zoomChange)
                            }
                            if (event.changes.count { it.pressed } == 1 &&
                                (abs(panChange.x) > 0.2f || abs(panChange.y) > 0.2f)
                            ) {
                                onOrbitDrag(panChange.x, panChange.y)
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    onDragStateChange(false)
                }
            }
    ) {
        val w = size.width
        val h = size.height

        drawReflectiveFloorGrid(
            rotationAngleX = rotationAngleX,
            rotationAngleY = rotationAngleY,
            zoomScale = zoomScale,
            w = w,
            h = h,
            isXray = renderMode == RenderMode.WIREFRAME_XRAY
        )

        val projectedSegments = wireframeMesh.segments.mapNotNull { seg ->
            val p1 = project3DPointWithMatrix(seg.start, rotationAngleX, rotationAngleY, zoomScale, w, h)
            val p2 = project3DPointWithMatrix(seg.end, rotationAngleX, rotationAngleY, zoomScale, w, h)
            if (p1 != null && p2 != null) {
                ProjectedSegment(
                    x1 = p1.screenX,
                    y1 = p1.screenY,
                    x2 = p2.screenX,
                    y2 = p2.screenY,
                    avgDepth = (p1.depth + p2.depth) * 0.5f,
                    role = seg.role
                )
            } else {
                null
            }
        }.sortedByDescending { it.avgDepth }

        if (renderMode == RenderMode.STANDARD_PBR) {
            wireframeMesh.solidPanels.forEach { panel ->
                val pts = panel.points.mapNotNull {
                    project3DPointWithMatrix(it, rotationAngleX, rotationAngleY, zoomScale, w, h)
                }
                if (pts.size >= 3) {
                    val path = Path().apply {
                        moveTo(pts[0].screenX, pts[0].screenY)
                        for (i in 1 until pts.size) lineTo(pts[i].screenX, pts[i].screenY)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xCC181C28),
                                Color(0xB310121C)
                            )
                        )
                    )
                }
            }
        }

        projectedSegments.forEach { seg ->
            val baseColor = when (seg.role) {
                SegmentRole.CHASSIS -> if (renderMode == RenderMode.WIREFRAME_XRAY) NeonCyan else Color(0xFF8CDCE8)
                SegmentRole.SUSPENSION -> NeonPurple
                SegmentRole.BATTERY_CORE -> NeonCyan
                SegmentRole.MOTOR_HUB -> NeonPurple
                SegmentRole.WHEEL -> if (renderMode == RenderMode.WIREFRAME_XRAY) Color(0xFF4BE8FF) else Color(0xFF6E7A94)
            }
            val strokeWidth = when (seg.role) {
                SegmentRole.BATTERY_CORE, SegmentRole.MOTOR_HUB -> 3.4f
                SegmentRole.CHASSIS -> 2.8f
                else -> 2.0f
            }

            if (renderMode == RenderMode.WIREFRAME_XRAY) {
                drawLine(
                    color = baseColor.copy(alpha = 0.22f),
                    start = Offset(seg.x1, seg.y1),
                    end = Offset(seg.x2, seg.y2),
                    strokeWidth = strokeWidth * 2.8f,
                    cap = StrokeCap.Round
                )
            }
            drawLine(
                color = baseColor.copy(alpha = if (renderMode == RenderMode.WIREFRAME_XRAY) 0.95f else 0.78f),
                start = Offset(seg.x1, seg.y1),
                end = Offset(seg.x2, seg.y2),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        if (renderMode == RenderMode.WIREFRAME_XRAY) {
            val sweepX = w * 0.5f + scanlinePhase * w * 0.34f
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        NeonCyan.copy(alpha = 0.55f),
                        NeonPurple.copy(alpha = 0.45f),
                        Color.Transparent
                    )
                ),
                start = Offset(sweepX, h * 0.14f),
                end = Offset(sweepX, h * 0.84f),
                strokeWidth = 2.5f
            )
        }
    }
}

private fun DrawScope.drawReflectiveFloorGrid(
    rotationAngleX: Float,
    rotationAngleY: Float,
    zoomScale: Float,
    w: Float,
    h: Float,
    isXray: Boolean
) {
    val gridSpan = 1.6f
    val step = 0.4f
    val lineColor = if (isXray) NeonCyan.copy(alpha = 0.18f) else ViewportGridLine.copy(alpha = 0.85f)
    val accentLineColor = if (isXray) NeonPurple.copy(alpha = 0.28f) else NeonCyan.copy(alpha = 0.22f)

    val centerProj = project3DPointWithMatrix(HotspotPoint(0f, 0f, 0f), rotationAngleX, rotationAngleY, zoomScale, w, h)
    if (centerProj != null) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonCyan.copy(alpha = 0.16f),
                    NeonPurple.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(centerProj.screenX, centerProj.screenY),
                radius = w * 0.32f * zoomScale.coerceIn(0.75f, 1.4f)
            ),
            center = Offset(centerProj.screenX, centerProj.screenY),
            radius = w * 0.32f * zoomScale.coerceIn(0.75f, 1.4f)
        )
    }

    var v = -gridSpan
    while (v <= gridSpan + 0.001f) {
        val isCenter = abs(v) < 0.05f
        val color = if (isCenter) accentLineColor else lineColor

        val pStartX = project3DPointWithMatrix(HotspotPoint(-gridSpan, 0f, v), rotationAngleX, rotationAngleY, zoomScale, w, h)
        val pEndX = project3DPointWithMatrix(HotspotPoint(gridSpan, 0f, v), rotationAngleX, rotationAngleY, zoomScale, w, h)
        if (pStartX != null && pEndX != null) {
            drawLine(
                color = color,
                start = Offset(pStartX.screenX, pStartX.screenY),
                end = Offset(pEndX.screenX, pEndX.screenY),
                strokeWidth = if (isCenter) 2f else 1.1f
            )
        }

        val pStartZ = project3DPointWithMatrix(HotspotPoint(v, 0f, -gridSpan), rotationAngleX, rotationAngleY, zoomScale, w, h)
        val pEndZ = project3DPointWithMatrix(HotspotPoint(v, 0f, gridSpan), rotationAngleX, rotationAngleY, zoomScale, w, h)
        if (pStartZ != null && pEndZ != null) {
            drawLine(
                color = color,
                start = Offset(pStartZ.screenX, pStartZ.screenY),
                end = Offset(pEndZ.screenX, pEndZ.screenY),
                strokeWidth = if (isCenter) 2f else 1.1f
            )
        }
        v += step
    }
}

@Composable
private fun HotspotPinOverlay(
    screenPos: Offset,
    label: String,
    accentColor: Color,
    isSelected: Boolean,
    pulseScale: Float,
    pulseAlpha: Float,
    testTag: String,
    onClick: () -> Unit
) {
    val touchSizePx = with(LocalDensity.current) { 48.dp.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (screenPos.x - touchSizePx / 2f).roundToInt(),
                    y = (screenPos.y - touchSizePx / 2f).roundToInt()
                )
            }
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = accentColor.copy(alpha = pulseAlpha),
                radius = (size.minDimension * 0.24f) * pulseScale,
                style = Stroke(width = 2.dp.toPx())
            )
            if (isSelected) {
                drawCircle(
                    color = accentColor.copy(alpha = 0.28f),
                    radius = size.minDimension * 0.34f
                )
            }
            drawCircle(
                color = OledCanvasBlack,
                radius = 7.dp.toPx()
            )
            drawCircle(
                color = accentColor,
                radius = if (isSelected) 6.5.dp.toPx() else 5.dp.toPx()
            )
        }
    }

    AnimatedVisibility(
        visible = isSelected,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
        modifier = Modifier.offset {
            IntOffset(
                x = (screenPos.x + 18f).roundToInt(),
                y = (screenPos.y - 34f).roundToInt()
            )
        }
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = GlassContainerFill,
            border = BorderStroke(1.dp, accentColor)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun RenderModePill(
    label: String,
    selected: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) activeColor.copy(alpha = 0.18f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, activeColor) else null,
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = stringResource(R.string.format_render_mode_pill, label),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) activeColor else TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

private data class ProjectedScreenPoint(
    val screenX: Float,
    val screenY: Float,
    val depth: Float
)

private data class ProjectedSegment(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val avgDepth: Float,
    val role: SegmentRole
)

/**
 * Projects a 3D mesh coordinate using real-time trigonometric rotation matrices
 * for both Y-axis horizontal orbit (radY) and X-axis vertical tilt (radX),
 * scaled by zoomScale (0.6f..2.2f).
 */
private fun project3DPointWithMatrix(
    point: HotspotPoint,
    rotationAngleX: Float,
    rotationAngleY: Float,
    zoomScale: Float,
    viewportWidth: Float,
    viewportHeight: Float
): ProjectedScreenPoint? {
    if (viewportWidth <= 0f || viewportHeight <= 0f) return null

    val x0 = point.x
    val y0 = point.y - 0.36f
    val z0 = point.z

    val radY = rotationAngleY * (PI / 180.0)
    val radX = rotationAngleX * (PI / 180.0)

    // 1. Y-Axis Horizontal Orbit Matrix (Yaw)
    val cosY = cos(radY).toFloat()
    val sinY = sin(radY).toFloat()
    val x1 = x0 * cosY - z0 * sinY
    val z1 = x0 * sinY + z0 * cosY

    // 2. X-Axis Vertical Tilt Matrix (Pitch, bounded -60°..+60°)
    val cosX = cos(radX).toFloat()
    val sinX = sin(radX).toFloat()
    val y2 = y0 * cosX - z1 * sinX
    val z2 = y0 * sinX + z1 * cosX

    val cameraDistance = BASE_CAMERA_DISTANCE / zoomScale.coerceIn(
        CameraTransformState.MIN_ZOOM_SCALE,
        CameraTransformState.MAX_ZOOM_SCALE
    )
    val depth = z2 + cameraDistance
    if (depth <= 0.25f) return null

    val focal = minOf(viewportWidth, viewportHeight) * 1.15f
    val screenX = viewportWidth * 0.5f + (x1 / depth) * focal
    val screenY = viewportHeight * 0.52f - (y2 / depth) * focal
    return ProjectedScreenPoint(screenX = screenX, screenY = screenY, depth = depth)
}
