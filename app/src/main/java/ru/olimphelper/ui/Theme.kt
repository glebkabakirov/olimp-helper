package ru.olimphelper.ui

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

private val Light = lightColorScheme(
    primary = Color(0xFF2F5BD3),
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF00174B),
    secondaryContainer = Color(0xFFDDE1F9),
    tertiary = Color(0xFF1B7A4B),
    tertiaryContainer = Color(0xFFB4F1C9),
    onTertiaryContainer = Color(0xFF002111),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    primaryContainer = Color(0xFF0E3FB5),
    onPrimaryContainer = Color(0xFFDCE1FF),
    tertiary = Color(0xFF98D5AE),
    tertiaryContainer = Color(0xFF005231),
    onTertiaryContainer = Color(0xFFB4F1C9),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
)

@Composable
fun OlimpTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
