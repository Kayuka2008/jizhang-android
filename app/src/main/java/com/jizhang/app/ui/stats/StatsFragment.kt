package com.jizhang.app.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jizhang.app.R
import com.jizhang.app.data.db.entity.TxKind
import com.jizhang.app.money.Money
import com.jizhang.app.ui.common.GlassBarInsets
import com.jizhang.app.ui.MainActivity
import com.jizhang.app.ui.common.PageScrollReporter
import com.jizhang.app.ui.common.ScrollOffsetPage
import com.jizhang.app.ui.common.appContainer
import com.jizhang.app.util.Dates
import com.jizhang.app.widget.BarPoint
import com.jizhang.app.widget.DonutSlice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

/**
 * 统计页（v1.51：渲染层搬到 Compose）。
 *
 * 和 [com.jizhang.app.ui.pending.PendingFragment] 同构：本 Fragment 只做
 *   · 组装 ComposeView；
 *   · 查数据库、算 [StatsUi]（这部分是命令式的，原样保留）；
 *   · 处理月份切换与"支出 / 收入构成"开关。
 *
 * 渲染与图表绘制交给 [StatsScreen]，底部避让由 [GlassBarInsets] 提供真实底栏高度。
 */
class StatsFragment : Fragment(), ScrollOffsetPage {

    /** v1.64：底栏显隐的偏移上报器（累积值要能被 Activity 读到，所以挂在 Fragment 上）。 */
    private var scrollReporter: PageScrollReporter? = null

    override fun currentScrollOffsetPx(): Float = scrollReporter?.current ?: 0f

    private val yearMonth = MutableStateFlow(YearMonth.now())

    private var renderJob: Job? = null
    private var renderVersion = 0

    /** 点击标题可在支出/收入构成之间切换。 */
    private var showIncomeBreakdown = false

    private var ui by mutableStateOf(emptyUi())
    private var statusBarHeight by mutableStateOf(0.dp)
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
        val composeView = ComposeView(inflater.context).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                StatsScreen(
                    ui = ui,
                    statusBarHeight = statusBarHeight,
                    bottomInset = glassBarHeight,
                    onPrevMonth = { yearMonth.value = yearMonth.value.minusMonths(1) },
                    onNextMonth = {
                        val next = yearMonth.value.plusMonths(1)
                        if (next <= YearMonth.now()) yearMonth.value = next
                    },
                    onToggleBreakdown = {
                        showIncomeBreakdown = !showIncomeBreakdown
                        requestRender(yearMonth.value)
                    },
                    scrollReporter = scrollReporter,
                )
            }
        }
        // 由普通容器监听 Insets，避免 ComposeView 建立组合时覆盖监听器。
        return FrameLayout(inflater.context).apply {
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
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
                    yearMonth.collect { requestRender(it) }
                }
                launch {
                    // 账目变化时重算
                    appContainer.transactionRepo.observeAll().collect {
                        requestRender(yearMonth.value)
                    }
                }
            }
        }
    }

    private data class StatsData(
        val expense: Long,
        val income: Long,
        val count: Int,
        val byCategoryExpense: List<Pair<Long?, Long>>,
        val byCategoryIncome: List<Pair<Long?, Long>>,
        val monthly: List<Pair<YearMonth, Long>>,
        val monthlyIncome: List<Pair<YearMonth, Long>>,
        val categories: List<com.jizhang.app.data.db.entity.Category>,
    )

    /**
     * 每次请求都绑定自己的月份，并取消前一个渲染任务。
     * 这样快速切月时，旧查询即使更晚返回也不能覆盖新月份。
     */
    private fun requestRender(ym: YearMonth) {
        renderJob?.cancel()
        val version = ++renderVersion
        renderJob = viewLifecycleOwner.lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                StatsData(
                    expense = appContainer.transactionRepo.sumOfKind(ym, TxKind.EXPENSE),
                    income = appContainer.transactionRepo.sumOfKind(ym, TxKind.INCOME),
                    count = appContainer.transactionRepo.countInMonth(ym),
                    byCategoryExpense = appContainer.transactionRepo
                        .sumByCategory(ym, TxKind.EXPENSE).map { it.categoryId to it.cents },
                    byCategoryIncome = appContainer.transactionRepo
                        .sumByCategory(ym, TxKind.INCOME).map { it.categoryId to it.cents },
                    monthly = appContainer.transactionRepo.monthlyTotals(6, TxKind.EXPENSE),
                    monthlyIncome = appContainer.transactionRepo.monthlyTotals(6, TxKind.INCOME),
                    categories = appContainer.categoryRepo.listAll(),
                )
            }
            // 取消防抖只降低开销；版本号负责阻止极窄竞态下的旧结果回写。
            if (version == renderVersion && yearMonth.value == ym && isAdded) {
                ui = buildUi(data, ym)
            }
        }
    }

    private fun buildUi(data: StatsData, ym: YearMonth): StatsUi {
        val context = requireContext()
        val isCurrentMonth = ym == YearMonth.now()
        val daysElapsed = if (isCurrentMonth) {
            minOf(Dates.today().dayOfMonth, ym.lengthOfMonth())
        } else {
            ym.lengthOfMonth()
        }

        val balance = data.income - data.expense
        val balanceText = (if (balance >= 0) "+" else "-") +
            Money.formatCentsWithSymbol(kotlin.math.abs(balance))

        val hasData = data.expense > 0 || data.income > 0 || data.count > 0

        // ---- 分类构成 ----
        val breakdown = if (showIncomeBreakdown) data.byCategoryIncome else data.byCategoryExpense
        val catMap = data.categories.associateBy { it.id }
        val palette = PALETTE.map { ContextCompat.getColor(context, it) }

        val allSlices = breakdown.mapIndexed { index, (categoryId, cents) ->
            DonutSlice(
                label = catMap[categoryId]?.name ?: getString(R.string.stats_other_category),
                value = cents,
                color = palette[index % palette.size],
            )
        }

        // 分类过多时把尾部合并为「其他」
        val maxSlices = 7
        val displayEntries: List<Pair<com.jizhang.app.data.db.entity.Category?, DonutSlice>>
        if (allSlices.size > maxSlices) {
            val headSlices = allSlices.take(maxSlices - 1)
            val otherSlice = DonutSlice(
                label = getString(R.string.stats_other_category),
                value = allSlices.drop(maxSlices - 1).sumOf { it.value },
                color = palette.last(),
            )
            displayEntries = headSlices.mapIndexed { index, slice ->
                catMap[breakdown[index].first] to slice
            } + (null to otherSlice)
        } else {
            displayEntries = allSlices.mapIndexed { index, slice ->
                catMap[breakdown[index].first] to slice
            }
        }

        val total = displayEntries.sumOf { it.second.value }
        val ranking = displayEntries.map { (category, slice) ->
            val percent = if (total > 0) slice.value * 100.0 / total else 0.0
            RankRow(
                colorArgb = slice.color,
                name = category?.name ?: slice.label,
                percentText = String.format("%.1f%%", percent),
                amountText = Money.formatCentsWithSymbol(slice.value),
            )
        }

        // ---- 趋势 ----
        val points = data.monthly.indices.map { index ->
            val (month, expense) = data.monthly[index]
            BarPoint(
                label = "${month.monthValue}月",
                expense = expense,
                income = data.monthlyIncome.getOrNull(index)?.second ?: 0L,
            )
        }

        return StatsUi(
            monthLabel = Dates.formatMonth(ym),
            canGoNext = ym < YearMonth.now(),
            expenseText = Money.formatCentsWithSymbol(data.expense),
            incomeText = Money.formatCentsWithSymbol(data.income),
            balanceText = balanceText,
            balanceNegative = balance < 0,
            avgText = Money.formatCentsWithSymbol(
                if (daysElapsed > 0) data.expense / daysElapsed else 0L,
            ),
            countText = getString(R.string.stats_count_format, data.count),
            hasData = hasData,
            byIncome = showIncomeBreakdown,
            donutSlices = displayEntries.map { it.second },
            donutCenter = getString(
                if (showIncomeBreakdown) R.string.stats_income else R.string.stats_expense,
            ),
            ranking = ranking,
            trendPoints = points,
        )
    }

    /** 还没查到数据时的占位，避免首帧出现空指针式的空白（日期/数字都有值）。 */
    private fun emptyUi(): StatsUi {
        val zero = Money.formatCentsWithSymbol(0L)
        return StatsUi(
            monthLabel = "",
            canGoNext = false,
            expenseText = zero,
            incomeText = zero,
            balanceText = zero,
            balanceNegative = false,
            avgText = zero,
            countText = "",
            hasData = false,
            byIncome = false,
            donutSlices = emptyList(),
            donutCenter = "",
            ranking = emptyList(),
            trendPoints = emptyList(),
        )
    }

    /** 同理 [com.jizhang.app.ui.pending.PendingFragment.observeStatusBarHeight]。 */
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

    private companion object {
        val PALETTE = listOf(
            R.color.chart_1,
            R.color.chart_2,
            R.color.chart_3,
            R.color.chart_4,
            R.color.chart_5,
            R.color.chart_6,
            R.color.chart_7,
        )
    }
}
