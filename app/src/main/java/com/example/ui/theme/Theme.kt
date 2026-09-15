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

private val CustomDarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003914),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFB4CCB8),
    onSecondary = Color(0xFF203526),
    secondaryContainer = Color(0xFF364C3B),
    onSecondaryContainer = Color(0xFFD0E8D3),
    tertiary = Color(0xFFD0BCFF),
    onTertiary = Color(0xFF381E72),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE2E3DF),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE2E3DF),
    surfaceVariant = Color(0xFF404941),
    onSurfaceVariant = Color(0xFFC0C9BE)
)

private val CustomLightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9),
    onPrimaryContainer = Color(0xFF002108),
    secondary = Color(0xFF4CAF50),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D5),
    onSecondaryContainer = Color(0xFF0C1F11),
    tertiary = Color(0xFF7C4DFF),
    onTertiary = Color.White,
    background = Color(0xFFF6FBF6),
    onBackground = Color(0xFF191D19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191D19),
    surfaceVariant = Color(0xFFE0E5E0),
    onSurfaceVariant = Color(0xFF434843)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to preserve our beautiful specific visual scheme!
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> CustomDarkColorScheme
        else -> CustomLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
