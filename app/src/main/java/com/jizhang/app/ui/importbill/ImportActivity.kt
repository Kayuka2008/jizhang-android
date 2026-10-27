package com.jizhang.app.ui.importbill

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import com.jizhang.app.ui.common.JizhangDialogHost
import com.jizhang.app.ui.common.JizhangDialogHostView
import com.jizhang.app.ui.common.JizhangSnackbarHost
import com.jizhang.app.ui.common.JizhangSnackbarHostView
import com.jizhang.app.ui.common.secondaryComposeRoot
import com.jizhang.app.R
import com.jizhang.app.data.db.entity.TxKind
import com.jizhang.app.importbill.BillReader
import com.jizhang.app.importbill.ColumnMapping
import com.jizhang.app.importbill.ImportCandidate
import com.jizhang.app.importbill.ImportPreview
import com.jizhang.app.importbill.ImportUseCase
import com.jizhang.app.importbill.ParseResult
import com.jizhang.app.importbill.PasswordRequiredException
import com.jizhang.app.importbill.WeChatBillParser
import com.jizhang.app.importbill.WrongPasswordException
import com.jizhang.app.money.Money
import com.jizhang.app.ui.common.appContainer
import com.jizhang.app.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/**
 * 导入微信账单：选文件 → 解析 → 预览 → 写入。
 *
 * v1.53：视图层改成 Compose + miuix（原 activity_import.xml / item_import_preview.xml /
 * 内嵌 PreviewAdapter 全删）。Activity 仍是**宿主 + 业务**：文件选择、读字节、
 * 密码弹窗、列映射弹窗、预览计算、写库、snackbar 全部留在原处；
 * Compose 侧只拿到「当前步骤 + 汇总 + 行列表 + 两个回调」。
 *
 * ★ 三步状态机（pick/preview/loading）改用 [ImportStep] 枚举表达互斥，
 *   不再靠三个 View 的 visibility 手动对齐。
 * ★ 系统栏高度订阅后推进 state（绝不在组合体里现读，见 v1.45 的坑）。
 */
class ImportActivity : com.jizhang.app.ui.common.SecondaryActivity() {

    private val step = mutableStateOf(ImportStep.PICK)
    private val summary = mutableStateOf<ImportSummary?>(null)
    private val rows = mutableStateOf<List<ImportPreviewRow>>(emptyList())

    private val useCase by lazy {
        ImportUseCase(
            appContainer.transactionRepo,
            appContainer.categoryRepo,
            appContainer.accountRepo,
        )
    }

    private var preview: ImportPreview? = null
    private var parsedText: String? = null

    /** 统一对话框宿主（v1.56）。 */
    private val dialogs = JizhangDialogHost()

    /** 统一提示条宿主（v1.60）。 */
    private val snackbar = JizhangSnackbarHost()

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val uri = result.data?.data ?: return@registerForActivityResult
            readAndParse(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = secondaryComposeRoot {
                ImportScreen(
                    step = step.value,
                    summary = summary.value,
                    rows = rows.value,
                    onBack = { finish() },
                    onPickFile = { launchPicker() },
                    onCommit = { commit() },
                )
                JizhangDialogHostView(dialogs)
                JizhangSnackbarHostView(snackbar)
        }
        setContentView(root)
    }

    private fun launchPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                    "text/csv",
                    "text/comma-separated-values",
                    "text/plain",
                ),
            )
        }
        pickFile.launch(intent)
    }

    private fun readAndParse(uri: Uri) {
        step.value = ImportStep.LOADING
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                step.value = ImportStep.PICK
                snack(getString(R.string.import_parse_failed, "无法读取文件"))
                return@launch
            }
            parse(bytes, queryFileName(uri), password = null)
        }
    }

    private fun queryFileName(uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index) ?: FALLBACK_NAME
            }
        }
        return uri.lastPathSegment ?: FALLBACK_NAME
    }

    private fun parse(bytes: ByteArray, name: String, password: String?) {
        step.value = ImportStep.LOADING
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val text = BillReader.read(bytes, name, password)
                    parsedText = text
                    WeChatBillParser.parse(text)
                }
            }

            outcome.fold(
                onSuccess = { result ->
                    if (result.rows.isEmpty() && result.mapping == null) {
                        step.value = ImportStep.PICK
                        if (result.headerFields.isEmpty()) {
                            snack(getString(R.string.import_bad_format))
                        } else {
                            askColumnMapping(result.headerFields)
                        }
                        return@fold
                    }
                    if (result.rows.isEmpty()) {
                        step.value = ImportStep.PICK
                        snack(getString(R.string.import_preview_empty))
                        return@fold
                    }
                    buildPreview(result)
                },
                onFailure = { e ->
                    when (e) {
                        is PasswordRequiredException -> askPassword(bytes, name)
                        is WrongPasswordException -> {
                            step.value = ImportStep.PICK
                            snack(getString(R.string.import_password_wrong))
                        }

                        else -> {
                            step.value = ImportStep.PICK
                            snack(getString(R.string.import_parse_failed, e.message.orEmpty()))
                        }
                    }
                },
            )
        }
    }

    private fun askPassword(bytes: ByteArray, name: String) {
        step.value = ImportStep.PICK
        // 旧版是 setCancelable(false)：密码没给就没法继续，点外部不关闭。
        dialogs.input(
            title = getString(R.string.import_password_title),
            message = getString(R.string.import_help),
            initial = "",
            confirmText = getString(R.string.action_confirm),
            cancelText = getString(R.string.action_cancel),
            numeric = true,
            cancelable = false,
        ) { password ->
            parse(bytes, name, password.trim())
        }
    }

    /**
     * 自动识别表头失败时，让用户依次指定关键列。
     */
    private fun askColumnMapping(headerFields: List<String>) {
        val labels = listOf(
            getString(R.string.import_col_time),
            getString(R.string.import_col_amount),
            getString(R.string.import_col_kind),
            getString(R.string.import_col_counterparty),
            getString(R.string.import_col_item),
        )
        val indices = IntArray(labels.size) { ColumnMapping.NONE }
        val options = arrayOf(getString(R.string.import_col_none)) + headerFields

        fun askFor(position: Int) {
            if (position >= labels.size) {
                if (indices[0] == ColumnMapping.NONE || indices[1] == ColumnMapping.NONE) {
                    snack(getString(R.string.import_bad_format))
                    return
                }
                val mapping = ColumnMapping(
                    timeIndex = indices[0],
                    amountIndex = indices[1],
                    kindIndex = indices[2],
                    counterpartyIndex = indices[3],
                    itemIndex = indices[4],
                    statusIndex = ColumnMapping.NONE,
                    tradeNoIndex = ColumnMapping.NONE,
                    merchantNoIndex = ColumnMapping.NONE,
                    payMethodIndex = ColumnMapping.NONE,
                    remarkIndex = ColumnMapping.NONE,
                    tradeTypeIndex = ColumnMapping.NONE,
                )
                val text = parsedText
                if (text == null) {
                    snack(getString(R.string.import_bad_format))
                    return
                }
                val result = WeChatBillParser.parse(text, mapping)
                if (result.rows.isEmpty()) {
                    snack(getString(R.string.import_preview_empty))
                } else {
                    buildPreview(result)
                }
                return
            }

            dialogs.pickLabels(
                title = getString(R.string.import_map_columns) + "：" + labels[position],
                labels = options.toList(),
                cancelText = getString(R.string.action_cancel),
            ) { which ->
                indices[position] = which - 1
                askFor(position + 1)
            }
        }

        dialogs.confirm(
            title = getString(R.string.import_map_columns),
            message = getString(R.string.import_map_columns_hint),
            confirmText = getString(R.string.action_confirm),
            cancelText = getString(R.string.action_cancel),
        ) {
            askFor(0)
        }
    }

    private fun buildPreview(result: ParseResult) {
        step.value = ImportStep.LOADING
        lifecycleScope.launch {
            val p = withContext(Dispatchers.IO) { useCase.preview(result) }
            preview = p
            renderPreview(p)
        }
    }

    private fun renderPreview(p: ImportPreview) {
        summary.value = ImportSummary(
            total = p.total,
            newCount = p.newCount,
            duplicateCount = p.duplicateCount,
            unrecognized = p.unrecognized,
            rangeText = if (p.earliest != null && p.latest != null) {
                getString(
                    R.string.import_range,
                    Dates.formatFullDate(p.earliest),
                    Dates.formatFullDate(p.latest),
                )
            } else {
                ""
            },
        )
        rows.value = p.candidates.map(::toRow)
        step.value = ImportStep.PREVIEW
    }

    /** 把一条候选映射成列表行（金额文本 + 颜色在旧的 bind() 里就是这么算的）。 */
    private fun toRow(candidate: ImportCandidate): ImportPreviewRow {
        val row = candidate.parsed
        val subs = mutableListOf<String>()
        row.tradeType?.takeIf { it.isNotBlank() }?.let { subs += it }
        row.payMethod?.takeIf { it.isNotBlank() }?.let { subs += it }

        val amountText = when (row.kind) {
            TxKind.EXPENSE -> Money.formatSigned(row.cents, negative = true)
            TxKind.INCOME -> Money.formatSigned(row.cents, negative = false)
            else -> Money.formatCents(row.cents)
        }
        val tint = when (row.kind) {
            TxKind.EXPENSE -> 0
            TxKind.INCOME -> 1
            TxKind.TRANSFER -> 2
            TxKind.NEUTRAL -> 3
        }

        return ImportPreviewRow(
            key = candidate.sourceRef
                ?: "${row.occurredAt}-${row.cents}-${row.counterparty}-${row.item}",
            dateText = Dates.fromEpochMillis(row.occurredAt).format(SHORT_DATE),
            title = row.counterparty?.takeIf { it.isNotBlank() }
                ?: row.item?.takeIf { it.isNotBlank() }
                ?: row.tradeType.orEmpty(),
            subText = subs.joinToString(" · "),
            amountText = amountText,
            amountColor = Color(tintColor(tint)),
            duplicate = candidate.duplicate,
        )
    }

    /** 在 Activity 侧解析颜色资源（Compose 侧只消费 Color，不读资源 id）。 */
    private fun tintColor(tint: Int): Int = getColor(
        when (tint) {
            0 -> R.color.expense
            1 -> R.color.income
            2 -> R.color.transfer
            else -> R.color.on_surface_variant
        },
    )

    private fun commit() {
        val p = preview ?: return
        if (p.newCount == 0) {
            snack(getString(R.string.import_nothing_new))
            return
        }
        step.value = ImportStep.LOADING
        lifecycleScope.launch {
            val inserted = withContext(Dispatchers.IO) { useCase.commit(p) }
            appContainer.settings.hasImported = true
            snack(getString(R.string.import_done, inserted, p.duplicateCount))
            finish()
        }
    }

    private fun snack(message: String) {
        snackbar.show(message)
    }

    private companion object {
        const val FALLBACK_NAME = "bill.csv"
        val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
    }
}
