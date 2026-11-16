package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ElectricScooter
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.Skateboarding
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.CatalogSyncStatus
import com.example.model.AppLanguage
import com.example.model.UnitSystem
import com.example.model.VehicleClass
import com.example.model.VehicleSummary
import com.example.ui.theme.CyanStroke35
import com.example.ui.theme.GlassElevatedSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.OledCanvasBlack
import com.example.ui.theme.PurpleStroke35
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageSettingsSheet(
    currentLanguage: AppLanguage,
    currentUnitSystem: UnitSystem,
    onLanguageSelected: (AppLanguage) -> Unit,
    onUnitSystemSelected: (UnitSystem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlassElevatedSurface,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonCyan
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("settings_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = TextSecondary
                    )
                }
            }

            // Language Switcher (EN / AZ / TR)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_language_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val languages = listOf(
                        Triple(AppLanguage.EN, R.string.lang_en, R.string.lang_en_name),
                        Triple(AppLanguage.AZ, R.string.lang_az, R.string.lang_az_name),
                        Triple(AppLanguage.TR, R.string.lang_tr, R.string.lang_tr_name)
                    )
                    languages.forEach { (lang, codeRes, nameRes) ->
                        val isSelected = currentLanguage == lang
                        Surface(
                            onClick = { onLanguageSelected(lang) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) NeonCyan.copy(alpha = 0.16f) else Color(0xFF1A1C26),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) NeonCyan else CyanStroke35.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("language_option_${lang.code}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(codeRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) NeonCyan else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(nameRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Unit System Switcher (Metric vs Imperial)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_units_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        UnitSystem.METRIC to R.string.unit_metric,
                        UnitSystem.IMPERIAL to R.string.unit_imperial
                    ).forEach { (unit, labelRes) ->
                        val isSelected = currentUnitSystem == unit
                        Surface(
                            onClick = { onUnitSystemSelected(unit) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) NeonPurple.copy(alpha = 0.18f) else Color(0xFF1A1C26),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) NeonPurple else PurpleStroke35.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("unit_option_${unit.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSelectorSheet(
    vehicles: List<VehicleSummary>,
    totalCount: Int,
    categoryCounts: Map<VehicleClass, Int>,
    selectedVehicleId: String?,
    searchQuery: String,
    selectedCategory: VehicleClass?,
    unitSystem: UnitSystem,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (VehicleClass?) -> Unit,
    onVehicleSelected: (String) -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlassElevatedSurface,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.selector_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonCyan
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = TextSecondary
                    )
                }
            }

            GarageCatalogPane(
                vehicles = vehicles,
                totalCount = totalCount,
                categoryCounts = categoryCounts,
                selectedVehicleId = selectedVehicleId,
                searchQuery = searchQuery,
                selectedCategory = selectedCategory,
                unitSystem = unitSystem,
                onSearchQueryChange = onSearchQueryChange,
                onCategorySelected = onCategorySelected,
                onVehicleSelected = { vehicleId ->
                    onVehicleSelected(vehicleId)
                    onDismiss()
                },
                onLoadMore = onLoadMore,
                highlightSelection = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            )
        }
    }
}

@Composable
fun GarageProfileDialog(
    totalVehicles: Int,
    activePassportCode: String,
    activeVehicleName: String,
    syncStatus: CatalogSyncStatus,
    onDismiss: () -> Unit
) {
    val syncLabel = when (syncStatus) {
        CatalogSyncStatus.CLOUD_SYNCED -> stringResource(R.string.sync_status_synced)
        CatalogSyncStatus.SYNCING -> stringResource(R.string.sync_status_syncing)
        CatalogSyncStatus.OFFLINE_CACHE -> stringResource(R.string.sync_status_cached)
    }
    val syncColor = when (syncStatus) {
        CatalogSyncStatus.CLOUD_SYNCED -> StatusActiveGreen
        CatalogSyncStatus.SYNCING -> NeonCyan
        CatalogSyncStatus.OFFLINE_CACHE -> WarningAmber
    }
    val syncIcon = when (syncStatus) {
        CatalogSyncStatus.CLOUD_SYNCED -> Icons.Rounded.CloudDone
        CatalogSyncStatus.SYNCING -> Icons.Rounded.CloudSync
        CatalogSyncStatus.OFFLINE_CACHE -> Icons.Rounded.CloudOff
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassmorphicContainer(
            shape = RoundedCornerShape(24.dp),
            containerColor = GlassElevatedSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.25f), NeonPurple.copy(alpha = 0.25f)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.profile_passport_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = TextSecondary
                        )
                    }
                }

                PassportInfoRow(
                    label = stringResource(R.string.profile_owner_label),
                    value = stringResource(R.string.profile_owner_value),
                    accentColor = NeonCyan
                )
                PassportInfoRow(
                    label = stringResource(R.string.profile_catalog_label),
                    value = stringResource(R.string.profile_catalog_count, totalVehicles),
                    accentColor = StatusActiveGreen
                )
                PassportInfoRow(
                    label = stringResource(R.string.profile_active_passport),
                    value = stringResource(R.string.format_brand_passport, activeVehicleName, activePassportCode),
                    accentColor = NeonPurple
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = syncColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, syncColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = syncIcon,
                            contentDescription = null,
                            tint = syncColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = syncLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = syncColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassportInfoRow(
    label: String,
    value: String,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OledCanvasBlack.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = accentColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun iconForVehicleClass(vehicleClass: VehicleClass): ImageVector = when (vehicleClass) {
    VehicleClass.SCOOTER -> Icons.Rounded.ElectricScooter
    VehicleClass.E_BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
    VehicleClass.BICYCLE -> Icons.Rounded.PedalBike
    VehicleClass.MOTO -> Icons.Rounded.TwoWheeler
    VehicleClass.E_SKATE -> Icons.Rounded.Skateboarding
    VehicleClass.ONEWHEEL -> Icons.Rounded.Album
    VehicleClass.MICRO_EV -> Icons.Rounded.DirectionsCar
}
