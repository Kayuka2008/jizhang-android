package com.jizhang.app.ui.common

import android.view.View
import android.view.animation.PathInterpolator

/**
 * 页签「翻页」动画 —— 参数是照 SleepDown 的 depth 页面动画抄的
 * （仓库 github.com/xiaomanjun233/SleepDown-Schedule，`app/src/main/res/anim/schedule_depth_*.xml`）。
 *
 * 源文件里的四条：
 * - `schedule_depth_enter`：alpha 0→1、scale 1.05→1.0、260ms
 * - `schedule_depth_exit`：alpha 1→0.6、scale 1.0→0.92、260ms
 * - `schedule_depth_pop_enter`：上面 exit 的反向
 * - `schedule_depth_pop_exit`：alpha 1→0、scale 1.0→1.04、220ms
 *
 * 为什么用 ViewPropertyAnimator 而不是那四个 xml：
 * FragmentTransaction 的自定义动画**只对 add/remove/replace 生效**，对 hide/show 不生效
 * （hide/show 不改变 Fragment 的生命周期状态，`moveToExpectedState` 不会触发任何转场）。
 * 我们的页签恰恰是 hide/show 复用 Fragment（为了保住滚动位置），所以只能自己驱动，
 * 曲线和时长沿用 SleepDown 的值，用 `ViewPropertyAnimator` 做真实属性动画。
 *
 * 与 SleepDown 唯一的有意偏差：**出场 alpha 目标取 0 而不是 0.6**。
 * 他们那边是跨 Activity，出场页面最后被窗口切换和系统层级接管，停在 0.6 也能自然过渡；
 * 我们这里是同一个容器里的两个 Fragment，出场页面到点要靠 [exit] 的收尾动作直接设为 GONE，
 * 停在 0.6 会在最后一帧「啪」地消失，反而突兀。缩到 0.92 + 淡到底就没有这个跳变。
 */
object PageFlip {

    /** 进场时长，照 `schedule_depth_enter`。 */
    private const val ENTER_MS = 260L

    /**
     * 出场时长。比进场短一点 —— 退场页面是「让位」，早一步走完，
     * 交叉那段时间短，重叠绘制的成本也短。
     */
    private const val EXIT_MS = 220L

    /** 进场起始缩放。> 1 = 从"更近"的一层压回页面平面（depth 的含义）。 */
    private const val ENTER_FROM_SCALE = 1.05f

    /** 出场终止缩放。退出时往后退一层。 */
    private const val EXIT_TO_SCALE = 0.92f

    /**
     * 缓动，与源文件 xml 的 `@android:interpolator/fast_out_slow_in` 同一条曲线
     * （cubic-bezier(0.4, 0, 0.2, 1)）。
     *
     * 没用 `androidx.interpolator.view.animation.FastOutSlowInInterpolator`：
     * 它在独立的 `androidx.interpolator` artifact 里，本项目没引，
     * 为一个缓动加一个依赖不值当。`PathInterpolator` API 21+，minSdk 26 够用。
     */
    private val EASING = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    /**
     * 把 View 的动画状态清干净。
     *
     * 必须显式 `cancel()`：快速连点时上一次的动画还在跑，新动画叠加上去会留下
     * 半透明 / 缩小的残影（`animate()` 不会自动互相顶掉）。
     */
    fun reset(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    /**
     * 新页面进场：从 1.05 倍淡入。
     *
     * 缩放轴心用 View 默认（自己的中心）—— 页面都是填满容器，中心就是屏幕中心。
     */
    fun enter(view: View) {
        reset(view)
        view.alpha = 0f
        view.scaleX = ENTER_FROM_SCALE
        view.scaleY = ENTER_FROM_SCALE
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ENTER_MS)
            .setInterpolator(EASING)
            .start()
    }

    /**
     * 旧页面出场：后退到 0.92 倍并淡出。
     *
     * @param onEnd 动画结束（或被 [reset] 打断时**不会**走这里）后执行，
     *             调用方负责把页面真正设为 GONE。注意：被打断时不回调，
     *             所以状态复位一律交给 [reset]，不要指望这个终态回调。
     */
    fun exit(view: View, onEnd: () -> Unit) {
        reset(view)
        view.animate()
            .alpha(0f)
            .scaleX(EXIT_TO_SCALE)
            .scaleY(EXIT_TO_SCALE)
            .setDuration(EXIT_MS)
            .setInterpolator(EASING)
            .withEndAction(onEnd)
            .start()
    }
}
