package com.example.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.CatalogSyncStatus
import com.example.data.UserPreferencesRepository
import com.example.data.VehicleRepository
import com.example.model.AppLanguage
import com.example.model.HotspotType
import com.example.model.RenderMode
import com.example.model.SpecTargetKey
import com.example.model.UnitSystem
import com.example.model.VehicleClass
import com.example.model.VehicleProfile
import com.example.model.VehicleSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraTransformState(
    val rotationAngleX: Float = INITIAL_PITCH_DEG,
    val rotationAngleY: Float = INITIAL_YAW_DEG,
    val zoomScale: Float = INITIAL_ZOOM_SCALE
) {
    companion object {
        const val INITIAL_PITCH_DEG = 20f
        const val INITIAL_YAW_DEG = -36f
        const val INITIAL_ZOOM_SCALE = 1.0f
        const val MIN_PITCH_DEG = -60f
        const val MAX_PITCH_DEG = 60f
        const val MIN_ZOOM_SCALE = 0.6f
        const val MAX_ZOOM_SCALE = 2.2f
    }
}

data class GarageUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    @param:StringRes val errorMessageRes: Int? = null,
    val filteredVehicles: List<VehicleSummary> = emptyList(),
    val categoryCounts: Map<VehicleClass, Int> = emptyMap(),
    val totalCachedCount: Int = 0,
    val pageLimit: Int = DEFAULT_PAGE_SIZE,
    val selectedVehicle: VehicleProfile? = null,
    val hasGlbAsset: Boolean = false,
    val renderMode: RenderMode = RenderMode.WIREFRAME_XRAY,
    val cameraState: CameraTransformState = CameraTransformState(),
    val activeHotspot: HotspotType? = null,
    val highlightedSpecTarget: SpecTargetKey? = null,
    val highlightPulseToken: Int = 0,
    val searchQuery: String = "",
    val selectedCategory: VehicleClass? = null,
    val language: AppLanguage = AppLanguage.EN,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val syncStatus: CatalogSyncStatus = CatalogSyncStatus.OFFLINE_CACHE,
    val cameraResetToken: Int = 0,
    val isInspectingVehicle: Boolean = false
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 50
        const val PAGE_INCREMENT = 50
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GarageViewModel(
    private val vehicleRepository: VehicleRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GarageUiState())
    val uiState: StateFlow<GarageUiState> = _uiState.asStateFlow()

    private val filterParamsFlow = MutableStateFlow(FilterParams())
    private var highlightClearJob: Job? = null

    init {
        observePreferences()
        initializeOfflineFirstCatalog()
        observeCloudSync()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                _uiState.update { current ->
                    current.copy(
                        language = prefs.language,
                        unitSystem = prefs.unitSystem
                    )
                }
            }
        }
    }

    private fun initializeOfflineFirstCatalog() {
        viewModelScope.launch {
            try {
                // 1. Seed Room locally if empty for immediate login-free offline-first startup
                vehicleRepository.ensureLocalSeeded()
                val savedPrefs = preferencesRepository.preferencesFlow.first()

                // 2. Observe total & per-category cached counts in Room
                launch {
                    vehicleRepository.observeTotalCachedCount().collect { count ->
                        _uiState.update { it.copy(totalCachedCount = count) }
                    }
                }
                launch {
                    vehicleRepository.observeCategoryCounts().collect { counts ->
                        _uiState.update { it.copy(categoryCounts = counts) }
                    }
                }

                // 3. Observe paginated, filtered lightweight Room summary queries reactively
                filterParamsFlow
                    .flatMapLatest { params ->
                        vehicleRepository.observeLocalVehicleSummaries(
                            query = params.query,
                            category = params.category,
                            limit = params.limit
                        )
                    }
                    .collect { vehicles ->
                        val currentSelected = _uiState.value.selectedVehicle
                        val resolvedSelected = if (currentSelected != null) {
                            vehicleRepository.getCachedVehicleById(currentSelected.id) ?: currentSelected
                        } else {
                            val savedId = savedPrefs.selectedVehicleId
                            (savedId?.let { vehicleRepository.getCachedVehicleById(it) })
                                ?: vehicles.firstOrNull()?.let { vehicleRepository.getCachedVehicleById(it.id) }
                        }

                        val hasGlb = resolvedSelected?.let {
                            vehicleRepository.hasBundledGlbAsset(it.modelAssetPath)
                        } ?: false

                        _uiState.update { state ->
                            val keepCurrentMode = state.selectedVehicle?.id == resolvedSelected?.id && !state.isLoading
                            state.copy(
                                isLoading = false,
                                errorMessage = null,
                                errorMessageRes = null,
                                filteredVehicles = vehicles,
                                selectedVehicle = resolvedSelected,
                                hasGlbAsset = hasGlb,
                                renderMode = if (keepCurrentMode) {
                                    state.renderMode
                                } else if (hasGlb) {
                                    RenderMode.STANDARD_PBR
                                } else {
                                    RenderMode.WIREFRAME_XRAY
                                }
                            )
                        }
                    }
            } catch (e: Exception) {
                val msg = e.localizedMessage?.takeIf { it.isNotBlank() }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = msg,
                        errorMessageRes = if (msg == null) R.string.error_catalog_load_failed else null
                    )
                }
            }
        }
    }

    private fun observeCloudSync() {
        viewModelScope.launch {
            vehicleRepository.observeCloudCatalogSync().collect { status ->
                _uiState.update { it.copy(syncStatus = status) }
            }
        }
    }

    fun selectVehicle(vehicleId: String, openInspector: Boolean = true) {
        viewModelScope.launch {
            val target = vehicleRepository.getCachedVehicleById(vehicleId) ?: return@launch
            val hasGlb = vehicleRepository.hasBundledGlbAsset(target.modelAssetPath)
            preferencesRepository.setSelectedVehicleId(target.id)
            highlightClearJob?.cancel()
            _uiState.update { state ->
                state.copy(
                    selectedVehicle = target,
                    hasGlbAsset = hasGlb,
                    cameraState = CameraTransformState(),
                    activeHotspot = null,
                    highlightedSpecTarget = null,
                    cameraResetToken = state.cameraResetToken + 1,
                    isInspectingVehicle = openInspector
                )
            }
        }
    }

    fun closeVehicleInspector() {
        _uiState.update { it.copy(isInspectingVehicle = false) }
    }

    fun setRenderMode(mode: RenderMode) {
        _uiState.update { it.copy(renderMode = mode) }
    }

    fun updateCameraTransform(
        rotationAngleX: Float,
        rotationAngleY: Float,
        zoomScale: Float
    ) {
        val clampedX = rotationAngleX.coerceIn(
            CameraTransformState.MIN_PITCH_DEG,
            CameraTransformState.MAX_PITCH_DEG
        )
        val wrappedY = ((rotationAngleY % 360f) + 360f) % 360f
        val clampedZoom = zoomScale.coerceIn(
            CameraTransformState.MIN_ZOOM_SCALE,
            CameraTransformState.MAX_ZOOM_SCALE
        )
        _uiState.update { state ->
            state.copy(
                cameraState = CameraTransformState(
                    rotationAngleX = clampedX,
                    rotationAngleY = wrappedY,
                    zoomScale = clampedZoom
                )
            )
        }
    }

    fun requestCameraReset() {
        _uiState.update {
            it.copy(
                cameraState = CameraTransformState(),
                cameraResetToken = it.cameraResetToken + 1
            )
        }
    }

    fun onHotspotSelected(hotspot: HotspotType) {
        val targetSpec = when (hotspot) {
            HotspotType.BATTERY_PACK -> SpecTargetKey.BATTERY_PACK
            HotspotType.REAR_MOTOR -> SpecTargetKey.MOTOR_POWER
            HotspotType.STEM_CONTROLLER -> SpecTargetKey.CONTROLLER_FW
        }

        _uiState.update { state ->
            state.copy(
                activeHotspot = hotspot,
                highlightedSpecTarget = targetSpec,
                highlightPulseToken = state.highlightPulseToken + 1
            )
        }

        highlightClearJob?.cancel()
        highlightClearJob = viewModelScope.launch {
            delay(3600L)
            _uiState.update { state ->
                state.copy(highlightedSpecTarget = null)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, pageLimit = GarageUiState.DEFAULT_PAGE_SIZE) }
        filterParamsFlow.update {
            it.copy(query = query, limit = GarageUiState.DEFAULT_PAGE_SIZE)
        }
    }

    fun selectCategory(category: VehicleClass?) {
        _uiState.update { it.copy(selectedCategory = category, pageLimit = GarageUiState.DEFAULT_PAGE_SIZE) }
        filterParamsFlow.update {
            it.copy(category = category, limit = GarageUiState.DEFAULT_PAGE_SIZE)
        }
    }

    fun loadMoreVehicles() {
        val nextLimit = _uiState.value.pageLimit + GarageUiState.PAGE_INCREMENT
        _uiState.update { it.copy(pageLimit = nextLimit) }
        filterParamsFlow.update { it.copy(limit = nextLimit) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesRepository.setLanguage(language)
        }
    }

    fun setUnitSystem(unitSystem: UnitSystem) {
        viewModelScope.launch {
            preferencesRepository.setUnitSystem(unitSystem)
        }
    }

    private data class FilterParams(
        val query: String = "",
        val category: VehicleClass? = null,
        val limit: Int = GarageUiState.DEFAULT_PAGE_SIZE
    )

    class Factory(
        private val vehicleRepository: VehicleRepository,
        private val preferencesRepository: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GarageViewModel::class.java)) {
                return GarageViewModel(vehicleRepository, preferencesRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
