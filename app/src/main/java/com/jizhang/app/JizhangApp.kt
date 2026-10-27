package com.jizhang.app

import android.app.Application
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.notify.NotifAccess
import com.jizhang.app.util.CrashLog

class JizhangApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        // 崩溃自捕获：记到 filesDir/crash.log，下次启动由 CrashReportActivity 显示出来。
        // 必须放在最前面 —— 后面任何一步炸了也要留得下堆栈。
        CrashLog.install(this, Thread.getDefaultUncaughtExceptionHandler())
        super.onCreate()
        container = AppContainer(this)
        val settings = SettingsStore(this)
        settings.clearLegacyUiStyle()
        settings.themeMode.apply()
        // 修复历史版本可能在监听组件上遗留的「禁用」状态（正常情况是空操作）
        NotifAccess.healState(this)
    }
}
