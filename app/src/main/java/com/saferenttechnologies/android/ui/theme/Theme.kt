package com.saferenttechnologies.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun SafeRentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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

    // Snackbars ("No internet connection", "Allow location" …) use the inverse colours. Dark mode:
    // our grey pill + light text + orange action instead of the wallpaper's light purple / pink.
    // Light mode stays exactly as it was.
    val scheme = if (darkTheme) {
        colorScheme.copy(
            inverseSurface = androidx.compose.ui.graphics.Color(0xFF2B313B),
            inverseOnSurface = androidx.compose.ui.graphics.Color(0xFFF1F5F9),
            inversePrimary = androidx.compose.ui.graphics.Color(0xFFF0874F),
        )
    } else {
        colorScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}