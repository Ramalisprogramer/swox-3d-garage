package com.example.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TireRepair
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CatalogSyncStatus
import com.example.model.LockMethod
import com.example.model.SpecSection
import com.example.model.SpecTargetKey
import com.example.model.UnitSystem
import com.example.model.VehicleProfile
import com.example.ui.components.GarageCatalogPane
import com.example.ui.components.GarageProfileDialog
import com.example.ui.components.GarageSettingsSheet
import com.example.ui.components.GlassmorphicContainer
import com.example.ui.components.InteractiveVehicle3DView
import com.example.ui.components.VehicleSelectorSheet
import com.example.ui.components.iconForVehicleClass
import com.example.ui.theme.GlassContainerFill
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.OledCanvasBlack
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.GarageViewModel
import java.util.Locale
import kotlin.math.roundToInt

private sealed interface DatasheetGridItem {
    val gridKey: String

    data class SectionHeader(
        val section: SpecSection,
        val title: String,
        val accentColor: Color
    ) : DatasheetGridItem {
        override val gridKey: String = "header_${section.name}"
    }

    data class SpecCardItem(
        val targetKey: SpecTargetKey,
        val section: SpecSection,
        val label: String,
        val value: String,
        val subValue: String? = null,
        val icon: ImageVector,
        val accentColor: Color
    ) : DatasheetGridItem {
        override val gridKey: String = "spec_${targetKey.name}"
    }

    data class OverviewCardItem(
        val title: String,
        val description: String,
        val vehicleClassLabel: String
    ) : DatasheetGridItem {
        override val gridKey: String = "overview_description"
    }
}

@Composable
fun VehicleDatasheetScreen(
    viewModel: GarageViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showVehicleSelectorSheet by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()

    val selectedVehicle = uiState.selectedVehicle
    val datasheetItems = if (selectedVehicle != null) {
        buildDatasheetItems(
            vehicle = selectedVehicle,
            unitSystem = uiState.unitSystem
        )
    } else {
        emptyList()
    }

    // Auto-scroll to target datasheet card when a 3D hotspot pin is tapped
    LaunchedEffect(uiState.highlightPulseToken, uiState.highlightedSpecTarget) {
        val target = uiState.highlightedSpecTarget
        if (target != null && uiState.highlightPulseToken > 0) {
            val targetIndex = datasheetItems.indexOfFirst {
                it is DatasheetGridItem.SpecCardItem && it.targetKey == target
            }
            if (targetIndex >= 0) {
                gridState.animateScrollToItem(index = targetIndex)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(OledCanvasBlack),
        containerColor = OledCanvasBlack,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpandedScreen = maxWidth >= 840.dp
            val viewportHeight = (maxHeight * 0.44f).coerceIn(240.dp, 380.dp)

            if (!isExpandedScreen && uiState.isInspectingVehicle) {
                BackHandler {
                    viewModel.closeVehicleInspector()
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            } else if (uiState.errorMessage != null || uiState.errorMessageRes != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.errorMessage
                            ?: uiState.errorMessageRes?.let { stringResource(it) }
                            ?: stringResource(R.string.selector_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Top Header Bar
                    GarageTopHeader(
                        syncStatus = uiState.syncStatus,
                        showBackButton = !isExpandedScreen && uiState.isInspectingVehicle,
                        onBackClick = viewModel::closeVehicleInspector,
                        onProfileClick = { showProfileDialog = true },
                        onSettingsClick = { showSettingsSheet = true }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isExpandedScreen) {
                        // Canonical List-Detail Layout: Full Searchable Catalog + Live 3D Passport Inspector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            GarageCatalogPane(
                                vehicles = uiState.filteredVehicles,
                                totalCount = uiState.totalCachedCount,
                                categoryCounts = uiState.categoryCounts,
                                selectedVehicleId = selectedVehicle?.id,
                                searchQuery = uiState.searchQuery,
                                selectedCategory = uiState.selectedCategory,
                                unitSystem = uiState.unitSystem,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onCategorySelected = viewModel::selectCategory,
                                onVehicleSelected = { id -> viewModel.selectVehicle(id, openInspector = true) },
                                onLoadMore = viewModel::loadMoreVehicles,
                                highlightSelection = true,
                                modifier = Modifier
                                    .weight(0.35f)
                                    .fillMaxHeight()
                            )

                            if (selectedVehicle != null) {
                                Column(
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .fillMaxHeight()
                                ) {
                                    VehicleSelectorPill(
                                        vehicle = selectedVehicle,
                                        unitSystem = uiState.unitSystem,
                                        onClick = { showVehicleSelectorSheet = true }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        InteractiveVehicle3DView(
                                            vehicle = selectedVehicle,
                                            hasGlbAsset = uiState.hasGlbAsset,
                                            renderMode = uiState.renderMode,
                                            cameraState = uiState.cameraState,
                                            activeHotspot = uiState.activeHotspot,
                                            cameraResetToken = uiState.cameraResetToken,
                                            onRenderModeSelected = viewModel::setRenderMode,
                                            onCameraTransformChanged = viewModel::updateCameraTransform,
                                            onHotspotTapped = viewModel::onHotspotSelected,
                                            onResetCamera = viewModel::requestCameraReset,
                                            modifier = Modifier
                                                .weight(0.48f)
                                                .fillMaxHeight()
                                                .padding(bottom = 12.dp)
                                        )
                                        DatasheetGridSection(
                                            items = datasheetItems,
                                            gridState = gridState,
                                            highlightedSpecTarget = uiState.highlightedSpecTarget,
                                            modifier = Modifier
                                                .weight(0.52f)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!uiState.isInspectingVehicle || selectedVehicle == null) {
                        // Handheld Launch: Opens directly into the full searchable catalog list (no featured section)
                        GarageCatalogPane(
                            vehicles = uiState.filteredVehicles,
                            totalCount = uiState.totalCachedCount,
                            categoryCounts = uiState.categoryCounts,
                            selectedVehicleId = selectedVehicle?.id,
                            searchQuery = uiState.searchQuery,
                            selectedCategory = uiState.selectedCategory,
                            unitSystem = uiState.unitSystem,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onCategorySelected = viewModel::selectCategory,
                            onVehicleSelected = { id -> viewModel.selectVehicle(id, openInspector = true) },
                            onLoadMore = viewModel::loadMoreVehicles,
                            highlightSelection = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        // Handheld Inspector: Quick-Switch Pill + Top 3D Viewport + Bottom 2-Column Datasheet
                        VehicleSelectorPill(
                            vehicle = selectedVehicle,
                            unitSystem = uiState.unitSystem,
                            onClick = { showVehicleSelectorSheet = true }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        InteractiveVehicle3DView(
                            vehicle = selectedVehicle,
                            hasGlbAsset = uiState.hasGlbAsset,
                            renderMode = uiState.renderMode,
                            cameraState = uiState.cameraState,
                            activeHotspot = uiState.activeHotspot,
                            cameraResetToken = uiState.cameraResetToken,
                            onRenderModeSelected = viewModel::setRenderMode,
                            onCameraTransformChanged = viewModel::updateCameraTransform,
                            onHotspotTapped = viewModel::onHotspotSelected,
                            onResetCamera = viewModel::requestCameraReset,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(viewportHeight)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        DatasheetGridSection(
                            items = datasheetItems,
                            gridState = gridState,
                            highlightedSpecTarget = uiState.highlightedSpecTarget,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        GarageSettingsSheet(
            currentLanguage = uiState.language,
            currentUnitSystem = uiState.unitSystem,
            onLanguageSelected = viewModel::setLanguage,
            onUnitSystemSelected = viewModel::setUnitSystem,
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showVehicleSelectorSheet) {
        VehicleSelectorSheet(
            vehicles = uiState.filteredVehicles,
            totalCount = uiState.totalCachedCount,
            categoryCounts = uiState.categoryCounts,
            selectedVehicleId = selectedVehicle?.id,
            searchQuery = uiState.searchQuery,
            selectedCategory = uiState.selectedCategory,
            unitSystem = uiState.unitSystem,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onCategorySelected = viewModel::selectCategory,
            onVehicleSelected = viewModel::selectVehicle,
            onLoadMore = viewModel::loadMoreVehicles,
            onDismiss = { showVehicleSelectorSheet = false }
        )
    }

    if (showProfileDialog && selectedVehicle != null) {
        GarageProfileDialog(
            totalVehicles = uiState.totalCachedCount,
            activePassportCode = selectedVehicle.passportCode,
            activeVehicleName = selectedVehicle.name,
            syncStatus = uiState.syncStatus,
            onDismiss = { showProfileDialog = false }
        )
    }
}

@Composable
private fun GarageTopHeader(
    syncStatus: CatalogSyncStatus,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val syncDotColor = when (syncStatus) {
        CatalogSyncStatus.CLOUD_SYNCED -> StatusActiveGreen
        CatalogSyncStatus.SYNCING -> NeonCyan
        CatalogSyncStatus.OFFLINE_CACHE -> WarningAmber
    }
    val syncIcon = when (syncStatus) {
        CatalogSyncStatus.CLOUD_SYNCED -> Icons.Rounded.CloudDone
        CatalogSyncStatus.SYNCING -> Icons.Rounded.CloudSync
        CatalogSyncStatus.OFFLINE_CACHE -> Icons.Rounded.CloudOff
    }
    val syncText = when (syncStatus) {
        CatalogSyncStatus.CLOUD_SYNCED -> stringResource(R.string.sync_status_synced)
        CatalogSyncStatus.SYNCING -> stringResource(R.string.sync_status_syncing)
        CatalogSyncStatus.OFFLINE_CACHE -> stringResource(R.string.sync_status_cached)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        GlassmorphicContainer(
            shape = CircleShape,
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = if (showBackButton) onBackClick else onProfileClick)
                .testTag(if (showBackButton) "back_to_catalog_button" else "profile_button")
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (showBackButton) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Person,
                    contentDescription = stringResource(
                        if (showBackButton) R.string.cd_back_to_catalog else R.string.cd_profile_icon
                    ),
                    tint = NeonCyan,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.header_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = syncIcon,
                    contentDescription = null,
                    tint = syncDotColor,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = syncText,
                    style = MaterialTheme.typography.labelSmall,
                    color = syncDotColor,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp
                )
            }
        }

        GlassmorphicContainer(
            shape = CircleShape,
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onSettingsClick)
                .testTag("settings_button")
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_settings_icon),
                    tint = NeonPurple,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun VehicleSelectorPill(
    vehicle: VehicleProfile,
    unitSystem: UnitSystem,
    onClick: () -> Unit
) {
    val speedBadge = if (unitSystem == UnitSystem.METRIC) {
        stringResource(
            R.string.format_unit_value,
            vehicle.topSpeedKmh.roundToInt().toString(),
            stringResource(R.string.unit_kmh)
        )
    } else {
        stringResource(
            R.string.format_unit_value,
            (vehicle.topSpeedKmh * 0.621371).roundToInt().toString(),
            stringResource(R.string.unit_mph)
        )
    }

    GlassmorphicContainer(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag("vehicle_selector_pill")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                NeonCyan.copy(alpha = 0.22f),
                                NeonPurple.copy(alpha = 0.22f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForVehicleClass(vehicle.vehicleClass),
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vehicle.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.format_vehicle_meta,
                        vehicle.brand,
                        vehicle.passportCode,
                        speedBadge
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = stringResource(R.string.cd_vehicle_selector),
                tint = NeonCyan,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun DatasheetGridSection(
    items: List<DatasheetGridItem>,
    gridState: LazyGridState,
    highlightedSpecTarget: SpecTargetKey?,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "card_highlight_pulse")
    val highlightGlow by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "highlight_glow"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.datasheet_title),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                letterSpacing = 1.4.sp
            )
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .testTag("datasheet_grid"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)
        ) {
            items(
                items = items,
                key = { it.gridKey },
                span = { item ->
                    when (item) {
                        is DatasheetGridItem.SectionHeader -> GridItemSpan(2)
                        is DatasheetGridItem.OverviewCardItem -> GridItemSpan(2)
                        is DatasheetGridItem.SpecCardItem -> GridItemSpan(1)
                    }
                }
            ) { item ->
                when (item) {
                    is DatasheetGridItem.SectionHeader -> {
                        DatasheetSectionHeader(
                            title = item.title,
                            accentColor = item.accentColor
                        )
                    }

                    is DatasheetGridItem.OverviewCardItem -> {
                        DatasheetOverviewCard(item = item)
                    }

                    is DatasheetGridItem.SpecCardItem -> {
                        val isHighlighted = item.targetKey == highlightedSpecTarget
                        DatasheetSpecCard(
                            item = item,
                            isHighlighted = isHighlighted,
                            highlightIntensity = if (isHighlighted) highlightGlow else 0f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DatasheetSectionHeader(
    title: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DatasheetSpecCard(
    item: DatasheetGridItem.SpecCardItem,
    isHighlighted: Boolean,
    highlightIntensity: Float
) {
    val containerColor = if (isHighlighted) {
        NeonCyan.copy(alpha = 0.10f + 0.10f * highlightIntensity)
    } else {
        GlassContainerFill
    }

    GlassmorphicContainer(
        shape = RoundedCornerShape(24.dp),
        containerColor = containerColor,
        strokeWidth = if (isHighlighted) 2.dp else 1.dp,
        highlightProgress = highlightIntensity,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("spec_card_${item.targetKey.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (isHighlighted) NeonCyan else item.accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = item.value,
                style = MaterialTheme.typography.titleMedium,
                color = if (isHighlighted) NeonCyan else TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.subValue.isNullOrBlank()) {
                Text(
                    text = item.subValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DatasheetOverviewCard(
    item: DatasheetGridItem.OverviewCardItem
) {
    GlassmorphicContainer(
        shape = RoundedCornerShape(24.dp),
        containerColor = GlassContainerFill,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("spec_card_vehicle_overview")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = NeonPurple.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.65f))
                ) {
                    Text(
                        text = item.vehicleClassLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonPurple,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun buildDatasheetItems(
    vehicle: VehicleProfile,
    unitSystem: UnitSystem
): List<DatasheetGridItem> {
    val decimal0Format = stringResource(R.string.format_decimal_0)
    val decimal1Format = stringResource(R.string.format_decimal_1)

    val speedFormatted = if (unitSystem == UnitSystem.METRIC) {
        stringResource(
            R.string.format_unit_value,
            String.format(Locale.US, decimal0Format, vehicle.topSpeedKmh),
            stringResource(R.string.unit_kmh)
        )
    } else {
        stringResource(
            R.string.format_unit_value,
            String.format(Locale.US, decimal1Format, vehicle.topSpeedKmh * 0.621371),
            stringResource(R.string.unit_mph)
        )
    }

    val rangeFormatted = if (unitSystem == UnitSystem.METRIC) {
        stringResource(
            R.string.format_unit_value,
            String.format(Locale.US, decimal0Format, vehicle.rangeKm),
            stringResource(R.string.unit_km)
        )
    } else {
        stringResource(
            R.string.format_unit_value,
            String.format(Locale.US, decimal1Format, vehicle.rangeKm * 0.621371),
            stringResource(R.string.unit_mi)
        )
    }

    val weightFormatted = if (unitSystem == UnitSystem.METRIC) {
        stringResource(
            R.string.format_unit_value,
            String.format(Locale.US, decimal1Format, vehicle.weightKg),
            stringResource(R.string.unit_kg)
        )
    } else {
        stringResource(
            R.string.format_unit_value,
            String.format(Locale.US, decimal1Format, vehicle.weightKg * 2.20462),
            stringResource(R.string.unit_lbs)
        )
    }

    val lockText = when (vehicle.lockMethod) {
        LockMethod.KEY_IGNITION -> stringResource(R.string.lock_key_ignition)
        LockMethod.NFC_PIN -> stringResource(R.string.lock_nfc_pin)
        LockMethod.APP_BLE -> stringResource(R.string.lock_app_ble)
        LockMethod.SMART_FOB -> stringResource(R.string.lock_smart_fob)
    }

    val gpsAlarmText = when {
        vehicle.hasGpsTracker && vehicle.hasAlarmSystem -> stringResource(R.string.security_gps_alarm_both)
        vehicle.hasAlarmSystem -> stringResource(R.string.security_alarm_only)
        vehicle.hasGpsTracker -> stringResource(R.string.security_gps_only)
        else -> stringResource(R.string.security_basic)
    }

    val regenSub = if (vehicle.regenLevels > 0) {
        stringResource(R.string.spec_regen_label, vehicle.regenLevels)
    } else {
        stringResource(R.string.spec_mechanical_no_regen)
    }

    val classLabel = when (vehicle.vehicleClass) {
        com.example.model.VehicleClass.SCOOTER -> stringResource(R.string.category_scooter)
        com.example.model.VehicleClass.E_BIKE -> stringResource(R.string.category_ebike)
        com.example.model.VehicleClass.BICYCLE -> stringResource(R.string.category_bicycle)
        com.example.model.VehicleClass.MOTO -> stringResource(R.string.category_moto)
        com.example.model.VehicleClass.E_SKATE -> stringResource(R.string.category_eskate)
        com.example.model.VehicleClass.ONEWHEEL -> stringResource(R.string.category_onewheel)
        com.example.model.VehicleClass.MICRO_EV -> stringResource(R.string.category_micro)
    }

    val motorValue = if (vehicle.motorWatts > 0) {
        stringResource(R.string.format_motor_watts, vehicle.motorWatts)
    } else {
        stringResource(R.string.spec_mechanical_power)
    }
    val motorSub = if (vehicle.peakWatts > 0) {
        stringResource(R.string.format_peak_watts, vehicle.peakWatts)
    } else {
        stringResource(R.string.spec_human_drivetrain)
    }
    val formattedAh = if (vehicle.batteryCapacityAh % 1.0 == 0.0) {
        vehicle.batteryCapacityAh.toInt().toString()
    } else {
        String.format(Locale.US, decimal1Format, vehicle.batteryCapacityAh)
    }
    val batteryValue = if (vehicle.batteryVoltage > 0) {
        stringResource(R.string.format_battery_pack, vehicle.batteryVoltage, formattedAh)
    } else {
        stringResource(R.string.spec_mechanical_no_battery)
    }
    val batterySub = if (vehicle.wattHours > 0) {
        stringResource(R.string.format_watt_hours, vehicle.wattHours)
    } else {
        null
    }

    val overviewDescription = vehicle.description.ifBlank {
        stringResource(
            R.string.format_overview_fallback,
            vehicle.brand,
            vehicle.name,
            vehicle.releaseYear,
            vehicle.frameMaterial,
            vehicle.tireSize
        )
    }

    return listOf(
        // Section 0: VEHICLE OVERVIEW (`haqqında`)
        DatasheetGridItem.SectionHeader(
            section = SpecSection.OVERVIEW,
            title = stringResource(R.string.section_overview),
            accentColor = NeonCyan
        ),
        DatasheetGridItem.OverviewCardItem(
            title = stringResource(R.string.spec_vehicle_about),
            description = overviewDescription,
            vehicleClassLabel = classLabel
        ),

        // Section 1: CORE SPECS
        DatasheetGridItem.SectionHeader(
            section = SpecSection.CORE_SPECS,
            title = stringResource(R.string.section_core_specs),
            accentColor = NeonCyan
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.MOTOR_POWER,
            section = SpecSection.CORE_SPECS,
            label = stringResource(R.string.spec_motor_power),
            value = motorValue,
            subValue = motorSub,
            icon = Icons.Rounded.Bolt,
            accentColor = NeonPurple
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.BATTERY_PACK,
            section = SpecSection.CORE_SPECS,
            label = stringResource(R.string.spec_battery_pack),
            value = batteryValue,
            subValue = batterySub,
            icon = Icons.Rounded.BatteryChargingFull,
            accentColor = NeonCyan
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.TOP_SPEED,
            section = SpecSection.CORE_SPECS,
            label = stringResource(R.string.spec_top_speed),
            value = speedFormatted,
            icon = Icons.Rounded.Speed,
            accentColor = NeonCyan
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.RANGE,
            section = SpecSection.CORE_SPECS,
            label = stringResource(R.string.spec_range),
            value = rangeFormatted,
            icon = Icons.Rounded.Route,
            accentColor = NeonPurple
        ),

        // Section 2: PHYSICAL DATA
        DatasheetGridItem.SectionHeader(
            section = SpecSection.PHYSICAL_DATA,
            title = stringResource(R.string.section_physical_data),
            accentColor = NeonPurple
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.WEIGHT,
            section = SpecSection.PHYSICAL_DATA,
            label = stringResource(R.string.spec_weight),
            value = weightFormatted,
            icon = Icons.Rounded.Scale,
            accentColor = NeonCyan
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.TIRE_SIZE,
            section = SpecSection.PHYSICAL_DATA,
            label = stringResource(R.string.spec_tire_size),
            value = vehicle.tireSize,
            icon = Icons.Rounded.TireRepair,
            accentColor = NeonPurple
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.FRAME,
            section = SpecSection.PHYSICAL_DATA,
            label = stringResource(R.string.spec_frame),
            value = vehicle.frameMaterial,
            icon = Icons.Rounded.Build,
            accentColor = NeonCyan
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.IP_RATING,
            section = SpecSection.PHYSICAL_DATA,
            label = stringResource(R.string.spec_water_rating),
            value = vehicle.ipRating,
            icon = Icons.Rounded.WaterDrop,
            accentColor = NeonPurple
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.BRAKES,
            section = SpecSection.PHYSICAL_DATA,
            label = stringResource(R.string.spec_brakes),
            value = vehicle.brakeSystem,
            icon = Icons.Rounded.Shield,
            accentColor = NeonCyan
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.SUSPENSION,
            section = SpecSection.PHYSICAL_DATA,
            label = stringResource(R.string.spec_suspension),
            value = vehicle.suspension,
            icon = Icons.Rounded.Build,
            accentColor = NeonPurple
        ),

        // Section 3: SECURITY
        DatasheetGridItem.SectionHeader(
            section = SpecSection.SECURITY,
            title = stringResource(R.string.section_security),
            accentColor = NeonCyan
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.LOCK_AUTH,
            section = SpecSection.SECURITY,
            label = stringResource(R.string.spec_lock_method),
            value = lockText,
            icon = Icons.Rounded.Lock,
            accentColor = NeonCyan
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.GPS_ALARM,
            section = SpecSection.SECURITY,
            label = stringResource(R.string.spec_gps_alarm),
            value = gpsAlarmText,
            icon = Icons.Rounded.GpsFixed,
            accentColor = NeonPurple
        ),

        // Section 4: FIRMWARE
        DatasheetGridItem.SectionHeader(
            section = SpecSection.FIRMWARE,
            title = stringResource(R.string.section_firmware),
            accentColor = NeonPurple
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.CONTROLLER_FW,
            section = SpecSection.FIRMWARE,
            label = stringResource(R.string.spec_controller_fw),
            value = vehicle.controllerVersion,
            subValue = regenSub,
            icon = Icons.Rounded.DeveloperBoard,
            accentColor = NeonCyan
        ),
        DatasheetGridItem.SpecCardItem(
            targetKey = SpecTargetKey.BMS_PROTOCOL,
            section = SpecSection.FIRMWARE,
            label = stringResource(R.string.spec_bms_protocol),
            value = vehicle.bmsVersion,
            subValue = vehicle.protocol,
            icon = Icons.Rounded.Memory,
            accentColor = NeonPurple
        )
    )
}
