package com.mediminder.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Accent colours users can tag medicines with. Mid tones work on light and dark surfaces. */
val MedicineColors = listOf(
    Color(0xFF0EA5E9), // sky
    Color(0xFF14B8A6), // teal
    Color(0xFF6366F1), // indigo
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

// Sky-blue Material 3 palette (seed ≈ #0EA5E9).
val LightColors = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF4E616D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E5F4),
    onSecondaryContainer = Color(0xFF0A1E28),
    tertiary = Color(0xFF62597C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8DEFF),
    onTertiaryContainer = Color(0xFF1E1635),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAFE),
    onBackground = Color(0xFF171C20),
    surface = Color(0xFFF6FAFE),
    onSurface = Color(0xFF171C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    inverseSurface = Color(0xFF2C3135),
    inverseOnSurface = Color(0xFFEDF1F5),
    inversePrimary = Color(0xFF89CEFF),
    surfaceBright = Color(0xFFF6FAFE),
    surfaceDim = Color(0xFFD6DADF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F8),
    surfaceContainer = Color(0xFFEAEEF2),
    surfaceContainerHigh = Color(0xFFE4E9ED),
    surfaceContainerHighest = Color(0xFFDFE3E7),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF89CEFF),
    onPrimary = Color(0xFF00344D),
    primaryContainer = Color(0xFF004C6D),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFB5C9D7),
    onSecondary = Color(0xFF20333E),
    secondaryContainer = Color(0xFF364955),
    onSecondaryContainer = Color(0xFFD1E5F4),
    tertiary = Color(0xFFCCC0E9),
    onTertiary = Color(0xFF332B4B),
    tertiaryContainer = Color(0xFF4A4263),
    onTertiaryContainer = Color(0xFFE8DEFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDFE3E7),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDFE3E7),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41484D),
    inverseSurface = Color(0xFFDFE3E7),
    inverseOnSurface = Color(0xFF2C3135),
    inversePrimary = Color(0xFF00658F),
    surfaceBright = Color(0xFF353A3E),
    surfaceDim = Color(0xFF0F1417),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C20),
    surfaceContainer = Color(0xFF1B2024),
    surfaceContainerHigh = Color(0xFF262A2E),
    surfaceContainerHighest = Color(0xFF313539),
)
