/*
 * 底栏的内容层：页签的图标、文字、角标。
 *
 * v1.36 从 LiquidBottomBar.kt 拆出来 —— 那个文件原来同时装着玻璃容器、
 * 拖拽动画、折射层和页签内容四件事，改任何一个都要在 700 行里翻。
 * 拆分后：这一层只管"画什么"，玻璃与布局在 LiquidBottomBar 里。
 *
 * 形态：
 *   · v1.36 曾对齐 SleepDown 做成「每槽固定宽、占满内容区高」，v1.37 改回
 *     近全宽后是 **weight(1f) 平分内容区**（槽宽 = (容器宽 - 2×inset) / 槽数，
 *     LiquidBottomBar 里用它当拖拽的一把尺）。两种模式共用本文件，只换
 *     weight / width，内容排版不变。
 *   · 图标 + 文字**常驻**（原来只有选中项显示文字 —— 未选中项在迷你胶囊里
 *     没有文字会变成一排含义不明的图标）；
 *   · 「记一笔」从"半宽小蓝胶囊"改成与页签同构的竖排槽位，用主题色区分，
 *     不再是玻璃容器中间的一块异质元素。
 */
package com.jizhang.app.ui.glass

import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.R
import com.kyant.shapes.Capsule

/** 单个页签的展示数据。文案由调用方从 strings.xml 提供，避免 Compose 资源额外依赖。 */
internal data class GlassTab(
    val id: Int,
    val label: String,
    val icon: ImageVector,
    /** 选中态图标。MD3 导航栏的惯例是未选中用轮廓、选中用实心。 */
    val iconSelected: ImageVector = icon,
)

/**
 * 页签内容的整体缩放。折射层在按压时会把内容放大 1.2 倍（液态凸起），
 * 通过这个 local 把缩放传给可见层与折射层两份内容，保证两者始终等比。
 */
internal val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }

internal const val TAB_PENDING_ID = 2

/**
 * 底栏页签文字用的字体。
 *
 * 与 XML 侧保持一致：主题 `fontFamily` 现在是 @font/misans（v1.50 起唯一风格），
 * 而 MiSans 只打包了 Regular 一档，Medium/Bold 由系统在 Regular 上合成加粗。
 * Compose 的 BasicText 不认 View 主题里的 fontFamily，必须在这里显式指定，
 * 否则底栏文字会掉回系统字体，与其它页面不一致。
 */
private val GlassLabelFontFamily = FontFamily(Font(R.font.misans_regular))

internal fun Context.glassTabs(): List<GlassTab> = listOf(
    GlassTab(0, getString(R.string.tab_ledger), IconLedger, IconLedgerFilled),
    GlassTab(1, getString(R.string.tab_stats), IconStats, IconStatsFilled),
    GlassTab(2, getString(R.string.tab_pending), IconPending, IconPendingFilled),
    GlassTab(3, getString(R.string.tab_settings), IconSettings, IconSettingsFilled),
)

/**
 * 绘制一层页签内容。
 *
 * 可见层与折射层必须绘制完全相同的图标与文字，
 * 否则两者位置不一致时会出现重影。
 */
@Composable
internal fun RowScope.GlassTabLayer(
    tabs: List<GlassTab>,
    currentTabIndex: Int,
    pendingBadgeCount: Int,
    accentColor: Color,
    contentColor: Color,
    secondaryContentColor: Color,
    isLightTheme: Boolean,
    clearLightLabels: Boolean,
    interactive: Boolean = false,
    addLabel: String = "",
    onAddClick: (() -> Unit)? = null,
    onSelected: ((Int) -> Unit)? = null,
) {
    val addSlotIndex = tabs.size / 2
    val labelStyle = MiuixTheme.textStyles.body2.copy(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = GlassLabelFontFamily,
        shadow = if (clearLightLabels) Shadow(
            color = Color.White.copy(alpha = .95f),
            offset = Offset.Zero,
            blurRadius = with(LocalDensity.current) { 1.dp.toPx() },
        ) else null,
    )
    tabs.forEachIndexed { index, tab ->
        if (index == addSlotIndex) {
            GlassAddSlot(
                label = addLabel,
                accentColor = accentColor,
                labelStyle = labelStyle,
                interactive = interactive,
                onClick = onAddClick,
            )
        }
        val selected = index == currentTabIndex
        GlassTabSlot(
            tab = tab,
            selected = selected,
            badgeCount = if (tab.id == TAB_PENDING_ID) pendingBadgeCount else 0,
            accentColor = accentColor,
            contentColor = contentColor,
            secondaryContentColor = secondaryContentColor,
            secondaryLabelColor = if (clearLightLabels) GlassBarTokens.ClearLabelColorLight
                                  else secondaryContentColor,
            labelStyle = labelStyle,
            isLightTheme = isLightTheme,
            interactive = interactive,
            onSelected = onSelected?.let { select -> { select(index) } },
        )
    }
}

/** 底栏中间的「记一笔」槽位：与页签同构（图标 + 文字竖排），用主题色区分。 */
@Composable
private fun RowScope.GlassAddSlot(
    label: String,
    accentColor: Color,
    labelStyle: TextStyle,
    interactive: Boolean,
    onClick: (() -> Unit)?,
) {
    val scale = LocalLiquidBottomTabScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(1f, 600f, 0.72f),
        label = "glassAddScale",
    )
    val icon = rememberVectorPainter(IconAddBold)

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val currentScale = scale()
                scaleX = currentScale
                scaleY = currentScale
            }
            .let { base ->
                if (!interactive || onClick == null) {
                    base
                } else {
                    base
                        .semantics(mergeDescendants = true) {
                            contentDescription = label
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                }
            }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .paint(icon, colorFilter = ColorFilter.tint(accentColor)),
        )
        Box(
            Modifier.heightIn(min = GlassBarTokens.LabelHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = labelStyle.copy(color = accentColor),
            )
        }
    }
}

@Composable
private fun RowScope.GlassTabSlot(
    tab: GlassTab,
    selected: Boolean,
    badgeCount: Int,
    accentColor: Color,
    contentColor: Color,
    secondaryContentColor: Color,
    secondaryLabelColor: Color,
    labelStyle: TextStyle,
    isLightTheme: Boolean,
    interactive: Boolean,
    onSelected: (() -> Unit)?,
) {
    val scale = LocalLiquidBottomTabScale.current
    val icon = rememberVectorPainter(if (selected) tab.iconSelected else tab.icon)
    val iconColor = if (selected) accentColor else secondaryContentColor
    val labelColor = if (selected) contentColor else secondaryLabelColor
    val badgeColor = if (isLightTheme) Color(0xFFBA1A1A) else Color(0xFFFFB4AB)

    Column(
        modifier = Modifier
            .clip(Capsule())
            .let { base ->
                if (!interactive || onSelected == null) {
                    base
                } else {
                    base
                        .semantics(mergeDescendants = true) {
                            contentDescription = tab.label
                            role = Role.Tab
                            this.selected = selected
                        }
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                            onClick = onSelected,
                        )
                }
            }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val currentScale = scale()
                scaleX = currentScale
                scaleY = currentScale
            },
        // 图标与文字常驻（v1.36 起）：未选中用次级色，选中用主题色系。
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier
                    .size(24.dp)
                    .paint(icon, colorFilter = ColorFilter.tint(iconColor)),
            )
            if (badgeCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer { translationX = 8.dp.toPx(); translationY = (-8).dp.toPx() }
                        .widthIn(min = 16.dp)
                        .height(16.dp)
                        .clip(Capsule())
                        .paint(
                            rememberVectorPainter(BadgeCircle),
                            colorFilter = ColorFilter.tint(badgeColor),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        style = MiuixTheme.textStyles.body2.copy(
                            color = Color.White,
                            fontSize = if (badgeCount > 99) 7.sp else 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GlassLabelFontFamily,
                        ),
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
        }
        Box(
            Modifier.heightIn(min = GlassBarTokens.LabelHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tab.label,
                style = labelStyle.copy(color = labelColor),
            )
        }
    }
}
