package com.jizhang.app.ui.common

import com.jizhang.app.ui.glass.LiquidPage
import com.jizhang.app.ui.glass.LocalLiquidScene
import com.jizhang.app.ui.glass.LocalLiquidBackdrop
import com.jizhang.app.ui.glass.LiquidSurfaceRole
import com.jizhang.app.ui.glass.liquidSurface
import com.jizhang.app.ui.glass.StatusBarGlassScrim
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

import top.yukonga.miuix.kmp.basic.ButtonDefaults
import com.jizhang.app.ui.glass.LiquidTextButton as TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.R
import com.jizhang.app.data.prefs.GlassStyle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 二级页面（带返回的独立 Activity）共用的 Compose 脚手架（v1.53）。
 *
 * 原来这些页面各写一份 MaterialToolbar + 可选底按钮的 XML，改 Compose 后
 * 顶栏几何、返回键、底按钮这些必须只有一份 —— 与 v1.50「token 只有一份」
 * 是同一条原则（各写一份必然分叉）。
 *
 * 背景铺到状态栏下，内容与悬浮控件分别避让系统图标。状态栏模糊与主页共用同一实现。
 */

/** 页面级提示条：原来的 @drawable/bg_chip（colorPrimaryContainer + 8dp 圆角）。 */
@Composable
internal fun HintBanner(text: String, modifier: Modifier = Modifier) {
    val shape = remember { RoundedCornerShape(8.dp) }
    val tint = themeColor(com.google.android.material.R.attr.colorPrimaryContainer)
    val clear = LocalLiquidScene.current?.style == GlassStyle.CLEAR
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (clear) Modifier.liquidSurface(shape, tint = tint, role = LiquidSurfaceRole.Control)
                else Modifier.background(tint))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = themeColor(ATTR_ON_SURFACE_VARIANT),
        )
    }
}

/** 空态：大 emoji + 主文案 + 副文案，与明细页 EmptyView 同一几何。 */
@Composable
internal fun EmptyState(
    emoji: String,
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = emoji, style = MiuixTheme.textStyles.title1.copy(fontSize = 44.sp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
            color = themeColor(ATTR_ON_SURFACE),
        )
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                style = MiuixTheme.textStyles.body2,
                color = themeColor(ATTR_ON_SURFACE_VARIANT),
            )
        }
    }
}

/** 文本按钮：原来 Widget.Material3.Button.TextButton。 */
@Composable
internal fun TextActionButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false,
) {
    TextButton(
        text = label, onClick = onClick, modifier = modifier, minHeight = 44.dp,
        colors = ButtonDefaults.textButtonColors(color = Color.Transparent,
            textColor = if (destructive) expenseColor() else MiuixTheme.colorScheme.primary),
    )
}

/** 小徽标（原 @drawable/bg_badge：colorSurfaceContainerHighest + 4dp 圆角）。 */
@Composable
internal fun Badge(text: String, color: Color) {
    val shape = remember { RoundedCornerShape(4.dp) }
    val tint = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
    val clear = LocalLiquidScene.current?.style == GlassStyle.CLEAR
    Box(
        modifier = Modifier
            .clip(shape)
            .then(if (clear) Modifier.liquidSurface(shape, tint = tint, role = LiquidSurfaceRole.Control)
                else Modifier.background(tint))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
            color = color,
            maxLines = 1,
        )
    }
}

/** 二级页根容器：统一页面底色（应用调色板）+ 铺满。 */
@Composable
internal fun SecondaryPage(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    headerHeight: Dp = 76.dp,
    header: (@Composable (statusBarHeight: Dp) -> Unit)? = null,
    overlay: (@Composable BoxScope.(topInset: Dp) -> Unit)? = null,
    content: @Composable (topInset: Dp) -> Unit,
) {
    val hasHeader = title != null && onBack != null
    val statusBarHeight = rememberAdaptiveInsets().top
    val toolbarHeight = if (hasHeader || header != null) headerHeight else 0.dp
    val topInset = statusBarHeight + toolbarHeight
    val scene = LocalLiquidScene.current
    val backdrop = scene?.contentBackdrop
    Box(Modifier.fillMaxSize()) {
        // Record the scrolling page once; floating controls are sibling consumers,
        // so their own glass never enters the source they sample.
        LiquidPage(recordContent = true) {
            content(topInset)
        }
        if (scene != null && backdrop != null) {
            if (toolbarHeight > 0.dp) {
                // Full screen width, from y=0 through the title's centre line.
                // A single progressive pass fades across the remaining toolbar.
                StatusBarGlassScrim(
                    backdrop = backdrop,
                    statusBarHeight = statusBarHeight + toolbarHeight / 2,
                    fadeHeight = toolbarHeight / 2,
                    maxBlur = 10.dp,
                )
            } else {
                StatusBarGlassScrim(backdrop, statusBarHeight)
            }
        }
        CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
            overlay?.invoke(this@Box, topInset)
            if (header != null) {
                header(statusBarHeight)
            } else if (hasHeader) {
                FloatingSecondaryHeader(title, onBack, Modifier.padding(top = statusBarHeight))
            }
        }
    }
}

@Composable
private fun FloatingSecondaryHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(76.dp)) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 76.dp).align(Alignment.Center),
            style = MiuixTheme.textStyles.title3.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            color = MiuixTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        top.yukonga.miuix.kmp.basic.IconButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp).size(48.dp)
                .liquidSurface(CircleShape, role = LiquidSurfaceRole.Navigation),
            backgroundColor = Color.Transparent,
        ) {
            androidx.compose.foundation.Image(painterResource(R.drawable.ic_floating_back), stringResource(R.string.action_back),
                Modifier.size(24.dp), colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onBackground))
        }
    }
}

/** View root owns side/IME insets; SecondaryPage owns top insets inside its scrolling content. */
internal fun android.content.Context.secondaryComposeRoot(
    content: @androidx.compose.runtime.Composable () -> Unit,
): android.widget.FrameLayout {
    val compose = androidx.compose.ui.platform.ComposeView(this).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
        )
        setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        setContent { com.jizhang.app.ui.settings.JizhangMiuixTheme(content) }
    }
    return android.widget.FrameLayout(this).apply {
        addView(compose)
    }
}

