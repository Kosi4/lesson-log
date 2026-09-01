package com.kosi.lessonlog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The web app's palette, pinned. Material 3 applies dynamic colour on Android 12+
 * unless a scheme is given, which pulls tones from the wallpaper -- that is where
 * the purple came from. Everything here is stated explicitly so the phone and the
 * laptop look like the same product.
 */
object Palette {
    val Background = Color(0xFFF4F3EF)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1A1A1A)
    val Muted = Color(0xFF666666)
    val Faint = Color(0xFF999999)
    val Hairline = Color(0xFFEEEEEE)
    val Border = Color(0xFFDDDDDD)

    val Green = Color(0xFF1D9E75)
    val Amber = Color(0xFFE8B84B)
    val Red = Color(0xFFD85A30)

    val DayEmpty = Color(0xFFF0EFEB)
    val DayOff = Color(0xFFF7F6F3)

    val PillDoneBg = Color(0xFFE3F5EC)
    val PillDoneInk = Color(0xFF14724F)
    val PillMissBg = Color(0xFFFBE6DD)
    val PillMissInk = Color(0xFF9C3D17)
}

private val scheme = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Color.White,
    background = Palette.Background,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Surface,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Border,
    error = Palette.Red,
)

@Composable
fun LessonLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
