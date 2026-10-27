package com.jizhang.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.ui.unit.Dp
import com.jizhang.app.R

/**
 * 订阅悬浮底栏在当前页面底部的实际占用（dp），包含底栏下方的间距。
 * 使用同一窗口的布局坐标，避免页面末尾内容被底栏覆盖；视觉显隐不改变占位。
 * 注册后立即读取一次，同时跟随之后的布局和窗口尺寸变化更新。
 */
internal class GlassBarInsets(
    private val host: View,
    private val onHeight: (Dp) -> Unit,
) {

    private var started = false
    private var lastInset: Dp? = null
    private val listener = ViewTreeObserver.OnGlobalLayoutListener { update() }

    fun start() {
        if (started) return
        started = true
        host.viewTreeObserver.addOnGlobalLayoutListener(listener)
        host.post { if (started) update() }
    }

    fun stop() {
        started = false
        if (host.viewTreeObserver.isAlive) host.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }

    private fun update() {
        val bar = host.context.activity()?.findViewById<View>(R.id.glassBottomBar)
        val overlap = if (bar != null && bar.height > 0 && host.height > 0) {
            bottomBarOverlap(windowLayoutFrame(host, false), windowLayoutFrame(bar, false))
        } else 0
        val inset = Dp(overlap / host.resources.displayMetrics.density)
        if (inset != lastInset) {
            lastInset = inset
            onHeight(inset)
        }
    }
}

private fun Context.activity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) break
        current = next
    }
    return current as? Activity
}
