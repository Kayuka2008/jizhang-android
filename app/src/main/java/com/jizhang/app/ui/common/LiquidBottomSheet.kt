package com.jizhang.app.ui.common

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.jizhang.app.ui.glass.LiquidSurfaceRole
import com.jizhang.app.ui.glass.LocalLiquidWindowBackdrop
import com.jizhang.app.ui.glass.liquidSurface
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One scrolling surface, including its handle, without a fixed empty header. */
@Composable
internal fun LiquidBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val showing by rememberUpdatedState(show)
    val dismiss by rememberUpdatedState(onDismissRequest)
    val finished by rememberUpdatedState(onDismissFinished)
    val progress = remember { Animatable(0f) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val shape = remember { RoundedRectangle(24.dp) }
    val density = LocalDensity.current
    val distanceThreshold = with(density) { 96.dp.toPx() }
    val velocityThreshold = with(density) { 1100.dp.toPx() }
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rebound by remember { mutableStateOf<Job?>(null) }
    val dragConnection = remember(scroll, distanceThreshold, velocityThreshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!showing || source != NestedScrollSource.UserInput) return Offset.Zero
                rebound?.cancel()
                if (dragOffset <= 0f || available.y >= 0f) return Offset.Zero
                val consumed = available.y.coerceAtLeast(-dragOffset)
                dragOffset += consumed
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!showing || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                dragOffset += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!showing || (dragOffset <= 0f && (scroll.canScrollBackward || available.y <= velocityThreshold))) {
                    return Velocity.Zero
                }
                rebound?.cancel()
                if (dragOffset > minOf(distanceThreshold, sheetHeight * .25f) || available.y > velocityThreshold) {
                    dismiss()
                } else {
                    rebound = scope.launch {
                        animate(dragOffset, 0f, initialVelocity = available.y,
                            animationSpec = spring(dampingRatio = 1f, stiffness = 450f)) { value, _ ->
                            dragOffset = value.coerceAtLeast(0f)
                        }
                    }
                }
                return Velocity(0f, available.y)
            }
        }
    }
    LaunchedEffect(show) {
        rebound?.cancel()
        if (show) {
            dragOffset = 0f
            // Record the Activity backdrop before revealing the separate window.
            repeat(2) { withFrameNanos { } }
            progress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        } else {
            progress.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
            finished()
        }
    }
    Dialog(
        onDismissRequest = { if (showing) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setWindowAnimations(0)
            onDispose { }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = .18f * progress.value)) }
                .pointerInput(Unit) { detectTapGestures { if (showing) dismiss() } }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(top = 8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val availableHeight = maxHeight
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                Column(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(max = availableHeight)
                        .onSizeChanged { sheetHeight = it.height.toFloat() }
                        .graphicsLayer {
                            translationY = (1f - progress.value) * sheetHeight + dragOffset
                            alpha = progress.value
                        }
                        .liquidSurface(shape, role = LiquidSurfaceRole.Sheet,
                            backdrop = LocalLiquidWindowBackdrop.current)
                        .clip(shape)
                        .pointerInput(Unit) { detectTapGestures { /* Consume empty surface taps. */ } }
                        .nestedScroll(dragConnection)
                        .verticalScrollWithBoundaryHaptics(scroll)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .background(MiuixTheme.colorScheme.outline.copy(alpha = .5f), RoundedRectangle(2.dp)),
                    )
                    content()
                }
            }
        }
    }
}
