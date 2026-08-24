package com.example.ui.theme

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
    primary = WisteriaLavender,
    onPrimary = WisteriaDeep,
    primaryContainer = WisteriaPurple,
    onPrimaryContainer = WisteriaSoftLilac,
    secondary = ForestGreenAccent,
    onSecondary = Color.White,
    secondaryContainer = ForestGreenDeep,
    onSecondaryContainer = ForestGreenMint,
    tertiary = ForestGreenMint,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = Color(0xFFEDE7F6),
    surface = DarkSurface,
    onSurface = Color(0xFFEDE7F6),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFD1C4E9),
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = WisteriaViolet,
    onPrimary = Color.White,
    primaryContainer = WisteriaPale,
    onPrimaryContainer = WisteriaDeep,
    secondary = ForestGreenAccent,
    onSecondary = Color.White,
    secondaryContainer = ForestGreenSage.copy(alpha = 0.25f),
    onSecondaryContainer = ForestGreenDeep,
    tertiary = ForestGreenMint,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1E142B),
    surface = LightSurface,
    onSurface = Color(0xFF1E142B),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4A3E5C),
    outline = LightBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep distinctive Wisteria aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = when {
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
