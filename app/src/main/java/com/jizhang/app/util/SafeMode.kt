package com.jizhang.app.util

import android.content.Context

/**
 * 安全模式开关。
 *
 * 用途：主页玻璃在 v1.43 换成了 `rememberLayerBackdrop` + `Modifier.layerBackdrop`
 * 这条**全新的**采样路径。它编译得过，但跑起来在真机上是什么行为没人知道。
 * 万一它崩，用户至少能在关掉它之后正常用这个 App —— 而"关掉它就不崩"
 * 本身就是一条决定性的诊断信息（说明问题就在玻璃采样，不在页面本身）。
 *
 * 只用一个独立的 SharedPreferences 文件，不往 [com.jizhang.app.data.prefs.SettingsStore]
 * 里塞东西：那是正式设置，这种排障开关不该出现在设置页里。
 */
object SafeMode {

    private const val PREF = "safe_mode"
    private const val KEY_LEDGER_GLASS = "ledger_glass"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 主页顶部玻璃是否开启。默认开；崩过之后可以由用户在崩溃页关掉。 */
    fun ledgerGlassEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LEDGER_GLASS, true)

    fun setLedgerGlassEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LEDGER_GLASS, enabled).apply()
    }
}
