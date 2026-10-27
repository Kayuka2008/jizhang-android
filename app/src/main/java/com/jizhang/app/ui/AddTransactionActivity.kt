package com.jizhang.app.ui

import com.jizhang.app.ui.common.MiuixDatePicker
import android.content.Context
import android.content.Intent
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
import com.jizhang.app.data.db.entity.Account
import com.jizhang.app.data.db.entity.Category
import com.jizhang.app.data.db.entity.Transaction
import com.jizhang.app.data.db.entity.TxKind
import com.jizhang.app.data.db.entity.TxSource
import com.jizhang.app.money.Money
import com.jizhang.app.ui.add.AddCategoryCell
import com.jizhang.app.ui.add.AddKindTab
import com.jizhang.app.ui.add.AddTransactionScreen
import com.jizhang.app.ui.add.AddUiState
import com.jizhang.app.ui.common.CategoryIcons
import com.jizhang.app.ui.common.appContainer
import com.jizhang.app.ui.common.hapticConfirm
import com.jizhang.app.ui.common.hapticKey
import com.jizhang.app.util.Dates
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 记一笔 / 编辑账目。
 *
 * v1.53：视图层改成 Compose + miuix（原 activity_add_transaction.xml / item_picker_cell.xml /
 * PickerAdapter 全删，分类网格改用 LazyVerticalGrid）。
 *
 * ★ 业务（金额解析、分类/账户校验、写库、日期与备注弹窗、账户单选）**全部留在 Activity**。
 *   Compose 侧只消费一份 [AddUiState] 快照 + 一串回调。
 *
 * ★ 旧的 PickerAdapter 做过的「快速切换方向时旧结果不覆盖当前页」防抖（categoryLoadVersion）
 *   原样保留 —— 那是并发正确性问题，不随视图层改动。
 *
 * ★ 系统栏：顶栏避让状态栏、键盘避让导航栏，都由 insets 订阅后推进 state（绝不在组合体里现读）。
 */
class AddTransactionActivity : com.jizhang.app.ui.common.SecondaryActivity() {


    private val ui = mutableStateOf(
        AddUiState(
            kind = AddKindTab.EXPENSE,
            amountText = "0.00",
            categories = emptyList(),
            selectedCategoryId = null,
            categoryEmpty = false,
            dateText = "",
            noteText = "",
            noteIsHint = true,
            accountText = "",
            toAccountText = "",
            canSave = false,
            editing = false,
        ),
    )

    private var editingId: Long = 0

    /** 统一对话框宿主（v1.56）。 */
    private val dialogs = JizhangDialogHost()

    /** 统一提示条宿主（v1.60）。 */
    private val snackbar = JizhangSnackbarHost()

    private var kind: TxKind = TxKind.EXPENSE
    private var amountInput: String = ""
    private var selectedCategoryId: Long? = null
    private val categorySelections = mutableMapOf<TxKind, Long>()
    private var categoryLoadVersion = 0
    private var accountId: Long? = null
    private var toAccountId: Long? = null
    private var note: String = ""
    private var occurredDate: LocalDate = LocalDate.now()
    private var occurredTime: LocalTime = LocalTime.now().withSecond(0).withNano(0)

    private var accounts: List<Account> = emptyList()
    private var loadedCategories: List<Category> = emptyList()
    private var saving = false
    private var accountsLoaded = false
    private var pendingAccountRemovedNotice = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        editingId = intent.getLongExtra(EXTRA_ID, 0L)
        occurredDate = LocalDate.now()
        occurredTime = LocalTime.now().withSecond(0).withNano(0)

        val root = secondaryComposeRoot {
                // ★ v1.57：这里原来有一句 `BackHandler(enabled = editingId == 0L) { }`。
                //   BackHandler 一旦注册就会**消费**返回手势与返回键，而它的 lambda 是空的 ——
                //   等于把「退出」这个动作直接吞掉：新建账目时侧滑返回完全没有反应
                //   （注释写的是「新建时不拦返回，交给系统」，但 enabled=true 恰恰就是拦住了）。
                //   现在不注册 BackHandler：系统默认返回 = finish()，与右上角关闭按钮
                //   onClose 完全等价。
                AddTransactionScreen(
                    state = ui.value,
                    onClose = { finish() },
                    onPickKind = { pickKind(it) },
                    onDelete = { confirmDelete() },
                    onKey = { onKey(it) },
                    onClear = {
                        amountInput = ""
                        renderAmount()
                    },
                    onSave = { save() },
                    onPickDate = { pickDate() },
                    onEditNote = { editNote() },
                    onPickAccount = { pickAccount(forTransferTarget = false) },
                    onPickToAccount = { pickAccount(forTransferTarget = true) },
                    onPickCategory = { pickCategory(it) },
                )
                JizhangDialogHostView(dialogs)
                JizhangSnackbarHostView(snackbar)
        }
        setContentView(root)

        observeData()
    }

    // ---- 状态组装 ---------------------------------------------------------

    /** 把 Activity 的散装字段拼成一份快照推给 Compose。 */
    private fun pushState() {
        ui.value = AddUiState(
            kind = kind.toTab(),
            amountText = if (amountInput.isEmpty()) "0.00" else amountInput,
            categories = loadedCategories.map { c ->
                AddCategoryCell(
                    id = c.id,
                    name = c.name,
                    iconRes = CategoryIcons.of(c.name, c.emoji),
                    special = false,
                )
            } + AddCategoryCell(
                id = MANAGE_ID,
                name = getString(R.string.add_manage_category),
                iconRes = R.drawable.ic_manage,
                special = true,
            ),
            selectedCategoryId = selectedCategoryId,
            categoryEmpty = kind != TxKind.TRANSFER && loadedCategories.isEmpty(),
            dateText = Dates.sectionTitle(occurredDate),
            noteText = if (note.isBlank()) getString(R.string.add_hint_note) else note,
            noteIsHint = note.isBlank(),
            accountText = accountLabel(),
            toAccountText = toAccountLabel(),
            canSave = canSave(),
            editing = editingId != 0L,
        )
    }

    private fun TxKind.toTab(): AddKindTab = when (this) {
        TxKind.EXPENSE -> AddKindTab.EXPENSE
        TxKind.INCOME -> AddKindTab.INCOME
        else -> AddKindTab.TRANSFER
    }

    private fun AddKindTab.toKind(): TxKind = when (this) {
        AddKindTab.EXPENSE -> TxKind.EXPENSE
        AddKindTab.INCOME -> TxKind.INCOME
        AddKindTab.TRANSFER -> TxKind.TRANSFER
    }

    private fun canSave(): Boolean {
        if (saving) return false
        val hasAmount = Money.keyboardToCents(amountInput) > 0
        val needsCategory = kind != TxKind.TRANSFER
        return hasAmount && (!needsCategory || selectedCategoryId != null)
    }

    // ---- 类型切换 ---------------------------------------------------------

    private fun pickKind(tab: AddKindTab) {
        val nextKind = tab.toKind()
        if (nextKind == kind) return

        // 每个方向分别记住自己的分类，收入/支出来回切换时不会互相清空。
        rememberCategorySelection()
        kind = nextKind
        selectedCategoryId = categorySelections[kind]
        reloadCategories(resetScroll = true)
        pushState()
    }

    private fun rememberCategorySelection() {
        if (kind == TxKind.TRANSFER) return
        selectedCategoryId?.let { categorySelections[kind] = it }
            ?: categorySelections.remove(kind)
    }

    // ---- 分类 -------------------------------------------------------------

    private fun pickCategory(cell: AddCategoryCell) {
        if (cell.special) {
            startActivity(Intent(this, CategoryManageActivity::class.java))
            return
        }
        selectedCategoryId = cell.id
        categorySelections[kind] = cell.id
        appContainer.settings.lastCategoryId = cell.id
        pushState()
    }

    private fun reloadCategories(resetScroll: Boolean = false) {
        val requestedKind = kind
        val loadVersion = ++categoryLoadVersion
        // resetScroll 目前只影响旧版的滚动定位；Compose 网格由 LazyVerticalGrid 自行管理，
        // 这里保留形参以维持调用点语义。
        if (resetScroll) { /* no-op */ }

        lifecycleScope.launch {
            val list: List<Category> = when (requestedKind) {
                TxKind.INCOME -> appContainer.categoryRepo.listByKind(TxKind.INCOME)
                TxKind.EXPENSE -> appContainer.categoryRepo.listByKind(TxKind.EXPENSE)
                TxKind.TRANSFER, TxKind.NEUTRAL -> emptyList()
            }

            // 快速切换方向时只接受最后一次请求，避免旧结果覆盖当前页面。
            if (requestedKind != kind || loadVersion != categoryLoadVersion) return@launch

            var selected = selectedCategoryId
            if (selected != null && list.none { it.id == selected }) {
                val invalidId = selected
                val existing = appContainer.categoryRepo.findById(invalidId)
                if (existing == null || existing.kind == requestedKind) {
                    selected = null
                    selectedCategoryId = null
                    categorySelections.remove(requestedKind)
                    if (appContainer.settings.lastCategoryId == invalidId) {
                        appContainer.settings.lastCategoryId = 0L
                    }
                    snack(getString(R.string.add_category_removed))
                } else {
                    // 分类仍然存在，只是属于另一个方向；留给切换方向时恢复。
                    categorySelections[existing.kind] = existing.id
                    selected = null
                    selectedCategoryId = null
                }
            } else if (selected != null) {
                categorySelections[requestedKind] = selected
            }

            loadedCategories = list
            pushState()
        }
    }

    // ---- 金额键盘 ---------------------------------------------------------

    private fun onKey(key: String) {
        amountInput = Money.appendKey(amountInput, key)
        renderAmount()
        // 每次按键给一下最轻的触感（受设置里「触感反馈」开关控制，见 Haptics.kt）。
        hapticKey()
    }

    private fun renderAmount() {
        pushState()
    }

    // ---- 数据观察 ---------------------------------------------------------

    private fun observeData() {
        lifecycleScope.launch {
            appContainer.accountRepo.observeActive().collectLatest { list ->
                accounts = list
                if (accountsLoaded && accountId != null && list.none { it.id == accountId }) {
                    pendingAccountRemovedNotice = true
                }
                if (accountId == null || list.none { it.id == accountId }) {
                    accountId = resolveDefaultAccount(list)
                }
                if (toAccountId == null || list.none { it.id == toAccountId }) {
                    toAccountId = list.firstOrNull { it.id != accountId }?.id
                }
                if (pendingAccountRemovedNotice) {
                    pendingAccountRemovedNotice = false
                    snack(getString(R.string.add_account_removed))
                }
                accountsLoaded = true
                pushState()
            }
        }

        if (editingId != 0L) {
            pushState()
            lifecycleScope.launch {
                appContainer.transactionRepo.findById(editingId)?.let { prefill(it) }
            }
        } else {
            amountInput = ""
            if (appContainer.settings.rememberLastSelection) {
                val last = appContainer.settings.lastCategoryId
                if (last != 0L) selectedCategoryId = last
            }
            pushState()
            reloadCategories(resetScroll = true)
        }
    }

    private suspend fun resolveDefaultAccount(list: List<Account>): Long? {
        val preferred = appContainer.settings.defaultAccountId
        if (preferred != 0L && list.any { it.id == preferred }) return preferred
        return list.firstOrNull()?.id
    }

    private fun prefill(tx: Transaction) {
        kind = tx.kind
        amountInput = Money.formatCents(tx.cents)
        selectedCategoryId = tx.categoryId
        tx.categoryId?.let { categorySelections[tx.kind] = it }
        accountId = tx.accountId
        toAccountId = tx.toAccountId
        note = tx.note.orEmpty()

        val dt = Dates.fromEpochMillis(tx.occurredAt)
        occurredDate = dt.toLocalDate()
        occurredTime = dt.toLocalTime().withSecond(0).withNano(0)

        pushState()
        reloadCategories(resetScroll = true)
    }

    private fun accountLabel(): String {
        val id = accountId ?: return getString(R.string.add_need_account)
        return accounts.firstOrNull { it.id == id }?.name ?: getString(R.string.add_need_account)
    }

    private fun toAccountLabel(): String {
        val name = accounts.firstOrNull { it.id == toAccountId }?.name
        return if (name == null) {
            getString(R.string.add_label_to_account)
        } else {
            getString(R.string.add_label_to_account) + "：" + name
        }
    }

    // ---- 弹窗 -------------------------------------------------------------

    private fun pickDate() {
        dialogs.custom(title = getString(R.string.date_picker_title)) { dismiss ->
            MiuixDatePicker(
                initialDate = occurredDate,
                onCancel = dismiss,
                onConfirm = { date ->
                    occurredDate = date
                    pushState()
                    dismiss()
                },
            )
        }
    }

    private fun editNote() {
        dialogs.input(
            title = getString(R.string.add_label_note),
            initial = note,
            confirmText = getString(R.string.action_confirm),
            cancelText = getString(R.string.action_cancel),
            maxLines = 2,
        ) { text ->
            note = text.trim()
            pushState()
        }
    }

    private fun pickAccount(forTransferTarget: Boolean) {
        if (accounts.isEmpty()) {
            startActivity(Intent(this, AccountManageActivity::class.java))
            return
        }
        val names = accounts.map { "${it.name}（${it.type.label}）" }.toTypedArray()
        val current = if (forTransferTarget) toAccountId else accountId
        val checked = accounts.indexOfFirst { it.id == current }

        dialogs.pickLabels(
            title = getString(
                if (forTransferTarget) R.string.add_label_to_account else R.string.add_label_account,
            ),
            labels = names.toList(),
            selectedIndex = checked,
            cancelText = getString(R.string.action_cancel),
        ) { which ->
            val picked = accounts[which].id
            if (forTransferTarget) {
                toAccountId = picked
            } else {
                accountId = picked
            }
            pushState()
        }
    }

    // ---- 保存 -------------------------------------------------------------

    private fun save() {
        if (saving) return
        val cents = Money.keyboardToCents(amountInput)
        if (cents <= 0) {
            snack(getString(R.string.add_need_amount))
            return
        }
        if (kind != TxKind.TRANSFER && selectedCategoryId == null) {
            snack(getString(R.string.add_need_category))
            return
        }
        if (accountId == null) {
            snack(getString(R.string.add_need_account))
            return
        }
        if (kind == TxKind.TRANSFER) {
            if (toAccountId == null) {
                snack(getString(R.string.add_need_to_account))
                return
            }
            if (toAccountId == accountId) {
                snack(getString(R.string.add_same_account))
                return
            }
        }

        val occurredAt = LocalDateTime.of(occurredDate, occurredTime)
            .atZone(Dates.ZONE).toInstant().toEpochMilli()

        saving = true
        pushState()
        lifecycleScope.launch {
            try {
                // 管理页可能刚删除或归档了当前选择，保存前以数据库状态为准。
                val fromAccount = accountId?.let { appContainer.accountRepo.findById(it) }
                if (fromAccount == null || fromAccount.archived) {
                    accountId = null
                    pushState()
                    snack(getString(R.string.add_account_removed))
                    return@launch
                }

                if (kind == TxKind.TRANSFER) {
                    val toAccount = toAccountId?.let { appContainer.accountRepo.findById(it) }
                    if (toAccount == null || toAccount.archived) {
                        toAccountId = null
                        pushState()
                        snack(getString(R.string.add_need_to_account))
                        return@launch
                    }
                    if (toAccount.id == fromAccount.id) {
                        snack(getString(R.string.add_same_account))
                        return@launch
                    }
                } else {
                    val category = selectedCategoryId?.let { appContainer.categoryRepo.findById(it) }
                    if (category == null || category.kind != kind) {
                        val staleId = selectedCategoryId
                        selectedCategoryId = null
                        categorySelections.remove(kind)
                        if (staleId != null && appContainer.settings.lastCategoryId == staleId) {
                            appContainer.settings.lastCategoryId = 0L
                        }
                        reloadCategories()
                        snack(getString(R.string.add_category_removed))
                        return@launch
                    }
                }

                if (editingId == 0L) {
                    appContainer.transactionRepo.add(
                        Transaction(
                            kind = kind,
                            cents = cents,
                            categoryId = if (kind == TxKind.TRANSFER) null else selectedCategoryId,
                            accountId = fromAccount.id,
                            toAccountId = if (kind == TxKind.TRANSFER) toAccountId else null,
                            occurredAt = occurredAt,
                            note = note.takeIf { it.isNotBlank() },
                            source = TxSource.MANUAL,
                            confirmed = true,
                        ),
                    )
                } else {
                    val original = appContainer.transactionRepo.findById(editingId)
                    if (original == null) {
                        snack(getString(R.string.add_transaction_removed))
                        finish()
                        return@launch
                    }
                    appContainer.transactionRepo.update(
                        original.copy(
                            kind = kind,
                            cents = cents,
                            categoryId = if (kind == TxKind.TRANSFER) null else selectedCategoryId,
                            accountId = fromAccount.id,
                            toAccountId = if (kind == TxKind.TRANSFER) toAccountId else null,
                            occurredAt = occurredAt,
                            note = note.takeIf { it.isNotBlank() },
                        ),
                    )
                }
                appContainer.settings.lastCategoryId = selectedCategoryId ?: 0L
                hapticConfirm()
                finish()
            } finally {
                saving = false
                pushState()
            }
        }
    }

    private fun confirmDelete() {
        dialogs.confirm(
            message = getString(R.string.add_delete_confirm),
            confirmText = getString(R.string.action_delete),
            cancelText = getString(R.string.action_cancel),
        ) {
            lifecycleScope.launch {
                appContainer.transactionRepo.deleteById(editingId)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 分类/账户可能在管理页被改动
        reloadCategories()
        if (accountId != null && accounts.none { it.id == accountId }) {
            accountId = null
            snack(getString(R.string.add_account_removed))
        }
        if (toAccountId != null && accounts.none { it.id == toAccountId }) {
            toAccountId = null
        }
        pushState()
    }

    private fun snack(message: String) {
        snackbar.show(message)
    }

    companion object {
        private const val EXTRA_ID = "tx_id"
        private const val MANAGE_ID = -1L

        fun editIntent(context: Context, txId: Long): Intent =
            Intent(context, AddTransactionActivity::class.java).putExtra(EXTRA_ID, txId)
    }
}
