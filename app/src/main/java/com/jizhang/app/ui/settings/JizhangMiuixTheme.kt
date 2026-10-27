package com.jizhang.app.ui.settings

import com.jizhang.app.ui.glass.ProvideLiquidMaterial

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.jizhang.app.R
import com.jizhang.app.data.prefs.GlassStyle
import com.jizhang.app.ui.common.ATTR_ON_PRIMARY
import com.jizhang.app.ui.common.ATTR_ON_SURFACE
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.ATTR_PRIMARY
import com.jizhang.app.ui.common.ATTR_SURFACE
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.ui.common.rememberGlassStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 应用内 miuix 主题封装。
 *
 * 统一接入 MiSans 字体，并按系统深浅色切换 miuix 默认配色。
 *
 * ★ v1.51：miuix 自带的默认配色（background 纯白 / surfaceContainer 纯白）
 * 和应用的调色板完全对不上 —— 统计页曾因此变成「白卡片贴白底」一片空白。
 * 这里把 miuix 的**页面底色 / 卡片色 / 主色**逐个映射到应用 View 主题的
 * 对应 attr（深浅色与八套配色由 DayNight + Palette overlay 自动带入），
 * 让「MiuixTheme.colorScheme.background」在任何配色下都和应用 windowBackground 一致。
 */
@Composable
fun JizhangMiuixTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    val isClear = rememberGlassStyle() == GlassStyle.CLEAR
    val secondaryText = themeColor(ATTR_ON_SURFACE_VARIANT)
    val colors = base.copy(
        primary = themeColor(ATTR_PRIMARY),
        onPrimary = themeColor(ATTR_ON_PRIMARY),
        background = themeColor(ATTR_SURFACE),
        onBackground = themeColor(ATTR_ON_SURFACE),
        onBackgroundVariant = secondaryText,
        surface = themeColor(R.attr.jizhangCardBackgroundColor),
        onSurface = themeColor(ATTR_ON_SURFACE),
        onSurfaceSecondary = secondaryText,
        onSurfaceVariantSummary = if (isClear) secondaryText else base.onSurfaceVariantSummary,
        onSurfaceVariantActions = if (isClear) secondaryText else base.onSurfaceVariantActions,
        surfaceContainer = themeColor(R.attr.jizhangCardBackgroundColor),
        onSurfaceContainer = themeColor(ATTR_ON_SURFACE),
        onSurfaceContainerVariant = secondaryText,
        surfaceContainerHigh = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh),
        surfaceContainerHighest = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest),
        secondaryVariant = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh),
        onSecondaryVariant = themeColor(ATTR_ON_SURFACE),
        secondaryContainer = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh),
        onSecondaryContainer = secondaryText,
        secondaryContainerVariant = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest),
        onSecondaryContainerVariant = secondaryText,
        primaryVariant = themeColor(com.google.android.material.R.attr.colorPrimaryContainer),
        onPrimaryVariant = themeColor(ATTR_PRIMARY),
        dividerLine = themeColor(com.google.android.material.R.attr.colorOutlineVariant),
        outline = themeColor(com.google.android.material.R.attr.colorOutline),
        disabledPrimaryButton = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh),
        disabledOnPrimaryButton = secondaryText,
    )
    MiuixTheme(
        colors = colors,
        textStyles = MiSansTextStyles,
    ) {
        // 静态 MiuixTheme(colors) 不提供文字前景色；输入框需要随应用深浅色切换。
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            ProvideLiquidMaterial(content)
        }
    }
}

private val MiSansTextStyles by lazy { textStylesWithMiSans() }

private fun textStylesWithMiSans(): TextStyles {
    val miSans = FontFamily(Font(R.font.misans_regular))
    val base = defaultTextStyles()
    return TextStyles(
        main = base.main.copy(fontFamily = miSans),
        paragraph = base.paragraph.copy(fontFamily = miSans),
        body1 = base.body1.copy(fontFamily = miSans),
        body2 = base.body2.copy(fontFamily = miSans),
        button = base.button.copy(fontFamily = miSans),
        footnote1 = base.footnote1.copy(fontFamily = miSans),
        footnote2 = base.footnote2.copy(fontFamily = miSans),
        headline1 = base.headline1.copy(fontFamily = miSans),
        headline2 = base.headline2.copy(fontFamily = miSans),
        subtitle = base.subtitle.copy(fontFamily = miSans),
        title1 = base.title1.copy(fontFamily = miSans),
        title2 = base.title2.copy(fontFamily = miSans),
        title3 = base.title3.copy(fontFamily = miSans),
        title4 = base.title4.copy(fontFamily = miSans),
    )
}
