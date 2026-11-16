package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.UnitSystem
import com.example.model.VehicleClass
import com.example.model.VehicleSummary
import com.example.ui.theme.CyanStroke35
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

/**
 * Full searchable rideables catalog view with per-category counts and lazy pagination,
 * scaling smoothly across 1,000+ items per category without any featured section.
 */
@Composable
fun GarageCatalogPane(
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
    modifier: Modifier = Modifier,
    highlightSelection: Boolean = false
) {
    val listState = rememberLazyListState()
    val maxForFilter = selectedCategory?.let { categoryCounts[it] ?: 0 } ?: totalCount
    val canLoadMore = searchQuery.isBlank() && vehicles.size < maxForFilter

    LaunchedEffect(selectedCategory) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(vehicles.size, maxForFilter, searchQuery) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= (vehicles.size - 8).coerceAtLeast(0)
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                onLoadMore()
            }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.selector_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = NeonCyan
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = TextSecondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = CyanStroke35,
                focusedContainerColor = Color(0xFF0C0E14),
                unfocusedContainerColor = Color(0xFF0C0E14),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vehicle_search_input")
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item {
                CatalogCategoryChip(
                    label = stringResource(R.string.category_all, totalCount),
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) }
                )
            }
            val categories = listOf(
                VehicleClass.SCOOTER to R.string.category_scooter,
                VehicleClass.E_BIKE to R.string.category_ebike,
                VehicleClass.BICYCLE to R.string.category_bicycle,
                VehicleClass.MOTO to R.string.category_moto,
                VehicleClass.E_SKATE to R.string.category_eskate,
                VehicleClass.ONEWHEEL to R.string.category_onewheel,
                VehicleClass.MICRO_EV to R.string.category_micro
            )
            items(categories) { (category, labelRes) ->
                val count = categoryCounts[category] ?: 0
                val chipLabel = if (count > 0) {
                    stringResource(
                        R.string.category_with_count,
                        stringResource(labelRes),
                        count
                    )
                } else {
                    stringResource(labelRes)
                }
                CatalogCategoryChip(
                    label = chipLabel,
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) }
                )
            }
        }

        if (vehicles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.selector_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("catalog_vehicle_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(vehicles, key = { it.id }) { item ->
                    val isSelected = highlightSelection && item.id == selectedVehicleId
                    val speedStr = if (unitSystem == UnitSystem.METRIC) {
                        stringResource(
                            R.string.format_unit_value,
                            item.topSpeedKmh.roundToInt().toString(),
                            stringResource(R.string.unit_kmh)
                        )
                    } else {
                        stringResource(
                            R.string.format_unit_value,
                            (item.topSpeedKmh * 0.621371).roundToInt().toString(),
                            stringResource(R.string.unit_mph)
                        )
                    }
                    Surface(
                        onClick = { onVehicleSelected(item.id) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) NeonCyan.copy(alpha = 0.14f) else Color(0xFF171923),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) NeonCyan else CyanStroke35.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vehicle_item_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconForVehicleClass(item.vehicleClass),
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(
                                        R.string.format_brand_passport,
                                        item.brand,
                                        item.passportCode
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (item.motorWatts > 0) {
                                        stringResource(R.string.format_watts_compact, item.motorWatts)
                                    } else {
                                        stringResource(R.string.badge_mechanical_short)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonCyan
                                )
                                Text(
                                    text = speedStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                if (canLoadMore) {
                    item(key = "load_more_footer") {
                        Surface(
                            onClick = onLoadMore,
                            shape = RoundedCornerShape(16.dp),
                            color = NeonPurple.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("load_more_vehicles_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.sync_load_more, vehicles.size, maxForFilter),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonPurple
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) NeonCyan.copy(alpha = 0.18f) else Color(0xFF171923),
        border = BorderStroke(
            1.dp,
            if (selected) NeonCyan else CyanStroke35.copy(alpha = 0.3f)
        ),
        modifier = Modifier.minimumInteractiveComponentSize()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) NeonCyan else TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
