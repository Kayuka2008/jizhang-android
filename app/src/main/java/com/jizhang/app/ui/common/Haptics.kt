package com.jizhang.app.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/*
 * 触感反馈（v1.56）：全应用统一的「系统级」震动入口。
 *
 * ## 为什么不用 Compose 的 LocalHapticFeedback
 *
 * `LocalHapticFeedback.performHapticFeedback(HapticFeedbackType.*)` 只是个抽象 ——
 * 震不震、震多强完全由实现决定，在多数 ROM 上只有 TextHandleMove 这类系统内建场景
 * 才有反应，普通点击常常是静默的。而记账键盘这种「每次按键都要有确认感」的场景要的
 * 是**确定会震**的系统级触感，所以直接走 [Vibrator]。
 *
 * ## 强度：优先用系统预置效果
 *
 * `VibrationEffect.EFFECT_*`（API 29+）是各 OEM 按自己马达调过的，和系统设置里那个
 * 「触感反馈」是同一种质感 —— 这也是「系统级」的本来意思。自己拼时长/振幅只作为
 * API 26~28 的降级（minSdk 26，createPredefined 要 29）。
 *
 * ## 两道静默开关
 *
 *   1. 应用内开关 `settings.hapticEnabled` —— 调用处不用自己判断，关掉后全应用静默；
 *   2. 设备有没有马达：[Vibrator.hasVibrator] 为 false（部分平板/电视）直接放弃。
 *
 * 震动失败一律吞掉：这东西是锦上添花，不该影响业务。
 */

private fun Context.vibrator(): Vibrator? {
    val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    return v?.takeIf { it.hasVibrator() }
}

/**
 * 播一次震动。
 *
 * [predefined] 是 `VibrationEffect.EFFECT_*` 常量（API 29+）；旧设备退化到
 * [fallbackMs] / [fallbackAmplitude] 描述的单次震动。
 */
@SuppressLint("NewApi")
private fun Context.play(predefined: Int, fallbackMs: Long, fallbackAmplitude: Int) {
    if (!appContainer.settings.hapticEnabled) return
    val vibrator = vibrator() ?: return
    val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(predefined)
    } else {
        @Suppress("DEPRECATION")
        VibrationEffect.createOneShot(fallbackMs, fallbackAmplitude)
    }
    runCatching { vibrator.vibrate(effect) }
}

/**
 * 键盘按键：最轻的一下「嗒」。
 *
 * 记账键盘每按一次都要有确认感，但连按十几次不能烦 —— 这是三档里最轻的一档。
 */
@SuppressLint("InlinedApi")
fun Context.hapticKey() = play(VibrationEffect.EFFECT_TICK, 8L, 40)

/** 完成 / 确认：明确一点的「咔」。 */
@SuppressLint("InlinedApi")
fun Context.hapticConfirm() = play(VibrationEffect.EFFECT_CLICK, 16L, 90)

