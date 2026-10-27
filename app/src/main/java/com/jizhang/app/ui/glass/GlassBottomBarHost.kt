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

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.withSave
import com.jizhang.app.R
import com.jizhang.app.data.prefs.GlassStyle
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.ui.common.rememberAdaptiveInsets
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.util.glassEffectsEnabled
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop

/**
 * 液态玻璃底栏的宿主。
 *
 * 该 View 只测量 Compose 子树需要的底部高度，不会遮住上方页面。
 * [contentView] 是页面真实内容容器，每帧按窗口坐标差平移后绘制进
 * Backdrop，所以玻璃折射的是真实页面，而不是一块模拟底色。
 */
class GlassBottomBarHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** 采样源：承载 Fragment 的内容容器。 */
    var contentView: android.view.View? = null

    private val tabIndexState = mutableIntStateOf(0)
    private val badgeCountState = mutableIntStateOf(0)
    private val revealState = mutableFloatStateOf(1f)

    /** 实际显示中的提示条，各自持有占用，直到最后一条退场才恢复底栏。 */
    private val snackbarOwners = HashSet<Any>()

    internal fun setSnackbarVisible(owner: Any, visible: Boolean) {
        val wasSuppressed = snackbarOwners.isNotEmpty()
        if (visible) snackbarOwners.add(owner) else snackbarOwners.remove(owner)
        val suppressed = snackbarOwners.isNotEmpty()
        if (suppressed == wasSuppressed) return

        // 提示入场前立即隐藏整个宿主，避免淡出期间仍遮住文字；不改变测量高度。
        // 滚动显隐仍独立运行，提示结束后自然回到当前滚动应有的状态。
        animate().cancel()
        importantForAccessibility = if (suppressed) {
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        if (suppressed) {
            alpha = 0f
            translationY = GlassBarTokens.SnackbarReturnOffset.value * resources.displayMetrics.density
        } else {
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(GlassBarTokens.SnackbarReturnAnimMs)
                .setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f))
                .start()
        }
    }

    /**
     * 底栏显隐进度：1 = 完全显示，0 = 完全收起。
     *
     * 由滚动方向驱动（向下滚收起、向上滚恢复），具体判定在 Activity 侧。
     * 用 snapshot state 承载，Activity 侧改动才能驱动 Compose 重组。
     *
     * 注意这里是**纯视觉**量：收起只改透明度，不改测量高度 ——
     * 各页通过 GlassBarInsets 读取实际占用高度，
     * 一旦跟着收起变化，所有页面的尾部留白都会跟着抖。
     */
    var revealProgress: Float
        get() = revealState.floatValue
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (revealState.floatValue != clamped) revealState.floatValue = clamped
        }

    /** 当前页签，0..3。用 snapshot state 承载，Activity 侧改动才能驱动 Compose 重组。 */
    var selectedTabIndex: Int
        get() = tabIndexState.intValue
        set(value) {
            if (tabIndexState.intValue != value) tabIndexState.intValue = value
        }

    /** 待确认角标数量。 */
    var pendingBadgeCount: Int
        get() = badgeCountState.intValue
        set(value) {
            if (badgeCountState.intValue != value) badgeCountState.intValue = value
        }

    /** 页签点击回调。 */
    var onTabSelected: ((Int) -> Unit)? = null

    /** 底栏中间「记一笔」按钮点击回调。 */
    var onAddClick: (() -> Unit)? = null

    private var composeInstalled = false

    init {
        setWillNotDraw(true)
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // XML 中即使写 match_parent，也强制按内容高度测量，避免覆盖整屏。
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
    }

    /**
     * 底栏收起后不再接收触摸。
     *
     * 为什么必须显式处理：收起是**纯 alpha** 动画（几何不能动，见 [revealProgress]），
     * 而 Compose 的 alpha=0 只影响绘制、**不影响命中测试** —— 底栏看不见了，
     * 里面的页签和「记一笔」按钮照样能被点到。那样用户会在底部空白处误触，
     * 凭空跳页或弹出记一笔界面。这里直接返回 false，让事件按 z 序交给下层页面。
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (snackbarOwners.isNotEmpty()) return false
        if (alpha < GlassBarTokens.TouchAlphaCutoff) return false
        if (revealState.floatValue < GlassBarTokens.TouchAlphaCutoff) return false
        return super.dispatchTouchEvent(ev)
    }

    /** 在 setContentView 之后调用，把 Compose 底栏挂进本 View。 */
    fun installCompose() {
        if (composeInstalled) return
        composeInstalled = true

        val composeView = ComposeView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            }
            setContent {
                com.jizhang.app.ui.settings.JizhangMiuixTheme { GlassBarContent() }
            }
        }
        addView(composeView)
    }

    @Composable
    private fun GlassBarContent() {
        val context = this.context
        val host = this
        val safeInsets = rememberAdaptiveInsets(
            minimumBottomClearance = GlassBarTokens.MinimumBottomClearance,
            excludeImeFromBottom = true,
        )
        val glassStyle = remember(context) { SettingsStore(context).glassStyle }
        val effectsEnabled = remember(context) { context.glassEffectsEnabled() }
        val lightTheme = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES

        // 颜色取自当前页面主题，这样五种 MD3 配色都能即时作用到底栏。
        val accent = Color(context.themeColor(com.google.android.material.R.attr.colorPrimary))
        val onSurface = Color(context.themeColor(com.google.android.material.R.attr.colorOnSurface))
        val onSurfaceVariant =
            Color(context.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        val surface = Color(context.themeColor(com.google.android.material.R.attr.colorSurface))

        // 内容 View 已经先于玻璃层完成绘制，因此每次渲染直接读取它当前状态即可，
        // 不需要额外的滚动监听或逐帧状态刷新。
        val backdrop = if (effectsEnabled) rememberCanvasBackdrop {
            val content = contentView ?: return@rememberCanvasBackdrop
            val contentLocation = IntArray(2)
            content.getLocationInWindow(contentLocation)
            val hostLocation = IntArray(2)
            host.getLocationInWindow(hostLocation)

            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.withSave {
                    // 页面本身大多是透明背景，先铺一层主题表面色，
                    // 模糊与折射才能得到真实页面质感，而不是空白。
                    drawColor(surface.toArgb())
                    translate(
                        (contentLocation[0] - hostLocation[0]).toFloat(),
                        (contentLocation[1] - hostLocation[1]).toFloat(),
                    )
                    content.draw(this)
                }
            }
        } else null

        // 收起 / 恢复做成动画：滚动方向翻转时状态是跳变的，直接跟随会「啪」一下。
        // 只作用于 alpha，不碰任何尺寸 —— 底栏总高是喂给各页 paddingBottom 的对外契约。
        val reveal by animateFloatAsState(
            targetValue = revealState.floatValue,
            animationSpec = tween(durationMillis = GlassBarTokens.RevealAnimMs),
            label = "bottomBarReveal",
        )

        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = reveal },
        ) {
        // v1.37 起恢复 v1.35 的近全宽排版：胶囊 fillMaxWidth，宿主给左右
        // BarSideGap。v1.36 的迷你居中版随「改回原来大小」撤销。
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            LiquidBottomBar(
                tabs = remember(context) { context.glassTabs() },
                addLabel = context.getString(R.string.action_add),
                selectedTabIndex = { tabIndexState.intValue },
                onTabSelected = { index -> onTabSelected?.invoke(index) },
                onAddClick = { onAddClick?.invoke() },
                backdrop = backdrop,
                glassStyle = if (effectsEnabled) glassStyle else GlassStyle.OPAQUE,
                pendingBadgeCount = badgeCountState.intValue,
                isLightTheme = lightTheme,
                accentColor = accent,
                contentColor = onSurface,
                secondaryContentColor = onSurfaceVariant,
                modifier = Modifier.padding(
                    start = GlassBarTokens.BarSideGap,
                    end = GlassBarTokens.BarSideGap,
                    top = GlassBarTokens.BarTopGap,
                ),
            )
        }
            // 只补当前窗口尚未避让的区域；键盘或父布局已抬高底栏时不再重复加导航栏。
            // 小白条关闭后仍保留最小悬浮空间；不缓存旧导航栏高度。
            // 页面尾部订阅整个宿主的实测占用，随系统栏变化自动避让。
            Spacer(Modifier.height(safeInsets.bottom))
        }
    }
}
