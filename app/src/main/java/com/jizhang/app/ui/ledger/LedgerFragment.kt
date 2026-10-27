package com.jizhang.app.ui.ledger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.jizhang.app.data.repo.LedgerFilter
import com.jizhang.app.ui.AddTransactionActivity
import com.jizhang.app.ui.MainActivity
import com.jizhang.app.ui.common.Factory
import com.jizhang.app.ui.common.GlassBarInsets
import com.jizhang.app.ui.common.PageScrollReporter
import com.jizhang.app.ui.common.ScrollOffsetPage
import com.jizhang.app.ui.common.appContainer
import com.jizhang.app.util.glassEffectsEnabled
import com.jizhang.app.ui.settings.JizhangMiuixTheme

/**
 * 明细页（主页）。
 *
 * ## v1.43：页面改成纯 Compose
 *
 * 渲染层整体交给 [LedgerScreen]，本 Fragment 只剩三件事：
 *
 *   · 下拉刷新在 [LedgerScreen] 内部用 miuix `PullToRefresh` 实现（v1.61）——
 *     以前是 View 侧的 `SwipeRefreshLayout` 套在 ComposeView 外面，连带需要
 *     覆写 `canChildScrollUp` 并从 Compose 注册一个查询（见 git 历史 v1.45），
 *     那条 workaround 这一版整体删除，手势同步改由 Compose nestedScroll 天然承担；
 *   · 把 ViewModel 的状态喂给 Compose；
 *   · 持有筛选面板的开关状态（v1.55：面板本体已改成 Compose，见 [LedgerFilterSheet]；
 *     旧的 `SheetFilterBinding` + Material ChipGroup + MaterialAlertDialog 那条链已删）。
 *
 * 顶栏玻璃不再由 `ThemedActivity` 装：它画在 [LedgerScreen] 内部
 * （根 Box 的最后一个子节点），采样走 `rememberLayerBackdrop`，
 * 与内容是**同一帧、同一次绘制遍历**，不再有「玻璃自己重画一遍 View 树」这回事。
 * 详见 `StatusBarGlass.kt` 与 `LedgerScreen.kt` 的文件头注释。
 */
class LedgerFragment : Fragment(), ScrollOffsetPage {

    /** v1.64：底栏显隐的偏移上报器（累积值要能被 Activity 读到，所以挂在 Fragment 上）。 */
    private var scrollReporter: PageScrollReporter? = null

    override fun currentScrollOffsetPx(): Float = scrollReporter?.current ?: 0f

    private val viewModel: LedgerViewModel by viewModels {
        Factory {
            LedgerViewModel(
                appContainer.transactionRepo,
                appContainer.categoryRepo,
                appContainer.accountRepo,
            )
        }
    }

    /**
     * 状态栏高度（dp），由 inset 回调写入 —— 见 [observeStatusBarHeight]。
     *
     * ★ v1.45 修掉的静默 bug：原来它在 `setContent { }` 的**组合体里**
     *   `view?.let { ViewCompat.getRootWindowInsets(it) }` 现读，
     *   而首次组合发生在 `onCreateView` 返回之后、视图**尚未 attach**时，
     *   `view` 为 null ⇒ 直接返回 `0.dp`。而 `0.dp` 不是一个"看起来不对"的值：
     *   顶栏玻璃的几何整个塌成一条（只有渐隐带 + 尾巴），
     *   LazyColumn 的 `contentPadding.top` 也变成 0 ⇒ 内容顶到系统图标底下。
     *   两处症状都不会报错，只能靠肉眼发现。
     *
     * 所以改成：值由 inset 分发**推**进来，组合体只负责读。
     * 首帧即使还是 0，inset 一到就会重组修正，不会停在那里。
     */
    private val statusBarHeightState = mutableStateOf(0.dp)

    /** 当前页面与悬浮底栏的实际重叠高度。 */
    private val bottomInsetState = mutableStateOf(0.dp)
    private var glassBarInsets: GlassBarInsets? = null

    /**
     * 筛选面板开关（v1.55）。
     *
     * 面板本体是 Compose 的 [LedgerFilterSheet]，它需要一个「是否显示」的真值 ——
     * 放在 Fragment 而不是组合体里，是为了让配置变化/返回前台后开关状态不丢。
     */
    private val filterSheetVisible = mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = inflater.context
        // v1.64：滚动在 Compose 里，View 树里没有滚动容器 ⇒ 底栏判据只能靠页面上报。
        val reporter = PageScrollReporter { px ->
            (activity as? MainActivity)?.onPageScroll(px)
        }
        scrollReporter = reporter
        val glassEnabled = context.glassEffectsEnabled()
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                val state = viewModel.uiState.collectAsState()
                val refreshing = viewModel.refreshing.collectAsState()
                val accounts = viewModel.accountMap.collectAsState()
                val categories = viewModel.categoryList.collectAsState()
                JizhangMiuixTheme {
                    LedgerScreen(
                        state = state.value,
                        statusBarHeight = statusBarHeightState.value,
                        bottomInset = bottomInsetState.value,
                        glassEnabled = glassEnabled,
                        accounts = accounts.value,
                        onPrevMonth = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() },
                        onToggleSearch = { viewModel.toggleSearch() },
                        onKeywordChange = { viewModel.setKeyword(it) },
                        onFilterClick = { filterSheetVisible.value = true },
                        onClearFilter = { viewModel.clearFilter() },
                        onItemClick = { tx ->
                            startActivity(AddTransactionActivity.editIntent(requireContext(), tx.id))
                        },
                        scrollReporter = reporter,
                        refreshing = refreshing.value,
                        onRefresh = { viewModel.refresh() },
                        // 筛选面板：开关 + 选项数据 + 三个回调。keyword 不在面板里编辑
                        // （它属于搜索框），应用筛选时原样带过去，与旧版一致。
                        showFilterSheet = filterSheetVisible.value,
                        filterPanel = FilterPanelData(
                            kinds = state.value.filter.kinds,
                            categoryId = state.value.filter.categoryId,
                            accountId = state.value.filter.accountId,
                            categoryOptions = categories.value.map { FilterOption(it.id, it.name) },
                            accountOptions = accounts.value.map { FilterOption(it.key, it.value) },
                        ),
                        onFilterDismiss = { filterSheetVisible.value = false },
                        onFilterApply = { kinds, categoryId, accountId ->
                            viewModel.setFilter(
                                LedgerFilter(
                                    kinds = kinds,
                                    categoryId = categoryId,
                                    accountId = accountId,
                                    keyword = state.value.filter.keyword,
                                ),
                            )
                            filterSheetVisible.value = false
                        },
                        onFilterReset = {
                            viewModel.clearFilter()
                            filterSheetVisible.value = false
                        },
                    )
                }
            }
        }

        // ★ v1.61.1：inset 监听绝不能挂在 ComposeView 自己身上。
        //
        //   Compose 建立组合时会往 ComposeView 上安装它**自己的**
        //   OnApplyWindowInsetsListener（WindowInsetsHolder 那套），把我们
        //   在 observeStatusBarHeight 里设置的那个顶掉 —— 监听从此不再触发，
        //   statusBarHeightState 恒为 0.dp：玻璃几何塌掉、LazyColumn 的
        //   contentPadding.top = 0，整个明细页内容压进状态栏（v1.61 首发回归，
        //   模拟器实测工具栏第一项 top 从 178px 掉到 42px，正好差一个状态栏高度）。
        //
        //   修法：套一层普通 FrameLayout 做根，监听挂它身上 —— inset 从这里
        //   原样下发给 ComposeView。1.59 及以前这个角色由 SwipeRefreshLayout
        //   充当（同样是「ComposeView 的父节点」），删掉它时把这个前提也删没了。
        return FrameLayout(context).apply {
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
        glassBarInsets = GlassBarInsets(view) { bottomInsetState.value = it }.also { it.start() }
        // 页面是 Compose 之后，ThemedActivity 那套 View 滚动探测在首页抓不到东西，
        // 顶栏强度与底栏显隐改由 LedgerScreen 的 onScrollPx 驱动。
        (activity as? MainActivity)?.onLedgerPageShown()
    }

    /**
     * 把状态栏高度接进 [statusBarHeightState]。
     *
     * 必须在 `onViewCreated`（视图已 attach）之后注册，且**返回原样 insets**：
     * 这一层只是"旁路读一个数"，不能消费 inset，否则下面 Compose 自己的
     * inset 处理会被掐断。
     */
    private fun observeStatusBarHeight(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
            ).top
            statusBarHeightState.value = Dp(top / resources.displayMetrics.density)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    override fun onDestroyView() {
        glassBarInsets?.stop()
        glassBarInsets = null
        scrollReporter = null
        super.onDestroyView()
    }
}
