package com.jizhang.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import com.jizhang.app.R
import com.jizhang.app.notify.NotifAccess
import com.jizhang.app.ui.common.appContainer
import com.jizhang.app.ui.common.secondaryComposeRoot
import com.jizhang.app.ui.importbill.ImportActivity
import com.jizhang.app.ui.onboarding.OnboardingScreen

/**
 * 首次启动引导：开启通知监听、导入历史账单。
 *
 * v1.52 rebuild：视图层改成 Compose + miuix；业务（通知状态检测、跳转设置、
 * 跳导入页、完成引导）留在 Activity。
 */
class OnboardingActivity : com.jizhang.app.ui.common.SecondaryActivity() {

    private val notifEnabled = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = secondaryComposeRoot {
            OnboardingScreen(
                notifEnabled = notifEnabled.value,
                onGrantNotif = { NotifAccess.openSettings(this) },
                onImport = { startActivity(Intent(this, ImportActivity::class.java)) },
                onStart = { finishOnboarding() },
                onSkip = { finishOnboarding() },
            )
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // 可能是刚在系统设置页开了开关再回来：这里会检测到「刚授权」并补一次重绑
        val enabled = NotifAccess.syncOnResume(this)
        notifEnabled.value = enabled
        if (enabled) NotifAccess.requestRebind(this)
    }

    private fun finishOnboarding() {
        appContainer.settings.onboardingDone = true
        if (NotifAccess.isEnabled(this)) NotifAccess.requestRebind(this)
        finish()
    }
}
