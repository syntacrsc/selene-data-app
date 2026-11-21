package de.schnarkowski.selene.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import de.schnarkowski.selene.ThemeMode

/** Zusätzliche Farben für Instrumente. */
@Immutable
data class InstrumentColors(
    val port: Color,       // Backbord
    val starboard: Color,  // Steuerbord
    val accent: Color,
    val dim: Color,
    val warn: Color,
)

val LocalInstrumentColors = staticCompositionLocalOf {
    InstrumentColors(Color.Red, Color.Green, Color.Cyan, Color.Gray, Color.Yellow)
}

@Composable
fun SeleneTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val (scheme, inst) = when (mode) {
        ThemeMode.DAY -> lightColorScheme(
            primary = Color(0xFF0B4F8A),
            background = Color.White,
            surface = Color(0xFFF2F4F7),
            surfaceVariant = Color(0xFFE3E8EE),
            onBackground = Color.Black,
            onSurface = Color.Black,
            onSurfaceVariant = Color(0xFF333B45),
        ) to InstrumentColors(
            port = Color(0xFFC62828), starboard = Color(0xFF2E7D32),
            accent = Color(0xFF0B4F8A), dim = Color(0xFF8A939E), warn = Color(0xFFE65100),
        )

        ThemeMode.DARK -> darkColorScheme(
            primary = Color(0xFF6CC4FF),
            background = Color(0xFF05080C),
            surface = Color(0xFF111821),
            surfaceVariant = Color(0xFF1B2632),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFB8C4D0),
        ) to InstrumentColors(
            port = Color(0xFFFF5252), starboard = Color(0xFF4CE07A),
            accent = Color(0xFF6CC4FF), dim = Color(0xFF5F6E7D), warn = Color(0xFFFFB300),
        )

        ThemeMode.NIGHT -> darkColorScheme(
            primary = Color(0xFFD32F2F),
            secondary = Color(0xFF9A1C1C),
            background = Color.Black,
            surface = Color(0xFF0E0000),
            surfaceVariant = Color(0xFF1A0303),
            onBackground = Color(0xFFE53935),
            onSurface = Color(0xFFE53935),
            onSurfaceVariant = Color(0xFFB71C1C),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF2A0505),
            onPrimaryContainer = Color(0xFFE53935),
            secondaryContainer = Color(0xFF2A0505),
            onSecondaryContainer = Color(0xFFE53935),
        ) to InstrumentColors(
            port = Color(0xFFFF1744), starboard = Color(0xFF8E0000),
            accent = Color(0xFFE53935), dim = Color(0xFF5C0A0A), warn = Color(0xFFFF6E40),
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalInstrumentColors provides inst) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
