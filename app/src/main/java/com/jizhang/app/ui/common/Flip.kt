package com.jizhang.app.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/*
 * 月份切换动画（v1.58）：明细页与统计页共用一套。
 *
 * 两部分：
 *   1. [FlipLabel] —— 月份文字本身，方向性 depth 进场；
 *   2. [Modifier.flipContent] —— 月份下方那些栏目（汇总卡、分类卡、趋势卡、
 *      明细列表），跟着月份一起做一次更克制的进场。
 *
 * ## 参数来源
 *
 * 照 SleepDown 的 depth 页面动画抄的 —— 详见 [PageFlip] 顶部那段注释
 * （仓库 github.com/xiaomanjun233/SleepDown-Schedule，`res/anim/schedule_depth_*.xml`）：
 *
 *   · 进：alpha 0→1、scale 1.05→1.0、260ms
 *   · 出：alpha 1→0、scale 1.0→0.92、220ms
 *   · 缓动 fast_out_slow_in = cubic-bezier(0.4, 0, 0.2, 1) = [FastOutSlowInEasing]
 *
 * 项目里已经抄过一次（底部页签切换的 [PageFlip]），这里沿用同一条曲线与时长 ——
 * 同一款 App 里两种「翻页」用不同参数，会显得各是各的。
 *
 * ## 为什么多叠了一层横向位移
 *
 * depth（缩放 + 淡入）只表达「换了一层」，说不清是往前还是往后。而月份切换**有明确
 * 方向**：点左箭头是回到过去、点右箭头是走向将来。所以额外叠一层位移，方向由
 * [FlipState.direction] 决定：
 *
 *   · 上一月（-1）：新内容从**左**进来，旧的往**右**退
 *   · 下一月（+1）：新内容从**右**进来，旧的往**左**退
 *
 * ## ★ 为什么栏目动画加在每个 item 上，而不是整列 / 整页
 *
 * 明细页的 LazyColumn 上挂着 `Modifier.layerBackdrop(contentBackdrop)` —— 玻璃层采样
 * 的就是它下面这棵子树（见 LedgerScreen 里那句注释）。给 LazyColumn **自身**再套一层
 * `graphicsLayer` 改 alpha/scale，等于在 backdrop 的挂载点上又叠一层 layer，有把玻璃
 * 采样搞坏的风险；为一个动画不值得冒这个险。
 *
 * 所以改成：backdrop 仍然挂在 LazyColumn 上不动，动画只加在它**内部的 item** 上。
 * 子节点的 layer 是 backdrop 本来就会录进去的东西，不影响挂载点。
 *
 * 附带好处：滚动时新出现的列表项不会播动画 —— 进度只在月份变化时重置（见
 * [FlipState.replayContent]），平时恒为 1。
 */

/** 进场时长，照 `schedule_depth_enter`。 */
private const val ENTER_MS = 260

/** 出场时长，照 `schedule_depth_pop_exit`。退场是「让位」，早一步走完。 */
private const val EXIT_MS = 220

/** 进场起始缩放：> 1 = 从「更近」的一层压回页面平面（depth 的含义）。 */
private const val ENTER_FROM_SCALE = 1.05f

/** 出场终止缩放：退出时往后退一层。 */
private const val EXIT_TO_SCALE = 0.92f

/** 月份文字的横向位移量：文字只有一百来 dp 宽，滑太远像整页翻页，会抢内容注意力。 */
private val LABEL_SLIDE = 40.dp

// ---- 栏目（内容区）用的参数：比文字保守 ----

/** 栏目起始透明度。卡片面积大，全透明进出太晃眼。 */
private const val CONTENT_FROM_ALPHA = 0.35f

/** 栏目起始缩放。列表长，幅度要小，否则每帧缩放的开销明显。 */
private const val CONTENT_FROM_SCALE = 0.97f

/** 栏目横向位移量，比文字小 —— 大块内容移动太多会显得整个页面在晃。 */
private val CONTENT_SLIDE = 24.dp

/**
 * 月份切换动画的状态：记住方向，以及内容区进场的进度。
 *
 * 不存月份本身 —— 月份是 ViewModel / Fragment 的状态，Compose 只负责观察它变化。
 */
class FlipState {

    /** -1 = 上一月，+1 = 下一月。初始 +1（首次进入没有方向可言，取「向前」）。 */
    var direction by mutableIntStateOf(1)
        private set

    /**
     * 内容区（栏目）的进场进度，0→1。
     *
     * ★ 整个页面**只此一份**：每个 item 各自建一个 Animatable 会各自跑一遍动画，
     *   而它们应该整齐地一起进场。所以由调用方在**一个地方**用 [replayContent] 重置，
     *   所有栏目只读这同一个进度。
     */
    val contentProgress = Animatable(1f)

    /** 点左箭头：先记方向，再让调用方真的去改月份。 */
    fun goPrev() {
        direction = -1
    }

    /** 点右箭头。 */
    fun goNext() {
        direction = 1
    }

    /**
     * 按「目标位置 - 当前位置」的差值设方向（记一笔的类型切换用：三段是并列的，
     * 没有天然的上一 / 下一，只有顺序上的前后）。
     */
    fun goTo(delta: Int) {
        direction = if (delta < 0) -1 else 1
    }

    /**
     * 月份变了：把内容区进场动画从头播一遍。
     *
     * 由 [AnimateContentChanges] 在选项实际变化时触发，首次显示不重播。
     */
    suspend fun replayContent() {
        contentProgress.snapTo(0f)
        contentProgress.animateTo(1f, tween(ENTER_MS, easing = FastOutSlowInEasing))
    }
}

@Composable
internal fun rememberFlipState(): FlipState = remember { FlipState() }

/** Animate an actual selection change; the first composition stays fully visible. */
@Composable
internal fun <T> AnimateContentChanges(value: T, state: FlipState) {
    var previous by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (previous != value) {
            previous = value
            state.replayContent()
        }
    }
}

/**
 * 会跟着月份变化做「方向性 depth 进场」的文字容器。
 *
 * [content] 由调用方给（明细页与统计页用的文字组件不是同一个），这里只负责在
 * [label] 变化时把旧文字按反方向送走、把新文字按 [FlipState.direction] 接进来。
 *
 * ★ 快速连点箭头：[AnimatedContent] 自己会打断上一次转场接着播新的，
 *   不需要像 [PageFlip]（View 版）那样手动 cancel。
 */
@Composable
internal fun FlipLabel(
    label: String,
    state: FlipState,
    modifier: Modifier = Modifier,
    content: @Composable (text: String) -> Unit,
) {
    val slide = with(LocalDensity.current) { LABEL_SLIDE.roundToPx() }
    AnimatedContent(
        targetState = label,
        modifier = modifier,
        transitionSpec = {
            val enterOffset = state.direction * slide
            (
                slideInHorizontally(
                    animationSpec = tween(ENTER_MS, easing = FastOutSlowInEasing),
                    initialOffsetX = { enterOffset },
                ) + fadeIn(
                    animationSpec = tween(ENTER_MS, easing = FastOutSlowInEasing),
                ) + scaleIn(
                    animationSpec = tween(ENTER_MS, easing = FastOutSlowInEasing),
                    initialScale = ENTER_FROM_SCALE,
                )
                ).togetherWith(
                slideOutHorizontally(
                    animationSpec = tween(EXIT_MS, easing = FastOutSlowInEasing),
                    targetOffsetX = { -enterOffset },
                ) + fadeOut(
                    animationSpec = tween(EXIT_MS, easing = FastOutSlowInEasing),
                ) + scaleOut(
                    animationSpec = tween(EXIT_MS, easing = FastOutSlowInEasing),
                    targetScale = EXIT_TO_SCALE,
                )
            )
        },
        label = "monthFlip",
    ) { text ->
        content(text)
    }
}

/**
 * 月份下方栏目的进场修饰（汇总卡、分类卡、趋势卡、明细列表项）。
 *
 * 与 [FlipLabel] 同一条曲线、同一个方向，但幅度更保守（见文件头的常量注释）。
 *
 * ★ 用 [graphicsLayer] 而不是分别改 alpha / scale：前者只影响绘制、不触发重新布局，
 *   长列表才不会在动画期间反复重排。
 */
@Composable
internal fun Modifier.flipContent(state: FlipState): Modifier {
    val slide = with(LocalDensity.current) { CONTENT_SLIDE.roundToPx() }
    return this.graphicsLayer {
        // Layer callbacks observe snapshot state; each tick only updates layer properties.
        val progress = state.contentProgress.value
        alpha = CONTENT_FROM_ALPHA + (1f - CONTENT_FROM_ALPHA) * progress
        val scale = CONTENT_FROM_SCALE + (1f - CONTENT_FROM_SCALE) * progress
        scaleX = scale
        scaleY = scale
        translationX = state.direction * slide * (1f - progress)
    }
}
