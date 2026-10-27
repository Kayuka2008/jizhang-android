/*
 * Copyright 2025 Kyant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.jizhang.app.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.jizhang.app.data.prefs.GlassStyle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 液态玻璃底栏。
 *
 * Adapted from Kyant0/AndroidLiquidGlass (Apache-2.0).
 *
 * The selected item is a real liquid-glass capsule that the user can drag
 * horizontally. On release it snaps to the nearest tab with a spring, matching
 * the interaction model of the official catalog component.
 *
 * [tabs] 是真实页签；底栏在正中间额外插入一个「记一笔」槽位，该槽位不参与
 * 选中与拖动吸附，因此选中索引空间仍是 [tabs] 的下标。
 *
 * 三层共用相同内容几何：底层玻璃 Row、折射内容 Row、可拖动选中胶囊。
 */
@Composable
internal fun LiquidBottomBar(
    tabs: List<GlassTab>,
    addLabel: String,
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    onAddClick: () -> Unit,
    backdrop: Backdrop?,
    glassStyle: GlassStyle,
    pendingBadgeCount: Int,
    isLightTheme: Boolean,
    accentColor: Color,
    contentColor: Color,
    secondaryContentColor: Color,
    modifier: Modifier = Modifier,
) {
    val tabCount = tabs.size

    /** 「记一笔」槽位插在正中间；页签的视觉位置要跳过它。 */
    val addTabIndex = tabCount / 2

    /** 总槽数（页签 + 记一笔）。加号槽与页签等宽，拖拽坐标系里占 1 个单位。 */
    val totalWeight = tabCount + 1

    /** 页签 [tabIndex] 左边缘在「槽位坐标系」里的位置；拖动与吸附都在这个坐标系里计算。 */
    fun tabLeftUnit(tabIndex: Int): Float =
        tabIndex + if (tabIndex >= addTabIndex) 1f else 0f

    // 底栏玻璃的底色：**把主题色混进中性底色**（对齐 2.9.8，配比在 Tokens）。
    // 为什么逐通道 lerp 而不是 Color.copy(alpha)：copy 只改 alpha 不改色相，
    // 要的是色相也向主题色偏移。
    val baseColor = if (isLightTheme) GlassBarTokens.BaseColorLight else GlassBarTokens.BaseColorDark
    val clearLightLabels = glassStyle == GlassStyle.CLEAR && isLightTheme
    val selectionTint = if (isLightTheme) Color.Black.copy(alpha = .08f)
                        else Color.White.copy(alpha = .10f)
    val accentMix = if (isLightTheme) GlassBarTokens.AccentMixLight else GlassBarTokens.AccentMixDark
    val containerAlpha = when (glassStyle) {
        GlassStyle.OPAQUE -> 1f
        GlassStyle.CLEAR -> if (isLightTheme) GlassBarTokens.ClearContainerAlphaLight else GlassBarTokens.ClearContainerAlphaDark
        GlassStyle.FROSTED -> GlassBarTokens.ContainerAlpha
    }
    val containerColor = Color(
        red = lerp(baseColor.red, accentColor.red, accentMix),
        green = lerp(baseColor.green, accentColor.green, accentMix),
        blue = lerp(baseColor.blue, accentColor.blue, accentMix),
        alpha = containerAlpha,
    )
    val tabsBackdrop = if (backdrop != null) rememberLayerBackdrop() else null

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        /** 玻璃内容区的水平内边距（像素）。槽宽要把两边各减掉这份再平分。 */
        val insetPx = with(density) { GlassBarTokens.ContentInset.toPx() }

        /** 单槽宽（像素）= (容器宽 - 2×inset) / 槽数。拖拽几何全部以它为一把尺。 */
        val unitWidth = (constraints.maxWidth.toFloat() - insetPx * 2f) / totalWeight

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentTabIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex().coerceIn(0, tabCount - 1))
        }

        val offsetAnimation = remember { Animatable(0f) }
        val dampedDragAnimation = remember(animationScope, tabCount, unitWidth) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = tabLeftUnit(selectedTabIndex().coerceIn(0, tabCount - 1)),
                valueRange = 0f..(totalWeight - 1f),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    // 加号槽位不参与吸附：在像素空间里找最近的页签左边缘。
                    var bestTab = 0
                    var bestDistance = Float.MAX_VALUE
                    for (index in 0 until tabCount) {
                        val distance = abs(tabLeftUnit(index) - targetValue) * unitWidth
                        if (distance < bestDistance) {
                            bestDistance = distance
                            bestTab = index
                        }
                    }
                    currentTabIndex = bestTab
                    animateToValue(tabLeftUnit(currentTabIndex))
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / unitWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, totalWeight - 1f),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }

        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentTabIndex = index.coerceIn(0, tabCount - 1)
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentTabIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(tabLeftUnit(index))
                    onTabSelected(index)
                }
        }

        // 拖动时整条容器跟着手势做一点弹性位移（3dp 封顶），松手弹回。
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 3f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val interactiveHighlight = if (backdrop != null) remember(animationScope, unitWidth, insetPx) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) {
                            insetPx + (dampedDragAnimation.value + 0.5f) * unitWidth + panelOffset
                        } else {
                            size.width - insetPx -
                                (dampedDragAnimation.value + 0.5f) * unitWidth + panelOffset
                        },
                        size.height / 2f,
                    )
                },
            )
        } else null

        // ---- 第 1 层：底层玻璃（唯一可见的容器）。----
        // 近全宽（fillMaxWidth，宿主给左右 BarSideGap），四边留 ContentInset。
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .then(if (backdrop == null) Modifier.background(containerColor, Capsule()) else Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        // 官方 LiquidBottomTabs 的配方（v1.37 对齐）：
                        // vibrancy 提升透过内容的饱和度，是「折射看得清」的一半前提
                        // （另一半是底色降到 0.4，见 Tokens）。
                        vibrancy()
                        blur(GlassBarTokens.BlurRadius.toPx())
                        lens(
                            GlassBarTokens.LensRadius.toPx(),
                            GlassBarTokens.LensRadius.toPx(),
                        )
                    },
                    // 高光描边（v1.35 加回，参数收进 Tokens）：
                    // 库默认 alpha=1 是完全不透明的实边（v1.28 前用户嫌的那条"描边"），
                    // 这里压到 HighlightAlpha 做成"隐约勾一圈"。
                    highlight = {
                        Highlight.Plain.copy(
                            width = GlassBarTokens.HighlightWidth,
                            blurRadius = GlassBarTokens.HighlightWidth,
                            alpha = GlassBarTokens.HighlightAlpha,
                        )
                    },
                    // 阴影：紧贴胶囊的一点虚影。offset 必须按当前半径现算 ——
                    // 库的默认 offset 是按 24dp 算死的，copy(radius) 不会跟着变
                    // （v1.35 修掉的失真）。
                    shadow = {
                        Shadow.Default.copy(
                            radius = GlassBarTokens.ShadowRadius,
                            offset = DpOffset(
                                0.dp,
                                GlassBarTokens.ShadowRadius / GlassBarTokens.ShadowOffsetDivisor,
                            ),
                        )
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                ))
                .then(interactiveHighlight?.modifier ?: Modifier)
                .height(GlassBarTokens.BarHeight)
                .fillMaxWidth()
                .padding(GlassBarTokens.ContentInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassTabLayer(
                tabs = tabs,
                currentTabIndex = currentTabIndex,
                pendingBadgeCount = pendingBadgeCount,
                accentColor = accentColor,
                contentColor = contentColor,
                secondaryContentColor = secondaryContentColor,
                isLightTheme = isLightTheme,
                clearLightLabels = clearLightLabels,
                interactive = true,
                addLabel = addLabel,
                onAddClick = onAddClick,
                onSelected = { currentTabIndex = it },
            )
        }

        // ---- 第 2 层：折射层。把落在下方玻璃上的内容再折射一次，形成液态凸起。----
        if (backdrop != null && tabsBackdrop != null) CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(GlassBarTokens.BlurRadius.toPx())
                            lens(
                                GlassBarTokens.LensRadius.toPx() * progress,
                                GlassBarTokens.LensRadius.toPx() * progress,
                            )
                        },
                        highlight = null,
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight?.modifier ?: Modifier)
                    .height(GlassBarTokens.ContentHeight)
                    .fillMaxWidth()
                    .padding(horizontal = GlassBarTokens.ContentInset)
                    .graphicsLayer {
                        // Keep the clear-mode label halo neutral in both content layers.
                        colorFilter = if (clearLightLabels) null else ColorFilter.tint(accentColor)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTabLayer(
                    tabs = tabs,
                    currentTabIndex = currentTabIndex,
                    pendingBadgeCount = pendingBadgeCount,
                    accentColor = accentColor,
                    contentColor = contentColor,
                    secondaryContentColor = secondaryContentColor,
                    isLightTheme = isLightTheme,
                    clearLightLabels = clearLightLabels,
                    // 折射层的"记一笔"也要有文字（v1.36 起那一项带文字了）。
                    // 不传的话这里渲染出一个空文字行 —— 几何上仍对齐，但折射
                    // 纹理里缺了这块内容，选中胶囊拖过它时会少一片。
                    // 不传 onAddClick：本层 alpha=0 且 interactive=false，
                    // 加了点击也不会生效，反而多一个手势节点。
                    addLabel = addLabel,
                )
            }
        }

        // ---- 第 3 层：可拖拽的选中胶囊。----
        Box(
            Modifier
                // ★ 这层 horizontal padding 不能省 —— 它是"对齐"的关键。
                //
                // 底层玻璃 Row 是 fillMaxWidth + 四边 ContentInset，所以内容区
                // 从 x = inset 开始、槽宽 = (容器宽 - 2×inset) / 槽数。
                // 胶囊如果也从这个坐标系起算，就必须先把同样的 inset 让出来，
                // 否则：起点偏左 inset、宽度还多出 2×inset/槽数（因为
                // fillMaxWidth 是按**未减 padding 的全宽**取比例的）。
                // v1.36 迷你化时删过这一行（那版没有水平 inset），v1.37 恢复
                // 近全宽时漏加回来 —— 表现为"选中胶囊和页签错位"。
                //
                // padding 必须在 graphicsLayer **之前**：内层拿到的才是减掉
                // inset 后的坐标系，translationX 与 fillMaxWidth(1/槽数)
                // 才会和 Row 内容区严丝合缝。
                .padding(horizontal = GlassBarTokens.ContentInset)
                .graphicsLayer {
                    translationX =
                        if (isLtr) {
                            dampedDragAnimation.value * unitWidth + panelOffset
                        } else {
                            // ★ 这里**不能**用 size.width：graphicsLayer 在
                            // fillMaxWidth(1f / totalWeight) 之上，它拿到的 size
                            // 是**胶囊自己的宽度**（= unitWidth），不是内容区宽度。
                            // 用 size.width 会退化成 -value * unitWidth，整个翻到左边。
                            // 内容区宽度 = unitWidth × 槽数，直接算，别去问 size。
                            unitWidth * totalWeight -
                                (dampedDragAnimation.value + 1f) * unitWidth + panelOffset
                        }
                }
                .then(interactiveHighlight?.gestureModifier ?: Modifier)
                .then(dampedDragAnimation.modifier)
                .then(if (backdrop == null || tabsBackdrop == null) {
                    Modifier.background(
                        selectionTint,
                        Capsule(),
                    )
                } else Modifier.drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            12.dp.toPx() * progress,
                            16.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    // 拖动胶囊仅保留折射和填色，避免上沿出现跟手移动的亮边/暗边。
                    highlight = null,
                    shadow = null,
                    innerShadow = null,
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            selectionTint,
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                ))
                .height(GlassBarTokens.ContentHeight)
                .fillMaxWidth(1f / totalWeight),
        )
    }
}
