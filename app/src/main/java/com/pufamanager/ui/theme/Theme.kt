package com.pufamanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AccentPink,
    secondary = SecondaryText,
    tertiary = SuccessGreen,
    background = AcademyDark,
    surface = PrimarySurface,
    onPrimary = AcademyDark,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = PrimarySurface, // Reduced contrast for softer integration
    onSurfaceVariant = SecondaryText,
    outline = Color(0xFF3A2029).copy(alpha = 0.3f),
    outlineVariant = Color(0xFF3A2029).copy(alpha = 0.1f),
    surfaceTint = Color.Transparent // Prevents Material3 tonal elevation shifts
)

private val LightColorScheme = lightColorScheme(
    primary = AccentPink,
    secondary = SecondaryText,
    tertiary = SuccessGreen,
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = AcademyDark,
    onTertiary = AcademyDark,
    onBackground = AcademyDark,
    onSurface = AcademyDark
)

@Composable
fun PUFAAttendanceManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled to restore the original brand-consistent cinematic atmosphere
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // We override dynamic color to ensure the academy's specific dark wine palette is used
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
