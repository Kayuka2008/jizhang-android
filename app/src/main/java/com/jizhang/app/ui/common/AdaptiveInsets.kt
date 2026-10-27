package com.jizhang.app.ui.common

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class AdaptiveInsets(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp)
internal data class SafeInsetsPx(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Layout coordinates exclude page transition transforms and use the current window, not the display. */
internal data class WindowLayoutFrame(
    val left: Int, val top: Int, val width: Int, val height: Int,
    val windowWidth: Int, val windowHeight: Int,
) {
    val bottom: Int get() = top + height
}

internal fun windowLayoutFrame(view: View, includeWindowMetrics: Boolean = true): WindowLayoutFrame {
    val root = view.rootView
    var left = 0
    var top = 0
    var child = view
    while (child !== root) {
        val parent = child.parent as? View ?: break
        left += child.left - parent.scrollX
        top += child.top - parent.scrollY
        child = parent
    }
    val bounds = if (includeWindowMetrics && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.context.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds
    } else null
    return WindowLayoutFrame(left, top, view.width, view.height,
        bounds?.width() ?: root.width, bounds?.height() ?: root.height)
}

internal fun remainingSafeInsets(
    safe: SafeInsetsPx,
    frame: WindowLayoutFrame,
    minimumBottomClearance: Int = 0,
): SafeInsetsPx {
    // The navigation hint may disappear without a navigation-mode change. A floating
    // control still needs breathing room when all reported bottom insets become zero.
    // Apply the design minimum before subtracting parent avoidance, including the IME.
    val bottom = maxOf(safe.bottom, minimumBottomClearance.coerceAtLeast(0))
    if (frame.width <= 0 || frame.height <= 0) return safe.copy(bottom = bottom)
    fun remainder(inset: Int, consumed: Int) = (inset - consumed.coerceAtLeast(0)).coerceAtLeast(0)
    return SafeInsetsPx(
        remainder(safe.left, frame.left),
        remainder(safe.top, frame.top),
        remainder(safe.right, frame.windowWidth - frame.left - frame.width),
        remainder(bottom, frame.windowHeight - frame.bottom),
    )
}

/** Includes the bar and any real gap below it; hide/show animations do not change this contract. */
internal fun bottomBarOverlap(page: WindowLayoutFrame, bar: WindowLayoutFrame): Int =
    (page.bottom - bar.top).coerceIn(0, page.height.coerceAtLeast(0))

/**
 * The View root already avoids side system bars and the keyboard. Measure that consumed space
 * before adding the remaining safety to a control or a list, so it is never counted twice.
 * Insets remain observable during navigation-mode/IME changes; geometry updates only on layout.
 */
@Composable
internal fun rememberAdaptiveInsets(
    minimumBottomClearance: Dp = 0.dp,
    excludeImeFromBottom: Boolean = false,
): AdaptiveInsets {
    val view = LocalView.current
    val density = LocalDensity.current
    var frame by remember(view) { mutableStateOf(windowLayoutFrame(view)) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener { frame = windowLayoutFrame(view) }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        view.post { if (view.isAttachedToWindow) frame = windowLayoutFrame(view) }
        onDispose {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    val drawing = WindowInsets.safeDrawing
    val tappable = WindowInsets.tappableElement
    val bottom = if (excludeImeFromBottom) {
        // The bottom bar must stay anchored to the window when the IME opens.
        // navigationBars excludes the IME; safeDrawing may include it on API 35.
        WindowInsets.navigationBars.getBottom(density)
    } else {
        maxOf(
            drawing.getBottom(density),
            WindowInsets.mandatorySystemGestures.getBottom(density),
            tappable.getBottom(density),
        )
    }
    val safe = SafeInsetsPx(
        drawing.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr),
        drawing.getTop(density),
        drawing.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr),
        bottom,
    )
    val minimumBottomPx = with(density) { minimumBottomClearance.roundToPx() }
    val remaining = remainingSafeInsets(safe, frame, minimumBottomPx)
    return with(density) {
        AdaptiveInsets(remaining.left.toDp(), remaining.top.toDp(), remaining.right.toDp(), remaining.bottom.toDp())
    }
}
