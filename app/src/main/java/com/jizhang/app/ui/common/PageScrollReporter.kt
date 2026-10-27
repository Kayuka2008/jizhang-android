package com.jizhang.app.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

/**
 * ★ Compose 页面的滚动偏移上报（v1.64）。
 *
 * ## 为什么需要它
 *
 * 悬浮底栏的收起/恢复由 [com.jizhang.app.ui.MainActivity.updateBarReveal] 驱动，
 * 判据是**滚动方向**。它原先只有一条数据来源：`ScrollProgressTracker` 去 View 树里
 * 找 `RecyclerView` / `ScrollView` / `NestedScrollView`。但 v1.60 之后
 * **四个主页签全是 Compose**，滚动发生在各自的 `verticalScroll` / `LazyColumn` 里，
 * View 树里一个滚动容器都没有 ⇒ 追踪器恒定上报 0 ⇒ 底栏永远停在「停在顶部」那一档。
 *
 * （v1.59 及以前设置页的根容器还是 `ScrollView`，所以那时候底栏是正常收起的 ——
 *  A/B 实测过，不是错觉。）
 *
 * ## 为什么读「绝对位置」而不是累计 `NestedScrollConnection.consumed`
 *
 * v1.64 首轮就是挂在 `nestedScroll` 上的，结果四个页签一律不生效。两个坑叠在一起：
 *
 *  1. **位置**：连接必须挂在滚动容器的**祖先**上（同一 modifier 链里就是排在
 *     `verticalScroll` 之前）。挂在内侧的话 `onPostScroll` 先于消费被调用，
 *     `consumed.y` 恒为 0。
 *  2. **符号**：`consumed.y` 的正负约定很容易搞反（内容向上移动 vs 手指方向），
 *     一旦反了，「向下滚」就变成负增量、累积值被 `coerceAtLeast(0f)` 钳成 0，
 *     表面上还是「底栏不消失」，跟没接一样。
 *
 * 改成读滚动状态自己的**绝对位置**就没有这两个问题：
 *  · `ScrollState.value` 就是滚动量本身，精确、无符号歧义；
 *  · `LazyListState` 没有「累计像素」，但 `firstVisibleItemIndex * 100000f
 *     + firstVisibleItemScrollOffset` 是**单调不减**的，而且停在顶部时正好是 0
 *     —— 而底栏判据要的正是「随滚动推进单调增长、回到顶部归零、方向由差值给」，
 *     越精确的像素反而不重要。
 *
 * ## 为什么偏移要由 Fragment 持有（而不是活在 composable 里）
 *
 * 切页时 [com.jizhang.app.ui.MainActivity] 要用「这一页当前的偏移」同步方向判据的
 * 基准，否则「切到一页已经滚下去 800px 的页面后往回滚」会因为基准是 0
 * 被算成 `delta > 0` ⇒ **往上滚反而把底栏收起来**。累积值必须能被 Fragment 读到。
 */
class PageScrollReporter(private val onOffset: (Float) -> Unit) {

    /** 当前滚动位置（>= 0；停在顶部为 0）。 */
    var current: Float = 0f
        private set

    /** 页面重新显示时用外部值复位（Compose 状态在 remember 里，切页不会清零）。 */
    fun report(offsetPx: Float) {
        current = offsetPx.coerceAtLeast(0f)
        onOffset(current)
    }

    override fun toString(): String = "PageScrollReporter($current)"
}

/**
 * 让一个 `verticalScroll` 容器把绝对滚动位置喂给 [reporter]。
 *
 * 用 [snapshotFlow] 而不是 `LaunchedEffect { while(true) emit(...) }`：
 * 前者只在值**真的变了**时才回调（每帧一次比较），后者每帧都重组 Activity 状态。
 */
@Composable
fun ReportScrollOffset(state: ScrollState, reporter: PageScrollReporter?) {
    if (reporter == null) return
    LaunchedEffect(state, reporter) {
        snapshotFlow { state.value.toFloat() }.collect { reporter.report(it) }
    }
}

/**
 * [LazyListState] 版本。位置用「item 序号 × 10 万 + item 内偏移」表示——
 * 单调不减、顶部为 0，够底栏判据用（原因见类注释）。
 */
@Composable
fun ReportListScrollOffset(state: LazyListState, reporter: PageScrollReporter?) {
    if (reporter == null) return
    LaunchedEffect(state, reporter) {
        snapshotFlow {
            state.firstVisibleItemIndex * ITEM_STRIDE + state.firstVisibleItemScrollOffset
        }.collect { reporter.report(it) }
    }
}

/** item 内偏移的上界。取一个远大于任何 item 高度的数，保证跨 item 时数值不回退。 */
private const val ITEM_STRIDE = 100_000f

/**
 * 由 [com.jizhang.app.ui.MainActivity] 在切页时问一次：
 * 「你现在滚到哪里了？」——用来把底栏方向判据的基准对齐到这一页的真值。
 *
 * 未实现该接口的页面以顶部位置作为切页基准。
 */
interface ScrollOffsetPage {
    fun currentScrollOffsetPx(): Float
}
