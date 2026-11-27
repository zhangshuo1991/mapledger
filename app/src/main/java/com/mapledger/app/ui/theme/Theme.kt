package com.mapledger.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Design tokens. One accent, quiet neutrals. */
@Immutable
data class Palette(
    val accent: Color,
    val onAccent: Color,
    val bg: Color,          // screen background
    val surface: Color,     // sheets, cards
    val surface2: Color,    // keys, chips, inputs
    val text: Color,
    val text2: Color,       // secondary
    val text3: Color,       // tertiary / hints
    val hairline: Color,
    val scrim: Color,
    val danger: Color,
    val dark: Boolean,
)

val LightPalette = Palette(
    accent = Color(0xFF1F6FEB), onAccent = Color.White,
    bg = Color(0xFFF6F6F8), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF1F1F4),
    text = Color(0xFF111114), text2 = Color(0xFF6B6B74), text3 = Color(0xFFA5A5AE),
    hairline = Color(0x14000000), scrim = Color(0x52000000), danger = Color(0xFFE5484D), dark = false,
)

val DarkPalette = Palette(
    accent = Color(0xFF4C8DFF), onAccent = Color.White,
    bg = Color(0xFF0B0B0D), surface = Color(0xFF1A1A1E), surface2 = Color(0xFF26262B),
    text = Color(0xFFF4F4F6), text2 = Color(0xFF9D9DA6), text3 = Color(0xFF66666E),
    hairline = Color(0x1FFFFFFF), scrim = Color(0x8C000000), danger = Color(0xFFFF6369), dark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object Type {
    val hero = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Normal)
    val key = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Normal)
}

@Composable
fun MapLedgerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    // The activity handles uiMode changes itself (no relaunch), so keep system-bar icon contrast in sync here.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    val scheme = if (dark) darkColorScheme(primary = p.accent, background = p.bg, surface = p.surface)
    else lightColorScheme(primary = p.accent, background = p.bg, surface = p.surface)
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

