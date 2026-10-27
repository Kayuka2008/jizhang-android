package com.jizhang.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃自捕获（v1.43.1 加的）。
 *
 * **为什么要有这个**：本机没有连设备（adb devices 是空的），拿不到 logcat，
 * 一旦用户反馈「闪退」，靠读代码猜是猜不出来的 —— 尤其是新引入 Compose
 * 渲染路径这种「编译得过、跑起来才炸」的问题。
 *
 * 做法：装一个全局 `UncaughtExceptionHandler`，把堆栈写进 `filesDir/crash.log`。
 * 下次启动时 [com.jizhang.app.ui.CrashReportActivity] 会把它显示出来，
 * 用户可以一键复制发回来。
 *
 * ★ 只在**自己进程里写文件**，不申请任何权限、不联网。
 */
object CrashLog {

    private const val FILE = "crash.log"

    /** 记录到最近 3 次崩溃，够定位就行，不无限增长。 */
    private const val MAX_ENTRIES = 3

    private fun file(context: Context): File = File(context.filesDir, FILE)

    /** 安装全局处理器。在 `Application.onCreate` 里调一次。 */
    fun install(context: Context, defaultHandler: Thread.UncaughtExceptionHandler?) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(context, thread, throwable) }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }

        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val header = buildString {
            appendLine("=== $stamp ===")
            appendLine("thread: ${thread.name}")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} / API ${Build.VERSION.SDK_INT}")
            appendLine("version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
        }

        val previous = read(context)
        // 只保留前 MAX_ENTRIES-1 段旧记录，避免文件越滚越大。
        val kept = previous.split(ENTRY_SEPARATOR)
            .take(MAX_ENTRIES - 1)
            .joinToString(ENTRY_SEPARATOR)

        file(context).writeText(
            if (kept.isBlank()) {
                header + sw
            } else {
                header + sw + ENTRY_SEPARATOR + kept
            },
        )
    }

    private const val ENTRY_SEPARATOR = "\n\n--------------------------------------------------\n\n"

    /** 有没有待查看的崩溃记录。 */
    fun hasPending(context: Context): Boolean {
        val f = file(context)
        return f.exists() && f.length() > 0
    }

    fun read(context: Context): String = runCatching { file(context).readText() }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /**
     * 把崩溃文本分享出去（系统分享面板）。
     *
     * 走 `ACTION_SEND` 而不是剪贴板：堆栈有几百字，让用户自己发微信/邮件最省事；
     * 剪贴板在 Android 10+ 上后台读不到，反而添乱。
     */
    fun shareIntent(context: Context, text: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "记账本崩溃日志")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
