package com.richard.glyphcontroller

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.defaultTextStyles

internal object GlyphUiSize {
    val PrimaryButton = 56.dp
    val Button = 52.dp
    val ZoneButton = 52.dp
}

@Composable
fun GlyphAppTheme(content: @Composable () -> Unit) {
    // Use Miuix's built-in palettes; System only selects light or dark mode.
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    val textStyles = remember {
        val standard = defaultTextStyles()
        standard.copy(
            main = standard.main.copy(letterSpacing = 0.3.sp, lineHeight = 1.35.em,
                fontWeight = FontWeight.Medium),
            paragraph = standard.paragraph.copy(letterSpacing = 0.3.sp, lineHeight = 1.4.em),
            body1 = standard.body1.copy(letterSpacing = 0.3.sp, lineHeight = 1.35.em),
            body2 = standard.body2.copy(letterSpacing = 0.3.sp, lineHeight = 1.4.em),
            button = standard.button.copy(letterSpacing = 0.4.sp,
                fontWeight = FontWeight.SemiBold),
            footnote1 = standard.footnote1.copy(letterSpacing = 0.2.sp, lineHeight = 1.35.em),
            footnote2 = standard.footnote2.copy(letterSpacing = 0.2.sp, lineHeight = 1.3.em),
            headline1 = standard.headline1.copy(letterSpacing = 0.3.sp),
            headline2 = standard.headline2.copy(letterSpacing = 0.3.sp),
            subtitle = standard.subtitle.copy(letterSpacing = 0.5.sp),
            title1 = standard.title1.copy(letterSpacing = 0.3.sp),
            title2 = standard.title2.copy(letterSpacing = 0.3.sp),
            title3 = standard.title3.copy(letterSpacing = 0.3.sp),
            title4 = standard.title4.copy(letterSpacing = 0.3.sp),
        )
    }
    MiuixTheme(controller = controller, textStyles = textStyles, content = content)
}
