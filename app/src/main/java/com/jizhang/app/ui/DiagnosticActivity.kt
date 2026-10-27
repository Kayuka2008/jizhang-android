package com.jizhang.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jizhang.app.ui.common.JizhangDialogHost
import com.jizhang.app.ui.common.JizhangDialogHostView
import com.jizhang.app.ui.common.JizhangSnackbarHost
import com.jizhang.app.ui.common.JizhangSnackbarHostView
import com.jizhang.app.ui.common.secondaryComposeRoot
import com.jizhang.app.R
import com.jizhang.app.data.db.entity.NotificationLog
import com.jizhang.app.ui.common.appContainer
import com.jizhang.app.ui.diagnostic.DiagLogRow
import com.jizhang.app.ui.diagnostic.DiagnosticScreen
import com.jizhang.app.util.Dates
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * 通知诊断页。
 * 识别规则对不上时，用户可在这里复制微信原始通知文本反馈，以便调整规则。
 *
 * v1.53：视图层改成 Compose + miuix（原 activity_diagnostic.xml / item_notification_log.xml /
 * 内嵌 LogAdapter 全删）。Activity 仍是**宿主 + 业务**：观察日志流、拼复制文本、
 * 清空确认框、snackbar 全部留在原处，Compose 侧只拿到一条列表与两个回调。
 *
 * ★ RecyclerView 的 DiffUtil 由 LazyColumn 的 key 取代 —— id 稳定即天然去重。
 * ★ 系统栏高度在这里订阅后推进 state（绝不在组合体里现读，见 v1.45 的坑）。
 */
class DiagnosticActivity : com.jizhang.app.ui.common.SecondaryActivity() {


    private val logs = mutableStateOf<List<DiagLogRow>>(emptyList())

    /** 统一对话框宿主（v1.56）：挂进下面那棵 Compose 树。 */
    private val dialogs = JizhangDialogHost()

    /** 统一提示条宿主（v1.60）。 */
    private val snackbar = JizhangSnackbarHost()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = secondaryComposeRoot {
                DiagnosticScreen(
                    logs = logs.value,
                    onBack = { finish() },
                    onCopy = { copy(it) },
                    onClearAll = { confirmClear() },
                )
                JizhangDialogHostView(dialogs)
                JizhangSnackbarHostView(snackbar)
        }
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            appContainer.pendingRepo.observeLogs().collect { list ->
                logs.value = list.map(::toRow)
            }
        }
    }

    private fun toRow(log: NotificationLog) = DiagLogRow(
        id = log.id,
        packageName = log.packageName,
        timeText = Dates.fromEpochMillis(log.capturedAt).format(TIME_FORMAT),
        title = log.title.orEmpty(),
        text = log.text.orEmpty(),
        recognized = log.recognized,
        matchedRule = log.matchedRule,
    )

    /** 复制给用户手动发出去的完整文本（与旧版逐行一致）。 */
    private fun copy(row: DiagLogRow) {
        val text = buildString {
            appendLine("时间：${row.timeText}")
            appendLine("包名：${row.packageName}")
            appendLine("标题：${row.title}")
            appendLine("正文：${row.text}")
            append("规则：${row.matchedRule ?: "未匹配"}")
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("微信通知", text))
        snackbar.show(getString(R.string.diag_copied))
    }

    private fun confirmClear() {
        dialogs.confirm(
            message = getString(R.string.diag_clear_question),
            confirmText = getString(R.string.action_delete),
            cancelText = getString(R.string.action_cancel),
        ) {
            lifecycleScope.launch { appContainer.pendingRepo.clearLogs() }
        }
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
