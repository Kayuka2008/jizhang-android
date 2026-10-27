package com.jizhang.app.ui.common

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.jizhang.app.ui.glass.LiquidSurfaceRole
import com.jizhang.app.ui.glass.LocalLiquidWindowBackdrop
import com.jizhang.app.ui.glass.liquidSurface
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Remaining body viewport after the window safety, panel padding and measured title. */
internal val LocalDialogContentMaxHeight = staticCompositionLocalOf { Dp.Infinity }

/** Centered window shell; the forms inside continue to use native miuix controls. */
@Composable
internal fun CenteredLiquidDialog(
    show: Boolean,
    title: String?,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val finished by rememberUpdatedState(onDismissFinished)
    val dismiss by rememberUpdatedState(onDismissRequest)
    val progress = remember { Animatable(0f) }
    val shape = remember { RoundedRectangle(32.dp) }
    LaunchedEffect(show) {
        if (show) {
            // Let the Activity record its backdrop before revealing the glass window.
            repeat(2) { withFrameNanos { } }
            progress.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        } else {
            progress.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
            finished()
        }
    }
    Dialog(
        onDismissRequest = { if (show) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            // Keep one window alive through both directions. The panel and scrim share a clock.
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setWindowAnimations(0)
            onDispose { }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = .18f * progress.value)) }
                .pointerInput(Unit) { detectTapGestures { dismiss() } }
                .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val availableHeight = maxHeight
            val density = LocalDensity.current
            var titleHeightPx by remember(title) { mutableIntStateOf(0) }
            val titleHeight = with(density) { titleHeightPx.toDp() }
            val contentHeight = (availableHeight - 48.dp - titleHeight).coerceAtLeast(0.dp)
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = availableHeight)
                    .graphicsLayer {
                        alpha = progress.value
                        scaleX = .97f + .03f * progress.value
                        scaleY = scaleX
                    }
                    .liquidSurface(shape, role = LiquidSurfaceRole.Dialog,
                        backdrop = LocalLiquidWindowBackdrop.current)
                    .pointerInput(Unit) { detectTapGestures { /* Consume empty surface taps. */ } }
                    .verticalScrollWithBoundaryHaptics().padding(24.dp),
            ) {
                if (!title.isNullOrEmpty()) {
                    Column(Modifier.fillMaxWidth().onSizeChanged { titleHeightPx = it.height }) {
                        Text(title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            style = MiuixTheme.textStyles.title3.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(20.dp))
                    }
                }
                CompositionLocalProvider(LocalDialogContentMaxHeight provides contentHeight) {
                    content()
                }
            }
        }
    }
}
