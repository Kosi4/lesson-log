package com.kosi.lessonlog

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * The palette the web app uses, stated explicitly. Material 3 applies dynamic
 * colour on Android 12+ unless a scheme is given, which derives tones from the
 * wallpaper -- that is where the purple came from. Nothing here is left to a
 * default.
 */

private object Light {
    val ground = Color(0xFFF2F1EC)
    val sheet = Color(0xFFFFFFFF)
    val ink = Color(0xFF191A17)
    val inkMuted = Color(0xFF5F625B)
    val rule = Color(0xFFE3E2DB)
    val done = Color(0xFF1A7A53)
    val missed = Color(0xFFB4482A)
    val empty = Color(0xFFDEDCD2)
    val onStatus = Color(0xFFFFFFFF)
    val pillDoneBg = Color(0xFFE3F1E9)
    val pillDoneInk = Color(0xFF14603F)
    val pillMissBg = Color(0xFFF7E4DC)
    val pillMissInk = Color(0xFF8C3A22)
}

private object Dark {
    val ground = Color(0xFF161714)
    val sheet = Color(0xFF1E201C)
    val ink = Color(0xFFEDEDE8)
    val inkMuted = Color(0xFF9A9E95)
    val rule = Color(0xFF2C2F29)
    val done = Color(0xFF3FB183)
    val missed = Color(0xFFD4674A)
    val empty = Color(0xFF303329)
    val onStatus = Color(0xFF10120F)
    val pillDoneBg = Color(0xFF1D3A2D)
    val pillDoneInk = Color(0xFF7FD3AC)
    val pillMissBg = Color(0xFF3B241B)
    val pillMissInk = Color(0xFFEDA087)
}

/** Semantic colours the UI reads, so no composable hardcodes a hex. */
data class LessonColors(
    val ground: Color,
    val sheet: Color,
    val ink: Color,
    val inkMuted: Color,
    val rule: Color,
    val done: Color,
    val missed: Color,
    val empty: Color,
    val onStatus: Color,
    val pillDoneBg: Color,
    val pillDoneInk: Color,
    val pillMissBg: Color,
    val pillMissInk: Color,
)

private val lightColors = LessonColors(
    Light.ground, Light.sheet, Light.ink, Light.inkMuted, Light.rule,
    Light.done, Light.missed, Light.empty, Light.onStatus,
    Light.pillDoneBg, Light.pillDoneInk, Light.pillMissBg, Light.pillMissInk,
)

private val darkColors = LessonColors(
    Dark.ground, Dark.sheet, Dark.ink, Dark.inkMuted, Dark.rule,
    Dark.done, Dark.missed, Dark.empty, Dark.onStatus,
    Dark.pillDoneBg, Dark.pillDoneInk, Dark.pillMissBg, Dark.pillMissInk,
)

val LocalLessonColors = staticCompositionLocalOf { lightColors }

object LessonTheme {
    val colors: LessonColors
        @Composable @ReadOnlyComposable get() = LocalLessonColors.current
}

/*
 * Fira Sans for text, Fira Mono for anything that has to line up in a column.
 * One superfamily, the same files the web app loads from Google Fonts, so the
 * phone and the laptop render the same shapes.
 */
private val FiraSans = FontFamily(
    Font(R.font.fira_sans_regular, FontWeight.Normal),
    Font(R.font.fira_sans_medium, FontWeight.Medium),
    Font(R.font.fira_sans_semibold, FontWeight.SemiBold),
)

val FiraMono = FontFamily(
    Font(R.font.fira_mono_regular, FontWeight.Normal),
    Font(R.font.fira_mono_medium, FontWeight.Medium),
)

// Sizes live here rather than at each call site, so system font scaling works
// and the scale can be changed in one place.
private val typography = Typography(
    titleLarge = TextStyle(fontFamily = FiraSans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FiraSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FiraSans, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FiraSans, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FiraSans, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FiraSans, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FiraSans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FiraSans, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    // Numbers: mono so counts and dates do not shift width as they change.
    headlineSmall = TextStyle(fontFamily = FiraMono, fontWeight = FontWeight.Medium, fontSize = 22.sp),
)

@Composable
fun LessonLogTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) darkColors else lightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.ink, onPrimary = colors.ground,
            background = colors.ground, onBackground = colors.ink,
            surface = colors.sheet, onSurface = colors.ink,
            surfaceVariant = colors.sheet, onSurfaceVariant = colors.inkMuted,
            outline = colors.rule, error = colors.missed,
        )
    } else {
        lightColorScheme(
            primary = colors.ink, onPrimary = colors.ground,
            background = colors.ground, onBackground = colors.ink,
            surface = colors.sheet, onSurface = colors.ink,
            surfaceVariant = colors.sheet, onSurfaceVariant = colors.inkMuted,
            outline = colors.rule, error = colors.missed,
        )
    }

    CompositionLocalProvider(LocalLessonColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
