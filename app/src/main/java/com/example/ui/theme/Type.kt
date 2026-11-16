package com.example.ui.theme

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.fonts.Font as NativeFont
import android.graphics.fonts.FontFamily as NativeFontFamily
import android.graphics.fonts.FontStyle as NativeFontStyle
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val ChakraPetchFontFamily = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

/**
 * Builds a unified geometric futuristic FontFamily chain:
 * Primary: Orbitron (sculpted cyber/display glyphs)
 * Fallback 1: Chakra Petch (matching weight, full Turkish/Azerbaijani extended Latin: ğ, ş, ç, ö, ü, ı, İ, Ə, ə)
 * Fallback 2: Inter (full 2,849-glyph extended Unicode safety net)
 *
 * This eliminates system Roboto fallback discrepancies on localized 'ğ', 'ş', 'ç', 'ö', 'ü', 'ı', 'İ', 'Ə', 'ə' glyphs.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun buildOrbitronUnifiedNativeFontFamily(
    resources: Resources,
    weightValue: Int,
    chakraResId: Int
): FontFamily? = runCatching {
    val orbitronFamily = NativeFontFamily.Builder(
        NativeFont.Builder(resources, R.font.orbitron)
            .setWeight(weightValue)
            .setSlant(NativeFontStyle.FONT_SLANT_UPRIGHT)
            .setFontVariationSettings("'wght' $weightValue")
            .build()
    ).build()

    val chakraFallback = NativeFontFamily.Builder(
        NativeFont.Builder(resources, chakraResId)
            .setWeight(weightValue)
            .setSlant(NativeFontStyle.FONT_SLANT_UPRIGHT)
            .build()
    ).build()

    val interFallback = NativeFontFamily.Builder(
        NativeFont.Builder(resources, R.font.inter)
            .setWeight(weightValue)
            .setSlant(NativeFontStyle.FONT_SLANT_UPRIGHT)
            .setFontVariationSettings("'wght' $weightValue")
            .build()
    ).build()

    val customTypeface = Typeface.CustomFallbackBuilder(orbitronFamily)
        .addCustomFallback(chakraFallback)
        .addCustomFallback(interFallback)
        .setStyle(NativeFontStyle(weightValue, NativeFontStyle.FONT_SLANT_UPRIGHT))
        .build()

    FontFamily(customTypeface)
}.getOrNull()

fun createDisplayFallbackFontFamily(
    context: Context,
    weight: FontWeight
): FontFamily {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val (weightValue, chakraRes) = when (weight) {
            FontWeight.Bold -> NativeFontStyle.FONT_WEIGHT_BOLD to R.font.chakra_petch_bold
            FontWeight.SemiBold -> NativeFontStyle.FONT_WEIGHT_SEMI_BOLD to R.font.chakra_petch_semibold
            FontWeight.Medium -> NativeFontStyle.FONT_WEIGHT_MEDIUM to R.font.chakra_petch_medium
            else -> NativeFontStyle.FONT_WEIGHT_NORMAL to R.font.chakra_petch_regular
        }
        buildOrbitronUnifiedNativeFontFamily(
            resources = context.resources,
            weightValue = weightValue,
            chakraResId = chakraRes
        )?.let { return it }
    }
    // API 24-28 fallback retains consistent futuristic geometric glyph coverage (ğ, ş, ç, ö, ü, ı, Ə, ə)
    return ChakraPetchFontFamily
}

fun createBodyFallbackFontFamily(
    context: Context,
    weight: FontWeight
): FontFamily {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val weightValue = when (weight) {
            FontWeight.Bold -> NativeFontStyle.FONT_WEIGHT_BOLD
            FontWeight.SemiBold -> NativeFontStyle.FONT_WEIGHT_SEMI_BOLD
            FontWeight.Medium -> NativeFontStyle.FONT_WEIGHT_MEDIUM
            else -> NativeFontStyle.FONT_WEIGHT_NORMAL
        }
        val chakraRes = when (weight) {
            FontWeight.Bold -> R.font.chakra_petch_bold
            FontWeight.SemiBold -> R.font.chakra_petch_semibold
            FontWeight.Medium -> R.font.chakra_petch_medium
            else -> R.font.chakra_petch_regular
        }
        runCatching {
            val interPrimary = NativeFontFamily.Builder(
                NativeFont.Builder(context.resources, R.font.inter)
                    .setWeight(weightValue)
                    .setSlant(NativeFontStyle.FONT_SLANT_UPRIGHT)
                    .setFontVariationSettings("'wght' $weightValue")
                    .build()
            ).build()
            val chakraBackup = NativeFontFamily.Builder(
                NativeFont.Builder(context.resources, chakraRes)
                    .setWeight(weightValue)
                    .setSlant(NativeFontStyle.FONT_SLANT_UPRIGHT)
                    .build()
            ).build()
            val typeface = Typeface.CustomFallbackBuilder(interPrimary)
                .addCustomFallback(chakraBackup)
                .setStyle(NativeFontStyle(weightValue, NativeFontStyle.FONT_SLANT_UPRIGHT))
                .build()
            return FontFamily(typeface)
        }
    }
    return InterFontFamily
}

fun createSwoxTypography(context: Context? = null): Typography {
    val displayNormal = context?.let { createDisplayFallbackFontFamily(it, FontWeight.Normal) } ?: ChakraPetchFontFamily
    val displayMedium = context?.let { createDisplayFallbackFontFamily(it, FontWeight.Medium) } ?: ChakraPetchFontFamily
    val displaySemiBold = context?.let { createDisplayFallbackFontFamily(it, FontWeight.SemiBold) } ?: ChakraPetchFontFamily
    val displayBold = context?.let { createDisplayFallbackFontFamily(it, FontWeight.Bold) } ?: ChakraPetchFontFamily

    val bodyNormal = context?.let { createBodyFallbackFontFamily(it, FontWeight.Normal) } ?: InterFontFamily
    val bodyMedium = context?.let { createBodyFallbackFontFamily(it, FontWeight.Medium) } ?: InterFontFamily
    val bodySemiBold = context?.let { createBodyFallbackFontFamily(it, FontWeight.SemiBold) } ?: InterFontFamily

    return Typography(
        displayLarge = TextStyle(
            fontFamily = displayBold,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = 1.4.sp,
            fontFeatureSettings = "tnum"
        ),
        displaySmall = TextStyle(
            fontFamily = displayBold,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = 1.2.sp,
            fontFeatureSettings = "tnum"
        ),
        headlineSmall = TextStyle(
            fontFamily = displayBold,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 1.0.sp,
            fontFeatureSettings = "tnum"
        ),
        titleLarge = TextStyle(
            fontFamily = displaySemiBold,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.8.sp,
            fontFeatureSettings = "tnum"
        ),
        titleMedium = TextStyle(
            fontFamily = displaySemiBold,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.6.sp,
            fontFeatureSettings = "tnum"
        ),
        titleSmall = TextStyle(
            fontFamily = displayMedium,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.8.sp,
            fontFeatureSettings = "tnum"
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyMedium,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyNormal,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.15.sp
        ),
        bodySmall = TextStyle(
            fontFamily = bodyNormal,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.2.sp
        ),
        labelLarge = TextStyle(
            fontFamily = displaySemiBold,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp,
            fontFeatureSettings = "tnum"
        ),
        labelMedium = TextStyle(
            fontFamily = bodySemiBold,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = displayMedium,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            letterSpacing = 0.8.sp,
            fontFeatureSettings = "tnum"
        )
    )
}

val SwoxTypography: Typography = createSwoxTypography(context = null)
