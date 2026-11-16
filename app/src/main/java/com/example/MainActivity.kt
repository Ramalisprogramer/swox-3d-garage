package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.UserPreferencesRepository
import com.example.data.VehicleRepository
import com.example.model.AppLanguage
import com.example.ui.screen.VehicleDatasheetScreen
import com.example.ui.theme.SwoxGarageTheme
import com.example.viewmodel.GarageViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var preferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        vehicleRepository = VehicleRepository(applicationContext)
        preferencesRepository = UserPreferencesRepository(applicationContext)

        setContent {
            val garageViewModel: GarageViewModel = viewModel(
                factory = GarageViewModel.Factory(
                    vehicleRepository = vehicleRepository,
                    preferencesRepository = preferencesRepository
                )
            )
            val uiState by garageViewModel.uiState.collectAsStateWithLifecycle()
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, uiState.language) {
                createLocalizedContext(baseContext, uiState.language)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration
            ) {
                SwoxGarageTheme {
                    VehicleDatasheetScreen(viewModel = garageViewModel)
                }
            }
        }
    }
}

private fun createLocalizedContext(base: Context, language: AppLanguage): Context {
    val locale = Locale.forLanguageTag(language.code)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}
