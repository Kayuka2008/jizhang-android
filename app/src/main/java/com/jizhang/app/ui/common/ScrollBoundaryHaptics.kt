package com.jizhang.app.ui.common

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity

/** 每次手势（含松手后的惯性）到达同一边界只反馈一次。 */
internal class ScrollBoundaryTracker {
    private var active = false
    private var topReached = false
    private var bottomReached = false

    fun startGesture() {
        active = true
        topReached = false
        bottomReached = false
    }

    fun finishGesture() { active = false }

    fun onScroll(deltaY: Float, canScrollBackward: Boolean, canScrollForward: Boolean): Boolean {
        // 不可滚动的短内容、首次布局及程序滚动都不触发。
        if (!active || (!canScrollBackward && !canScrollForward)) return false
        return when {
            deltaY > 0f && !canScrollBackward && !topReached -> {
                topReached = true
                true
            }
            deltaY < 0f && !canScrollForward && !bottomReached -> {
                bottomReached = true
                true
            }
            else -> false
        }
    }
}

/** 放在滚动容器的 modifier 外侧，不消费事件，不改变 miuix 回弹和刷新手势。 */
internal fun Modifier.scrollBoundaryHaptics(state: ScrollableState): Modifier = composed {
    val context = LocalContext.current
    val tracker = remember(state) { ScrollBoundaryTracker() }
    val connection = remember(state, context, tracker) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (tracker.onScroll(consumed.y + available.y, state.canScrollBackward, state.canScrollForward)) {
                    context.hapticKey()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                tracker.finishGesture()
                return Velocity.Zero
            }
        }
    }
    this.pointerInput(tracker) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            tracker.startGesture()
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
        }
    }.nestedScroll(connection)
}

@Composable
internal fun Modifier.verticalScrollWithBoundaryHaptics(state: ScrollState = rememberScrollState()): Modifier =
    scrollBoundaryHaptics(state).verticalScroll(state)
