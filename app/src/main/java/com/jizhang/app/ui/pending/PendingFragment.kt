package com.jizhang.app.ui.pending

import android.widget.FrameLayout
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jizhang.app.R
import com.jizhang.app.data.db.entity.Account
import com.jizhang.app.data.db.entity.Category
import com.jizhang.app.data.db.entity.Confidence
import com.jizhang.app.data.db.entity.PendingItem
import com.jizhang.app.data.db.entity.TxKind
import com.jizhang.app.data.repo.ConfirmResult
import com.jizhang.app.data.repo.PendingConfirmation
import com.jizhang.app.notify.NotifAccess
import com.jizhang.app.ui.common.GlassBarInsets
import com.jizhang.app.ui.MainActivity
import com.jizhang.app.ui.common.PageScrollReporter
import com.jizhang.app.ui.common.ScrollOffsetPage
import com.jizhang.app.ui.common.JizhangDialogHost
import com.jizhang.app.ui.common.JizhangDialogHostView
import com.jizhang.app.ui.common.JizhangSnackbarHost
import com.jizhang.app.ui.common.JizhangSnackbarHostView
import com.jizhang.app.ui.common.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration

/**
 * 待确认队列（v1.51：渲染层搬到 Compose）。
 *
 * 本 Fragment 现在只剩三件事，和 [com.jizhang.app.ui.ledger.LedgerFragment] 同构：
 *   · 建一个有 inset 处理的 ComposeView；
 *   · 把 Flow 数据转成 Compose 状态；
 *   · 业务逻辑（确认 / 忽略 / 选择器 / 批量确认）—— 这部分是命令式的，原样保留。
 *
 * [PendingScreen] 只做渲染，不做决定。
 */
class PendingFragment : Fragment(), ScrollOffsetPage {

    /** v1.64：底栏显隐的偏移上报器（累积值要能被 Activity 读到，所以挂在 Fragment 上）。 */
    private var scrollReporter: PageScrollReporter? = null

    override fun currentScrollOffsetPx(): Float = scrollReporter?.current ?: 0f

    private var rows by mutableStateOf<List<PendingRow>>(emptyList())
    private var busyIds by mutableStateOf<Set<Long>>(emptySet())
    private var bulkBusy by mutableStateOf(false)

    /** 业务数据的最新快照（业务方法要用，渲染不读它 —— 渲染只读 [rows]）。 */
    private var categories: List<Category> = emptyList()
    private var accounts: List<Account> = emptyList()
    private var items: List<PendingItem> = emptyList()
    private var bulkConfirming = false

    /** 统一对话框宿主（v1.56）。 */
    private val dialogs = JizhangDialogHost()

    /** 统一提示条宿主（v1.60）。 */
    private val snackbar = JizhangSnackbarHost()

    /**
     * 状态栏高度（dp），由 inset 回调写入 —— 见 [observeStatusBarHeight]。
     *
     * ★ 这里刻意**不在组合体里现读**：首次组合发生在视图 attach 之前，
     *   那时 `getRootWindowInsets` 拿不到东西，会静默返回 0.dp（v1.45 在明细页
     *   踩过同一个坑：几何塌掉但完全不报错）。
     */
    private var statusBarHeight by mutableStateOf(0.dp)

    /** 悬浮底栏高度，用于列表尾部避让。 */
    private var glassBarHeight by mutableStateOf(0.dp)

    private var glassBarInsets: GlassBarInsets? = null

    /** v1.64：滚动在 Compose 里，View 树里没有滚动容器 ⇒ 底栏判据只能靠页面上报。 */
    private fun newScrollReporter(): PageScrollReporter = PageScrollReporter { px ->
        (activity as? MainActivity)?.onPageScroll(px)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        scrollReporter = newScrollReporter()
        val compose = ComposeView(inflater.context).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                com.jizhang.app.ui.settings.JizhangMiuixTheme {
                PendingScreen(
                    rows = rows,
                    busyIds = busyIds,
                    bulkBusy = bulkBusy,
                    statusBarHeight = statusBarHeight,
                    bottomInset = glassBarHeight,
                    onConfirmAll = { confirmAllHighConfidence() },
                    onOpenNotifSettings = { NotifAccess.openSettings(requireContext()) },
                    onConfirm = { confirm(it) },
                    onIgnore = { ignore(it) },
                    onPickCategory = { pickCategory(it) },
                    onPickAccount = { pickAccount(it) },
                    onPickToAccount = { pickToAccount(it) },
                    scrollReporter = scrollReporter,
                )
                JizhangDialogHostView(dialogs)
                JizhangSnackbarHostView(snackbar)
                }
            }
        }
        // 由普通父容器接收 inset，避免 Compose 的监听覆盖状态栏避让。
        return FrameLayout(inflater.context).apply {
            addView(compose, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeStatusBarHeight(view)
        glassBarInsets = GlassBarInsets(view) { glassBarHeight = it }.also { it.start() }
        observe()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    appContainer.categoryRepo.observeAll().collect { list ->
                        categories = list
                        rows = renderRows()
                    }
                }
                launch {
                    appContainer.accountRepo.observeActive().collect { list ->
                        accounts = list
                        rows = renderRows()
                    }
                }
                launch {
                    appContainer.pendingRepo.observePending().collect { list ->
                        items = list
                        rows = renderRows()
                    }
                }
            }
        }
    }

    private fun renderRows(): List<PendingRow> = items.map { item ->
        PendingRow(
            item = item,
            categoryName = item.suggestedCategoryId
                ?.let { id -> categories.firstOrNull { it.id == id } }
                ?.name,
            accountName = item.suggestedAccountId
                ?.let { id -> accounts.firstOrNull { it.id == id } }
                ?.name
                ?: accounts.firstOrNull()?.name,
            toAccountName = item.suggestedToAccountId
                ?.let { id -> accounts.firstOrNull { it.id == id } }
                ?.name,
        )
    }

    /** 待确认条目 -> 账目的统一校验，单项与批量确认共用。 */
    private fun confirmation(item: PendingItem): PendingConfirmation.Result {
        val kind = item.kind ?: TxKind.EXPENSE
        return PendingConfirmation.build(
            item = item,
            fallbackCategoryId = categories.firstOrNull { it.kind == kind }?.id,
            fallbackAccountId = accounts.firstOrNull()?.id,
        )
    }

    private fun messageFor(result: PendingConfirmation.Result): String = getString(
        when (result) {
            PendingConfirmation.Result.MissingAmount -> R.string.pending_need_amount
            PendingConfirmation.Result.MissingAccount -> R.string.add_need_account
            PendingConfirmation.Result.MissingCategory -> R.string.add_need_category
            PendingConfirmation.Result.MissingToAccount -> R.string.add_need_to_account
            PendingConfirmation.Result.SameAccount -> R.string.add_same_account
            is PendingConfirmation.Result.Valid -> R.string.pending_confirm_invalid
        },
    )

    private fun confirm(item: PendingItem) {
        if (item.id in busyIds || bulkConfirming) return
        val confirmation = confirmation(item)
        if (confirmation !is PendingConfirmation.Result.Valid) {
            snackbar.show(messageFor(confirmation))
            return
        }
        val tx = confirmation.transaction
        busyIds = busyIds + item.id
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (appContainer.pendingRepo.confirm(item, tx)) {
                    is ConfirmResult.Confirmed -> {
                        learnFrom(item, tx.categoryId)
                        snackbar.show(
                            getString(R.string.pending_confirmed),
                            duration = SnackbarDuration.Custom(2_000),
                        )
                    }

                    ConfirmResult.AlreadyResolved -> {
                        snackbar.show(getString(R.string.pending_already_resolved))
                    }

                    ConfirmResult.Duplicate -> {
                        snackbar.show(getString(R.string.pending_duplicate))
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                snackbar.show(getString(R.string.pending_action_failed))
            } finally {
                busyIds = busyIds - item.id
            }
        }
    }

    /** 记住「交易对方 → 分类」，下次自动推荐。 */
    private suspend fun learnFrom(item: PendingItem, categoryId: Long?) {
        val name = item.counterparty ?: return
        val cid = categoryId ?: return
        appContainer.categoryRepo.learnKeyword(name, cid)
    }

    private fun ignore(item: PendingItem) {
        if (item.id in busyIds || bulkConfirming) return
        busyIds = busyIds + item.id
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ignored = appContainer.pendingRepo.ignore(item.id)
                snackbar.show(
                    getString(if (ignored) R.string.pending_ignored else R.string.pending_already_resolved),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                snackbar.show(getString(R.string.pending_action_failed))
            } finally {
                busyIds = busyIds - item.id
            }
        }
    }

    /** 批量确认高置信度且金额有效的条目。 */
    private fun confirmAllHighConfidence() {
        if (bulkConfirming) return
        val targets = items.filter {
            it.confidence == Confidence.HIGH && it.cents != null && it.cents > 0
        }
        if (targets.isEmpty()) {
            snackbar.show(getString(R.string.pending_need_amount))
            return
        }
        dialogs.confirm(
            message = getString(R.string.pending_confirm_all_question, targets.size),
            confirmText = getString(R.string.action_confirm),
            cancelText = getString(R.string.action_cancel),
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                bulkConfirming = true
                bulkBusy = true
                var done = 0
                var failed = 0
                try {
                    targets.forEach { item ->
                        val confirmation = confirmation(item)
                        if (confirmation !is PendingConfirmation.Result.Valid) {
                            failed++
                            return@forEach
                        }
                        val tx = confirmation.transaction
                        busyIds = busyIds + item.id
                        try {
                            when (appContainer.pendingRepo.confirm(item, tx)) {
                                is ConfirmResult.Confirmed -> {
                                    learnFrom(item, tx.categoryId)
                                    done++
                                }

                                // 已处理过、或同来源账目已存在：都算本次未入账
                                ConfirmResult.AlreadyResolved, ConfirmResult.Duplicate -> failed++
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            failed++
                        } finally {
                            busyIds = busyIds - item.id
                        }
                    }
                } finally {
                    bulkConfirming = false
                    bulkBusy = false
                }
                snackbar.show(
                    getString(R.string.pending_bulk_result, done, failed),
                    duration = if (failed == 0) SnackbarDuration.Custom(2_000) else SnackbarDuration.Long,
                )
            }
        }
    }

    private fun pickCategory(item: PendingItem) {
        val kind = item.kind ?: TxKind.EXPENSE
        val options = categories.filter { it.kind == kind || kind == TxKind.TRANSFER }
        if (options.isEmpty()) return
        val labels = options.map { it.name }.toTypedArray()
        val current = options.indexOfFirst { it.id == item.suggestedCategoryId }

        dialogs.pickLabels(
            title = getString(R.string.add_label_category),
            labels = labels.toList(),
            selectedIndex = current,
            cancelText = getString(R.string.action_cancel),
        ) { which ->
            val picked = options[which]
            viewLifecycleOwner.lifecycleScope.launch {
                val updated = runCatching {
                    appContainer.pendingRepo.setSuggestedCategory(item.id, picked.id)
                }.getOrElse {
                    if (it is CancellationException) throw it
                    snackbar.show(getString(R.string.pending_action_failed))
                    return@launch
                }
                if (!updated) {
                    snackbar.show(getString(R.string.pending_already_resolved))
                }
            }
        }
    }

    private fun pickAccount(item: PendingItem) {
        if (accounts.isEmpty()) return
        val labels = accounts.map { it.name }.toTypedArray()
        val current = accounts.indexOfFirst { it.id == item.suggestedAccountId }

        dialogs.pickLabels(
            title = getString(R.string.add_label_account),
            labels = labels.toList(),
            selectedIndex = current,
            cancelText = getString(R.string.action_cancel),
        ) { which ->
            val picked = accounts[which]
            viewLifecycleOwner.lifecycleScope.launch {
                val updated = runCatching {
                    appContainer.pendingRepo.setSuggestedAccount(item.id, picked.id)
                }.getOrElse {
                    if (it is CancellationException) throw it
                    snackbar.show(getString(R.string.pending_action_failed))
                    return@launch
                }
                if (!updated) {
                    snackbar.show(getString(R.string.pending_already_resolved))
                }
            }
        }
    }

    private fun pickToAccount(item: PendingItem) {
        if (accounts.isEmpty()) return
        val fromId = item.suggestedAccountId ?: accounts.firstOrNull()?.id
        val options = accounts.filter { it.id != fromId }
        if (options.isEmpty()) {
            snackbar.show(getString(R.string.pending_need_second_account))
            return
        }
        val labels = options.map { it.name }.toTypedArray()
        val current = options.indexOfFirst { it.id == item.suggestedToAccountId }

        dialogs.pickLabels(
            title = getString(R.string.add_label_to_account),
            labels = labels.toList(),
            selectedIndex = current,
            cancelText = getString(R.string.action_cancel),
        ) { which ->
            val picked = options[which]
            viewLifecycleOwner.lifecycleScope.launch {
                val updated = runCatching {
                    appContainer.pendingRepo.setSuggestedToAccount(item.id, picked.id)
                }.getOrElse {
                    if (it is CancellationException) throw it
                    snackbar.show(getString(R.string.pending_action_failed))
                    return@launch
                }
                if (!updated) {
                    snackbar.show(getString(R.string.pending_already_resolved))
                }
            }
        }
    }

    /**
     * 把状态栏高度接进 [statusBarHeight]。
     *
     * 必须 **返回原样 insets**：这一层只是旁路读一个数，不能消费 inset。
     */
    private fun observeStatusBarHeight(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
            ).top
            statusBarHeight = Dp(top / resources.displayMetrics.density)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    override fun onDestroyView() {
        glassBarInsets?.stop()
        glassBarInsets = null
        super.onDestroyView()
    }
}

/** 待确认行渲染所需的数据。 */
data class PendingRow(
    val item: PendingItem,
    val categoryName: String?,
    val accountName: String?,
    val toAccountName: String?,
)
