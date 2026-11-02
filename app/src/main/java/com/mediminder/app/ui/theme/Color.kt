package com.mediminder.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Accent colours users can tag medicines with. Mid tones work on light and dark surfaces. */
val MedicineColors = listOf(
    Color(0xFF14B8A6), // teal
    Color(0xFF3B82F6), // blue
    Color(0xFF8B5CF6), // violet
    Color(0xFFEC4899), // pink
    Color(0xFFF97316), // orange
    Color(0xFFEAB308), // amber
    Color(0xFF22C55E), // green
    Color(0xFFEF4444), // red
    Color(0xFF64748B), // slate
)

fun medicineColor(index: Int): Color = MedicineColors[index.mod(MedicineColors.size)]

val SuccessLight = Color(0xFF15803D)
val SuccessDark = Color(0xFF4ADE80)
val WarningLight = Color(0xFFB45309)
val WarningDark = Color(0xFFFBBF24)

val LightColors = lightColorScheme(
    primary = Color(0xFF006A66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF2EB),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A6361),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E5),
    onSecondaryContainer = Color(0xFF051F1E),
    tertiary = Color(0xFF47617D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFE4FF),
    onTertiaryContainer = Color(0xFF001D35),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAF9),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF6FAF9),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E3),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFBEC9C7),
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFECF2F0),
    inversePrimary = Color(0xFF80D5CE),
    surfaceBright = Color(0xFFF6FAF9),
    surfaceDim = Color(0xFFD6DBDA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F3),
    surfaceContainer = Color(0xFFEAEFED),
    surfaceContainerHigh = Color(0xFFE4E9E8),
    surfaceContainerHighest = Color(0xFFDFE4E2),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5CE),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFF9CF2EB),
    secondary = Color(0xFFB1CCC9),
    onSecondary = Color(0xFF1C3533),
    secondaryContainer = Color(0xFF324B49),
    onSecondaryContainer = Color(0xFFCCE8E5),
    tertiary = Color(0xFFAFC9EA),
    onTertiary = Color(0xFF17324D),
    tertiaryContainer = Color(0xFF2F4964),
    onTertiaryContainer = Color(0xFFCFE4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C7),
    outline = Color(0xFF889391),
    outlineVariant = Color(0xFF3F4948),
    inverseSurface = Color(0xFFDDE4E2),
    inverseOnSurface = Color(0xFF2B3231),
    inversePrimary = Color(0xFF006A66),
    surfaceBright = Color(0xFF343B3A),
    surfaceDim = Color(0xFF0E1514),
    surfaceContainerLowest = Color(0xFF090F0F),
    surfaceContainerLow = Color(0xFF171D1C),
    surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635),
)
