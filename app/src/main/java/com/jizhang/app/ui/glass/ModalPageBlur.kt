package com.jizhang.app.ui.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.jizhang.app.R
import com.jizhang.app.data.prefs.GlassStyle
import kotlin.math.roundToInt

/** Blur the Activity, including its floating navigation, while the dialog window stays sharp. */
@Composable
internal fun ModalPageBlurEffect(active: Boolean) {
    val scene = LocalLiquidScene.current
    val enabled = scene?.enabled == true
    val steps = if (scene?.style == GlassStyle.CLEAR) 6 else 18
    val root = LocalView.current.rootView.findViewById<View>(android.R.id.content)
    DisposableEffect(root, active, enabled, steps) {
        val owner = Any()
        val controller = if (enabled && active && root != null) {
            (root.getTag(R.id.liquid_modal_blur_controller) as? ModalBlurController
                ?: ModalBlurController(root, steps).also { root.setTag(R.id.liquid_modal_blur_controller, it) })
                .also { it.acquire(owner) }
        } else null
        onDispose { controller?.release(owner) }
    }
}

@android.annotation.TargetApi(31)
private class ModalBlurController(private val view: View, private val steps: Int) : View.OnAttachStateChangeListener {
    private val owners = mutableSetOf<Any>()
    // Prepare the small radius ladder once; animation only switches cached effects.
    private val effects = Array(steps + 1) { step ->
        if (step == 0) null else {
            val radius = step * view.resources.displayMetrics.density
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        }
    }
    private var level = 0
    private var animator: ValueAnimator? = null
    private val easing = PathInterpolator(.4f, 0f, .2f, 1f)

    init { view.addOnAttachStateChangeListener(this) }

    fun acquire(owner: Any) {
        if (owners.add(owner) && owners.size == 1) animateTo(steps)
    }

    fun release(owner: Any) {
        if (owners.remove(owner) && owners.isEmpty()) animateTo(0)
    }

    private fun animateTo(target: Int) {
        animator?.cancel()
        if (target == level) return
        animator = ValueAnimator.ofFloat(level.toFloat(), target.toFloat()).apply {
            duration = if (target > level) 180L else 140L
            interpolator = easing
            addUpdateListener {
                val next = (it.animatedValue as Float).roundToInt().coerceIn(0, steps)
                if (next != level) {
                    level = next
                    view.setRenderEffect(effects[next])
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) animator = null
                }
            })
            start()
        }
    }

    override fun onViewAttachedToWindow(v: View) = Unit

    override fun onViewDetachedFromWindow(v: View) {
        animator?.cancel()
        animator = null
        owners.clear()
        level = 0
        view.setRenderEffect(null)
        view.setTag(R.id.liquid_modal_blur_controller, null)
        view.removeOnAttachStateChangeListener(this)
    }
}
