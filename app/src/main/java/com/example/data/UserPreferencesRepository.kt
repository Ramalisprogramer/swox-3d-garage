package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppLanguage
import com.example.model.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.garageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "swox_garage_preferences"
)

data class UserPreferences(
    val language: AppLanguage = AppLanguage.EN,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val selectedVehicleId: String? = null
)

class UserPreferencesRepository(
    private val context: Context
) {
    private object Keys {
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val SELECTED_VEHICLE_ID = stringPreferencesKey("selected_vehicle_id")
    }

    val preferencesFlow: Flow<UserPreferences> = context.garageDataStore.data.map { prefs ->
        UserPreferences(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE_CODE]),
            unitSystem = UnitSystem.fromName(prefs[Keys.UNIT_SYSTEM]),
            selectedVehicleId = prefs[Keys.SELECTED_VEHICLE_ID]
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.garageDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE_CODE] = language.code
        }
    }

    suspend fun setUnitSystem(unitSystem: UnitSystem) {
        context.garageDataStore.edit { prefs ->
            prefs[Keys.UNIT_SYSTEM] = unitSystem.name
        }
    }

    suspend fun setSelectedVehicleId(vehicleId: String) {
        context.garageDataStore.edit { prefs ->
            prefs[Keys.SELECTED_VEHICLE_ID] = vehicleId
        }
    }
}
