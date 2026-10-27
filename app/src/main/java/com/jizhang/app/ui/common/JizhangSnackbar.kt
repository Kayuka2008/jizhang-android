package com.jizhang.app.ui.common

import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jizhang.app.ui.glass.liquidSurface
import com.jizhang.app.ui.glass.LiquidSurfaceRole
import top.yukonga.miuix.kmp.basic.SnackbarDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.jizhang.app.R
import com.jizhang.app.ui.glass.GlassBottomBarHost
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Snackbar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

/*
 * 统一提示条（v1.60）：miuix 的 Snackbar + 给 View 侧的命令式入口。
 *
 * ## 与 JizhangDialog 是同一个套路
 *
 * 调用点全在 View 侧的命令式代码里（onClick、协程回调），而 miuix 的 Snackbar 是
 * Compose 组件、必须挂在组合树上 —— 所以中间需要一座桥。这里是最小的一座：
 * 一个 state holder + 一个渲染它的 composable。
 *
 * ## 为什么连 showSnackbar 也要自己包
 *
 * `SnackbarHostState.showSnackbar` 是 **suspend**（要等上一条让位），只能在
 * `LaunchedEffect` 里调。所以请求先落到一个 state 上，由组合里的 LaunchedEffect 接手。
 *
 * ★ 请求对象每次 [show] 都新建一个实例 —— LaunchedEffect 以它为 key，
 *   否则「同样的文案连续弹两次」第二次不会触发（对象没变，等于没变化）。
 *
 * ★ 一个页面一个 Host，别做全局单例：二级页 finish 之后全局单例会把提示条渲染到
 *   已经销毁的 Activity 上 —— 与 [JizhangDialogHost] 同一个理由。
 *
 * 用法：
 *   1. `private val snackbar = JizhangSnackbarHost()`
 *   2. 在页面 Compose 树里挂 `JizhangSnackbarHostView(snackbar)`
 *   3. `snackbar.show("已保存")`
 */
@Stable
internal class JizhangSnackbarHost {

    internal val state = SnackbarHostState()

    internal var request by mutableStateOf<SnackRequest?>(null)

    fun show(
        message: String,
        actionLabel: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Long,
        onAction: (() -> Unit)? = null,
    ) {
        request = SnackRequest(message, actionLabel, duration, onAction)
    }
}

internal class SnackRequest(
    val message: String,
    val actionLabel: String?,
    val duration: SnackbarDuration,
    val onAction: (() -> Unit)?,
)

/**
 * 渲染 [host] 的提示条。挂在页面自己的 Compose 树里即可 ——
 * 位置无所谓（它是覆盖层），但必须和页面在同一个组合中。
 */
@Composable
internal fun JizhangSnackbarHostView(
    host: JizhangSnackbarHost,
    modifier: Modifier = Modifier,
) {
    val bottomInset = rememberAdaptiveInsets().bottom
    val request = host.request
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        val result = host.state.showSnackbar(
            message = request.message,
            actionLabel = request.actionLabel,
            duration = request.duration,
        )
        if (result == SnackbarResult.ActionPerformed) request.onAction?.invoke()
    }
    SnackbarHost(state = host.state, modifier = modifier.padding(bottom = bottomInset)) { data ->
        // 挂在实际提示内容上：miuix 退场动画完成才 dispose，连续提示各持有一份占用。
        // 二级页没有悬浮底栏，自动跳过。
        val view = LocalView.current
        DisposableEffect(view, data) {
            val bar = view.rootView.findViewById<GlassBottomBarHost>(R.id.glassBottomBar)
            val owner = Any()
            val observer = view.viewTreeObserver
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                bar?.setSnackbarVisible(owner, view.isShown)
            }
            bar?.setSnackbarVisible(owner, view.isShown)
            observer.addOnGlobalLayoutListener(listener)
            onDispose {
                if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
                bar?.setSnackbarVisible(owner, false)
            }
        }
        val scope = rememberCoroutineScope()
        val dismissLabel = stringResource(R.string.snackbar_dismiss)
        // miuix 自己会消费点击，因此在 Initial pass 观察轻点；滑动仍由原手势处理。
        // 带操作按钮时保留按钮语义，避免关闭提示抢先吞掉操作回调。
        val dismissModifier = if (data.visuals.actionLabel.isNullOrEmpty()) {
            Modifier
                .pointerInput(data) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) {
                            scope.launch { data.dismiss() }
                        }
                    }
                }
                .semantics {
                    onClick(label = dismissLabel) {
                        scope.launch { data.dismiss() }
                        true
                    }
                }
        } else {
            Modifier
        }
        val colors = SnackbarDefaults.snackbarColors()
        Snackbar(
            data,
            modifier = dismissModifier
                .padding(SnackbarDefaults.OuterPadding)
                .liquidSurface(RoundedCornerShape(16.dp), colors.containerColor, role = LiquidSurfaceRole.Message),
            colors = colors.copy(containerColor = Color.Transparent),
            // Native Snackbar supplies its outer inset inside the glass surface.
            insideMargin = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        )
    }
}
