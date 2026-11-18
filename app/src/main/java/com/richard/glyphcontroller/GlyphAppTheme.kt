package com.richard.glyphcontroller

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.effect.content.MochiThemeRevealHost
import ui.feice.mochi.effect.content.MochiThemeRevealState
import ui.feice.mochi.effect.content.rememberMochiThemeRevealState
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.nothing.NothingColors
import ui.feice.mochi.nothing.nothingColorScheme
import ui.feice.mochi.nothing.nothingControlPalette
import ui.feice.mochi.nothing.nothingTextStyles
import ui.feice.mochi.theme.ColorSchemeMode
import ui.feice.mochi.theme.LocalMochiControlPalette
import ui.feice.mochi.theme.MochiGraphics
import ui.feice.mochi.theme.MochiMaterials
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.PaletteStyle
import ui.feice.mochi.theme.ThemeController
import ui.feice.mochi.theme.animateMochiColorScheme

/** Light/Dark preference independent of whether colors come from Monet wallpaper or a custom seed. */
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

    fun toColorSchemeMode(useMonet: Boolean): ColorSchemeMode = when (this) {
        SYSTEM -> if (useMonet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        LIGHT -> if (useMonet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        DARK -> if (useMonet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
    }
}

/** Material Design 3 HCT palette styles plus Nothing's monochrome surface option. */
enum class GlyphStyleVariant(@param:StringRes val labelRes: Int) {
    TONAL(R.string.palette_style_tonal),
    EXPRESSIVE(R.string.palette_style_expressive),
    VIBRANT(R.string.palette_style_vibrant),
    MONOCHROME(R.string.palette_style_monochrome),
    ;

    fun toMochiPaletteStyle(): PaletteStyle = when (this) {
        TONAL -> PaletteStyle.TonalSpot
        EXPRESSIVE -> PaletteStyle.Expressive
        VIBRANT -> PaletteStyle.Vibrant
        MONOCHROME -> PaletteStyle.TonalSpot
    }
}

/**
 * 9 curated Material Design 3 seed swatches (paired with `MochiSeedPalette(custom = true)` = 10 items,
 * forming a symmetrical 2x5 grid on standard phone widths). Starts with Nothing Red followed by
 * balanced cool/warm tonal hues.
 */
val GlyphSeedSwatches: ImmutableList<Color> = persistentListOf(
    NothingColors.Accent,
    Color(0xFF4A6FA5),
    Color(0xFF6750A4),
    Color(0xFF2B6A5A),
    Color(0xFF3C7D8C),
    Color(0xFFB0507A),
    Color(0xFFD97757),
    Color(0xFFC08A2B),
    Color(0xFF5E8C31),
)

/** Persists theme mode, Material Design 3 Monet toggle, seed color, palette style, and typography preferences. */
object GlyphAppearancePrefs {
    private const val PREFS = "settings"
    private const val KEY_COLOR_MODE = "nothing_color_mode"
    private const val KEY_USE_MONET = "theme_use_monet"
    private const val KEY_STYLE_VARIANT = "theme_style_variant"
    private const val KEY_SEED_COLOR = "theme_seed_color"
    private const val KEY_SERIF_TITLES = "theme_serif_titles"

    val DefaultSeedColor: Color = Color(0xFF4A6FA5)

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

    fun getUseMonet(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_MONET, true)

    fun setUseMonet(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_USE_MONET, enabled)
        }
    }

    fun getStyleVariant(context: Context): GlyphStyleVariant {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STYLE_VARIANT, GlyphStyleVariant.TONAL.name)
        return GlyphStyleVariant.entries.firstOrNull { it.name == raw } ?: GlyphStyleVariant.TONAL
    }

    fun setStyleVariant(context: Context, variant: GlyphStyleVariant) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_STYLE_VARIANT, variant.name)
        }
    }

    fun getSeedColor(context: Context): Color {
        val argb = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SEED_COLOR, DefaultSeedColor.toArgb())
        return Color(argb).copy(alpha = 1f)
    }

    fun setSeedColor(context: Context, color: Color) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_SEED_COLOR, color.copy(alpha = 1f).toArgb())
        }
    }

    fun getSerifTitles(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERIF_TITLES, false)

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
 * Single root theme entry point combining Mochi's Material Design 3 [ThemeController] (supporting
 * Android 12+ Monet wallpaper dynamic colors and custom HCT seed palettes) with Nothing's typography
 * (`Inter` + optional serif titles) and optional monochrome neutral surface mode.
 */
@Composable
fun GlyphAppTheme(
    colorMode: GlyphColorMode,
    useMonet: Boolean,
    styleVariant: GlyphStyleVariant,
    seedColor: Color,
    serifTitles: Boolean,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = colorMode.resolveDark(systemDark)
    val schemeMode = colorMode.toColorSchemeMode(useMonet)
    val mochiPaletteStyle = styleVariant.toMochiPaletteStyle()

    val themeController = remember {
        ThemeController(
            colorSchemeMode = schemeMode,
            keyColor = seedColor,
            paletteStyle = mochiPaletteStyle,
        )
    }
    SideEffect {
        themeController.colorSchemeMode = schemeMode
        themeController.keyColor = seedColor
        themeController.paletteStyle = mochiPaletteStyle
    }

    val md3Scheme = themeController.currentColorScheme()
    val targetColors = remember(styleVariant, isDark, md3Scheme, useMonet, seedColor) {
        if (styleVariant != GlyphStyleVariant.MONOCHROME) {
            md3Scheme
        } else {
            val baseMono = nothingColorScheme(isDark)
            if (!useMonet && seedColor == NothingColors.Accent) {
                baseMono
            } else {
                val accent = if (useMonet) md3Scheme.primary else seedColor
                val onAccent = if (useMonet) md3Scheme.onPrimary else Color.White
                baseMono.copy(
                    primary = accent,
                    onPrimary = onAccent,
                    tertiary = md3Scheme.tertiary,
                    onTertiary = md3Scheme.onTertiary,
                    inversePrimary = md3Scheme.inversePrimary,
                )
            }
        }
    }

    val controlPalette = remember(styleVariant, isDark, targetColors.primary) {
        if (styleVariant == GlyphStyleVariant.MONOCHROME) {
            nothingControlPalette(isDark).copy(tabSelected = targetColors.primary)
        } else {
            null
        }
    }

    val textStyles = remember(serifTitles) { nothingTextStyles(serifDisplay = serifTitles) }
    val materials = remember { MochiMaterials() }
    val revealState = rememberMochiThemeRevealState()

    MochiThemeRevealHost(state = revealState) {
        val animatedColors = animateMochiColorScheme(
            target = targetColors,
            animate = !revealState.isRevealing,
        )
        CompositionLocalProvider(
            LocalMochiControlPalette provides controlPalette,
            LocalGlyphThemeReveal provides revealState,
        ) {
            MochiTheme(
                colors = animatedColors,
                textStyles = textStyles,
                materials = materials,
            ) {
                GlyphHighRefreshRateEffect()
                ProvideMochiOverscroll(content = content)
            }
        }
    }
}

/**
 * Requests the highest native display refresh rate matching the current physical resolution
 * (e.g. 120Hz on Nothing Phone (2) LTPO OLED) without enabling `MochiGraphicsLevel.Extra`'s
 * 1.5x blur radius penalty.
 */
@Composable
private fun GlyphHighRefreshRateEffect() {
    val view = LocalView.current
    val graphicsLevel = MochiGraphics.level
    DisposableEffect(view, graphicsLevel) {
        val window = view.context.findActivity()?.window
        val display = view.display
        if (window == null || display == null) return@DisposableEffect onDispose { }
        val savedModeId = window.attributes.preferredDisplayModeId
        val current = display.mode
        val highRefreshMode = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
        if (highRefreshMode != null && highRefreshMode.refreshRate > 60.5f) {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = highRefreshMode.modeId
            }
        }
        onDispose {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = savedModeId
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
