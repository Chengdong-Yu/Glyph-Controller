package com.richard.glyphcontroller

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import ui.feice.mochi.effect.content.MochiThemeRevealHost
import ui.feice.mochi.effect.content.MochiThemeRevealState
import ui.feice.mochi.effect.content.rememberMochiThemeRevealState
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.nothing.nothingColorScheme
import ui.feice.mochi.nothing.nothingControlPalette
import ui.feice.mochi.nothing.nothingTextStyles
import ui.feice.mochi.theme.LocalMochiControlPalette
import ui.feice.mochi.theme.MochiMaterials
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.animateMochiColorScheme

/** Nothing OS color mode preference: system-following monochrome or explicit light/dark. */
enum class GlyphColorMode(@param:StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_mode_system),
    LIGHT(R.string.theme_mode_light),
    DARK(R.string.theme_mode_dark),
    ;

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** Persists Nothing appearance preferences in the shared settings store. */
object GlyphAppearancePrefs {
    private const val PREFS = "settings"
    private const val KEY_COLOR_MODE = "nothing_color_mode"
    private const val KEY_SERIF_TITLES = "nothing_serif_titles"

    fun getColorMode(context: Context): GlyphColorMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COLOR_MODE, GlyphColorMode.SYSTEM.name)
        return GlyphColorMode.entries.firstOrNull { it.name == raw } ?: GlyphColorMode.SYSTEM
    }

    fun setColorMode(context: Context, mode: GlyphColorMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_COLOR_MODE, mode.name)
        }
    }

    fun getSerifTitles(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERIF_TITLES, true)

    fun setSerifTitles(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_SERIF_TITLES, enabled)
        }
    }
}

/** Exposes the root [MochiThemeRevealState] so appearance settings can trigger reveal or crossfade transitions. */
val LocalGlyphThemeReveal: ProvidableCompositionLocal<MochiThemeRevealState?> =
    staticCompositionLocalOf { null }

/**
 * Single root theme entry point configuring Mochi with the Nothing appearance (`mochi-nothing`):
 * - Monochrome warm-neutral light/dark surfaces + `#D71921` Nothing Red primary accent
 * - `nothingControlPalette` (white thumbs, neutral inactive tracks, Nothing-red active tracks & tab selection)
 * - `nothingTextStyles` (system Serif display/navigation titles + Inter UI text, with CJK fallback)
 * - Solid `MochiMaterials()` (zero Liquid Glass)
 * - Cresto overscroll physics via [ProvideMochiOverscroll]
 */
@Composable
fun GlyphAppTheme(
    colorMode: GlyphColorMode,
    serifTitles: Boolean,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = colorMode.resolveDark(systemDark)
    val revealState = rememberMochiThemeRevealState()
    val targetColors = remember(isDark) { nothingColorScheme(isDark) }
    val controlPalette = remember(isDark) { nothingControlPalette(isDark) }
    val textStyles = remember(serifTitles) { nothingTextStyles(serifDisplay = serifTitles) }
    val materials = remember { MochiMaterials() }

    MochiThemeRevealHost(state = revealState) {
        val colors = animateMochiColorScheme(
            target = targetColors,
            animate = !revealState.isRevealing,
        )
        CompositionLocalProvider(
            LocalMochiControlPalette provides controlPalette,
            LocalGlyphThemeReveal provides revealState,
        ) {
            MochiTheme(
                colors = colors,
                textStyles = textStyles,
                materials = materials,
            ) {
                ProvideMochiOverscroll(content = content)
            }
        }
    }
}
