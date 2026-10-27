/*
 * 明细页（主页）—— 纯 Compose 版（v1.43）。
 *
 * ============================================================================
 * 为什么要把这一页改成 Compose
 * ============================================================================
 *
 * 顶栏玻璃要靠「采样页面内容」才能糊出东西。旧版页面是
 * Fragment + RecyclerView（Android View），玻璃只能在自己的绘制过程中
 * 把整棵 View 树再画一遍（`contentView.draw(canvas)`）—— 那是一次**脱离
 * 正常绘制流程的重绘**，抖动、时有时无都指向它。
 *
 * 改成 Compose 之后，页面内容本身是一个 Compose 节点，可以用
 * `rememberLayerBackdrop()` + `Modifier.layerBackdrop(...)` 让 Compose
 * 在**同一帧、同一次绘制遍历**里把内容录进 GraphicsLayer —— 也就是
 * SleepDown 的做法。玻璃只是把这一层拿来模糊，不再自己去重画任何东西。
 *
 * ============================================================================
 * 三条实现约束（改之前先读）
 * ============================================================================
 *
 * 1. **滚动偏移不能用 `LazyListState` 读**。
 *    `firstVisibleItemIndex` / `firstVisibleItemScrollOffset` 是快照状态，
 *    在页面级读它会让整页每帧重组（LazyColumn 跟着抖）。
 *    这里用 `Modifier.nestedScroll` 的 `onPostScroll` 累积 `consumed.y` ——
 *    真实像素、严格线性，而且**完全不进快照系统**（写进一个普通数组，
 *    再交给 Activity 侧驱动底栏显隐，那条链是 View 侧的命令式代码）。
 *
 * 2. **顶栏玻璃不读任何滚动态**（v1.45 起）。
 *    它显形靠的是「内容钻进状态栏底下」带来的纹理，不需要强度跟着滚动变；
 *    让任何量随滚动变化，就等于给「每帧重组 → 每帧重建 GPU 效果链」开门。
 *    详见 `StatusBarGlass.kt` 文件头的 v1.45 一节。
 *
 * 3. **玻璃必须画在内容之上**：它是 [LedgerScreen] 根 Box 的**最后一个**子节点。
 */
package com.jizhang.app.ui.ledger

import top.yukonga.miuix.kmp.basic.TextFieldDefaults

import com.jizhang.app.ui.glass.liquidSurface
import com.jizhang.app.ui.glass.LiquidSurfaceRole
import com.jizhang.app.ui.glass.LocalLiquidScene

import com.jizhang.app.ui.glass.LiquidPage

import com.jizhang.app.ui.common.scrollBoundaryHaptics
import top.yukonga.miuix.kmp.basic.CardDefaults
import com.jizhang.app.ui.glass.LiquidCard as Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import com.jizhang.app.ui.common.AnimateContentChanges
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.RefreshState
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.jizhang.app.R
import com.jizhang.app.data.prefs.GlassStyle
import com.jizhang.app.ui.common.ATTR_ON_SURFACE
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.ATTR_PRIMARY
import com.jizhang.app.ui.common.expenseColor
import com.jizhang.app.ui.common.incomeColor
import com.jizhang.app.ui.common.MonthNavigation
import com.jizhang.app.ui.common.FlipState
import com.jizhang.app.ui.common.flipContent
import com.jizhang.app.ui.common.PageScrollReporter
import com.jizhang.app.ui.common.ReportListScrollOffset
import com.jizhang.app.ui.common.rememberFlipState
import com.jizhang.app.ui.common.transferColor
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.ui.common.themeDp
import com.jizhang.app.data.db.entity.Transaction
import com.jizhang.app.data.db.entity.TxKind
import com.jizhang.app.data.db.entity.TxSource
import com.jizhang.app.money.Money
import com.jizhang.app.ui.common.CategoryIcons
import com.jizhang.app.ui.glass.StatusBarGlassScrim
import com.jizhang.app.util.Dates
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 文字/强调色用的是 Material 的语义属性（XML 里写的就是 `?attr/colorOnSurface`）。
 *
 * ★ 它们**不在本应用的 R.attr 里** —— 本应用只声明 `jizhang*` 前缀的那批，
 *   `colorOnSurface` / `colorPrimary` 这些是由 Material 库声明的，
 *   所以要引 Material 自己的 R 类。写成 `R.attr.colorOnSurface` 是**编译不过**的。
 */

/**
 * 日期头圆角栏的两个厚度参数（v1.48）。
 *
 * [DAY_HEADER_PADDING_V] 栏内竖向留白（对称）。栏高 ≈ 2×它 + 一行 14sp 的字 ≈ 39dp；
 *   改前是「上 28dp + 下 8dp + 字」≈ 55dp，明显偏厚 —— 那 28dp 是给通栏色带
 *   做上方留白用的，圆角化之后全落进栏里，就把栏撑成了厚色带。
 * [DAY_HEADER_GAP_ABOVE] 栏**外**上方留白，负责分组间距；它不进栏内，
 *   所以调它只改间距、不改厚度。
 */
private val DAY_HEADER_PADDING_V = 10.dp
private val DAY_HEADER_GAP_ABOVE = 14.dp

/**
 * 明细页。
 *
 * @param statusBarHeight 状态栏高度，由 Fragment 从 Activity 侧读出后传入
 *                        （不用 `WindowInsets.statusBars`：Fragment 里能否拿到取决于
 *                        inset 分发时机，而这个值错了玻璃的几何全错）。
 * @param scrollReporter  累计滚动像素的上报器（喂给 Activity 驱动底栏显隐）。
 *                        v1.64 起由 [PageScrollReporter] 统一承担，
 *                        统计 / 待确认 / 设置三页共用同一套。
 * @param refreshing     下拉刷新是否正在转圈（由 ViewModel 直通给 miuix PullToRefresh）。
 */
@Composable
internal fun LedgerScreen(
    state: LedgerUiState,
    statusBarHeight: Dp,
    bottomInset: Dp,
    /**
     * 顶部玻璃开关（安全模式用）。
     *
     * 关掉之后不挂 `layerBackdrop`、也不画顶栏玻璃 —— 页面照常显示，
     * 只是状态栏底下没有玻璃（内容直接滚过去）。
     * 这是为了排查「崩在玻璃采样还是崩在页面本身」：关了还崩就是后者。
     */
    glassEnabled: Boolean = true,
    accounts: Map<Long, String>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleSearch: () -> Unit,
    onKeywordChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onClearFilter: () -> Unit,
    onItemClick: (Transaction) -> Unit,
    scrollReporter: PageScrollReporter? = null,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    /**
     * 筛选面板（v1.55 起是 Compose）。
     *
     * [showFilterSheet] 由 Fragment 的 state 驱动；[filterPanel] 是面板要用的
     * 选项数据（分类 / 账户列表），Fragment 组装好传进来。
     * 面板本体在 [LedgerFilterSheet]，是一个独立 Window，不参与下面 Box 的布局。
     */
    showFilterSheet: Boolean = false,
    filterPanel: FilterPanelData? = null,
    onFilterDismiss: () -> Unit = {},
    onFilterApply: (kinds: Set<TxKind>, categoryId: Long?, accountId: Long?) -> Unit = { _, _, _ -> },
    onFilterReset: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val contentBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val refreshState = rememberPullToRefreshState()
    // v1.64：把滚动位置喂给 Activity（驱动底栏收起/恢复）
    ReportListScrollOffset(listState, scrollReporter)

    // 月份切换动画：方向给月份文字，进度给下方栏目（与统计页同一套）。
    val monthFlip = rememberFlipState()
    AnimateContentChanges(state.yearMonth, monthFlip)

    LiquidPage(modifier) {
        PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            pullToRefreshState = refreshState,
            contentPadding = PaddingValues(top = statusBarHeight),
            refreshTexts = listOf(
                stringResource(R.string.refresh_pull),
                stringResource(R.string.refresh_release),
                stringResource(R.string.refresh_loading),
                stringResource(R.string.refresh_complete),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            // 内容层：这一层被录进 contentBackdrop，玻璃读的就是它。
            LazyColumn(
                state = listState,
                // 顺序照官方 BackdropDemoScaffold：`layerBackdrop` 在最外层，
                // 保证它录到的是下面整棵子树（LazyColumn）画出来的东西。
                modifier = Modifier
                    .scrollBoundaryHaptics(listState)
                    .then(if (glassEnabled) Modifier.layerBackdrop(contentBackdrop) else Modifier)
                    .fillMaxSize()
                    // ★ 必须挂 miuix 自己的 overScrollVertical（v1.61）：
                    //   没有它，列表顶部的下拉会被 LazyColumn 内部的**平台 stretch
                    //   overscroll** 消费成橡皮筋（模拟器实测：拖动全程只有列表内容
                    //   在弹性形变、布局 bounds 不变、松手即回弹），miuix PullToRefresh
                    //   的 NestedScrollConnection 永远收不到剩余滚动，指示器不出现。
                    //   miuix 的 overscroll 通过 LocalPullToRefreshState 与
                    //   PullToRefresh 协调（shouldBypassForPullToRefresh：刷新态让路），
                    //   这是它源码里的标准组装方式。
                    .overScrollVertical(),
                contentPadding = PaddingValues(
                    top = statusBarHeight,
                    bottom = themeDp(R.attr.jizhangListBottomPadding) + bottomInset,
                ),
            ) {
                item(key = "toolbar") {
                    ToolbarCard(state, monthFlip, onPrevMonth, onNextMonth, onToggleSearch, onFilterClick)
                }
                if (state.searchVisible) {
                    item(key = "search") {
                        SearchField(
                            keyword = state.filter.keyword,
                            onKeywordChange = onKeywordChange,
                        )
                    }
                }
                if (state.filterLabel.isNotBlank()) {
                    item(key = "filterBanner") {
                        FilterBanner(label = state.filterLabel, onClear = onClearFilter)
                    }
                }
                item(key = "summary") {
                    Box(Modifier.flipContent(monthFlip)) { SummaryCard(state) }
                }
                if (!state.loading) {
                    item(key = "count") {
                        Box(Modifier.flipContent(monthFlip)) {
                            CountHeader(count = state.count)
                        }
                    }
                }
                if (state.rows.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        Box(Modifier.flipContent(monthFlip)) {
                            EmptyView(
                                filtered = state.filter.isActive || state.filter.keyword.isNotBlank(),
                            )
                        }
                    }
                }
                items(
                    items = state.rows,
                    key = { row ->
                        when (row) {
                            is LedgerRow.Header -> "h:${row.date}"
                            is LedgerRow.Item -> "i:${row.tx.id}"
                        }
                    },
                ) { row ->
                    Box(Modifier.flipContent(monthFlip)) {
                        when (row) {
                            is LedgerRow.Header -> DayHeader(row)
                            is LedgerRow.Item -> TransactionRow(row, accounts, onItemClick)
                        }
                    }
                }
            }

        }
        // Keep the opaque page background outside PullToRefresh: its header is
        // offset below the status bar and overlaps the first inset of content().
        // The stationary glass yields for the entire pull/refresh/completion cycle.
        // Reading state in the layer block leaves the cached blur chain untouched.
        StatusBarGlassScrim(
            backdrop = contentBackdrop,
            statusBarHeight = statusBarHeight,
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                alpha = if (refreshState.refreshState == RefreshState.Idle) 1f else 0f
            },
        )
    }

    // 筛选面板：独立 Window（有遮罩、自己处理 inset），因此**不在**上面那个 Box 里，
    // 也就不受「玻璃必须最后画」的约束。panel 为 null 时整块不成立 —— 只在没有
    // 选项数据的调用场景（测试/预览）才会发生。
    filterPanel?.let { panel ->
        LedgerFilterSheet(
            show = showFilterSheet,
            data = panel,
            onDismiss = onFilterDismiss,
            onApply = onFilterApply,
            onReset = onFilterReset,
        )
    }
}

// ============================================================================
// 各区块
// ============================================================================

@Composable
private fun ToolbarCard(
    state: LedgerUiState,
    flip: FlipState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleSearch: () -> Unit,
    onFilter: () -> Unit,
) {
    CardSurface(Modifier.padding(top = themeDp(R.attr.jizhangSectionSpacing))) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp)) {
            // Keep a readable month at large font scales instead of clipping its last character.
            val splitActions = maxWidth < 192.dp + 80.dp * LocalDensity.current.fontScale
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonthNavigation(
                        label = Dates.formatMonth(state.yearMonth),
                        flip = flip,
                        onPrev = onPrev,
                        onNext = onNext,
                        modifier = Modifier.weight(1f),
                    )
                    if (!splitActions) {
                        IconButton(onClick = onToggleSearch, res = R.drawable.ic_search, desc = R.string.action_search)
                        IconButton(onClick = onFilter, res = R.drawable.ic_filter, desc = R.string.action_filter)
                    }
                }
                if (splitActions) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        IconButton(onClick = onToggleSearch, res = R.drawable.ic_search, desc = R.string.action_search)
                        IconButton(onClick = onFilter, res = R.drawable.ic_filter, desc = R.string.action_filter)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(keyword: String, onKeywordChange: (String) -> Unit) {
    TextField(value = keyword, onValueChange = onKeywordChange,
        colors = TextFieldDefaults.textFieldColors(backgroundColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
            .liquidSurface(RoundedCornerShape(16.dp), role = LiquidSurfaceRole.Field),
        label = stringResource(R.string.ledger_search_hint), useLabelAsPlaceholder = true, singleLine = true,
        textStyle = MiuixTheme.textStyles.body1,
    )
}

@Composable
private fun FilterBanner(label: String, onClear: () -> Unit) {
    val clearGlass = LocalLiquidScene.current?.style == GlassStyle.CLEAR
    val tint = themeColor(R.attr.jizhangListSectionBackground)
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (clearGlass) Modifier.liquidSurface(RoundedCornerShape(8.dp), tint)
                else Modifier.background(tint),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ledger_filter_title) + "：" + label,
            style = MiuixTheme.textStyles.body2.copy(fontSize = 13.sp, color = themeColor(ATTR_ON_SURFACE_VARIANT)),
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.action_clear),
            style = MiuixTheme.textStyles.body2.copy(fontSize = 13.sp, color = themeColor(ATTR_PRIMARY)),
            modifier = Modifier
                .clickable(onClick = onClear)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SummaryCard(state: LedgerUiState) {
    CardSurface(Modifier.padding(top = themeDp(R.attr.jizhangSectionSpacing))) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            SumColumn(
                label = stringResource(R.string.ledger_month_expense),
                value = Money.formatCentsWithSymbol(state.expense),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            SumColumn(
                label = stringResource(R.string.ledger_month_income),
                value = Money.formatCentsWithSymbol(state.income),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            SumColumn(
                label = stringResource(R.string.ledger_month_balance),
                value = Money.formatCentsWithSymbol(state.balance),
                color = if (state.balance < 0) expenseColor() else incomeColor(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SumColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, color = themeColor(ATTR_ON_SURFACE_VARIANT)),
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2.copy(
                fontSize = 16.sp,
                color = if (color == Color.Unspecified) themeColor(ATTR_ON_SURFACE) else color,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 16.sp),
        )
    }
}

@Composable
private fun CountHeader(count: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            // v1.47：左缘对齐到卡片文字的起点（marginH + rowPadding），
            // 别再比下面的圆角栏多凸出一段。
            .padding(horizontal = themeDp(R.attr.jizhangListItemMarginHorizontal))
            .padding(horizontal = themeDp(R.attr.jizhangListRowPadding))
            .padding(top = 16.dp, bottom = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.ledger_count_format, count),
            style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, color = themeColor(ATTR_ON_SURFACE)),
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(themeColor(R.attr.jizhangListSectionDividerColor)),
        )
    }
}

@Composable
private fun DayHeader(row: LedgerRow.Header) {
    // v1.47：日期头从「通栏直角色带」改成与条目卡同几何的圆角栏。
    //
    // 原来是 fillMaxWidth + 通栏背景，左右两端是两个 90° 直角，
    // 夹在上面的汇总卡和下面的条目卡（都是圆角）中间非常生硬。
    //
    // 几何全部走条目卡的 token（marginH / itemCornerRadius / 描边）：
    //   · HyperOS / MD3 下自动是圆角栏，且左右缩进、圆角、描边都和下面的
    //     条目卡完全一致 —— 连文字起点都对齐（marginH + rowPadding）；
    //   · 经典风格这两个 token 本来就是 0dp/0dp，自动回到原来的通栏直角，
    //     那是经典风格自己的语言，不受影响。
    // 背景仍用 sectionBackground（与条目卡同色），改的只是形状不是颜色。
    val marginH = themeDp(R.attr.jizhangListItemMarginHorizontal)
    val corner = themeDp(R.attr.jizhangListItemCornerRadius)
    val clearGlass = LocalLiquidScene.current?.style == GlassStyle.CLEAR
    val tint = themeColor(R.attr.jizhangListSectionBackground)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = marginH)
            // v1.48：把「上方留白」挪到栏**外**。
            // 原来栏内是 sectionHeaderPaddingTop/Bottom（HyperOS 下 28dp/8dp），
            // 那是给通栏色带设计的 —— 那时上方留白必须压在色带里面。
            // 圆角化之后 28dp 全落进栏里，栏就被撑成一条厚色带。
            // 现在：栏外留白 14dp 负责分组间距，栏内对称 10dp 只管文字呼吸。
            .padding(top = DAY_HEADER_GAP_ABOVE)
            .clip(RoundedCornerShape(corner))
            .then(
                if (clearGlass) Modifier.liquidSurface(RoundedCornerShape(corner), tint)
                else Modifier.background(tint),
            )
            .then(
                if (themeDp(R.attr.jizhangListItemStrokeWidth).value > 0f) {
                    Modifier.border(
                        themeDp(R.attr.jizhangListItemStrokeWidth),
                        themeColor(R.attr.jizhangListItemStrokeColor),
                        RoundedCornerShape(corner),
                    )
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = themeDp(R.attr.jizhangListRowPadding),
                vertical = DAY_HEADER_PADDING_V,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.title,
            style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, color = themeColor(ATTR_ON_SURFACE)),
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        val parts = mutableListOf<String>()
        if (row.expense > 0) parts += "支出 ${Money.formatCents(row.expense)}"
        if (row.income > 0) parts += "收入 ${Money.formatCents(row.income)}"
        Text(
            text = parts.joinToString("  "),
            style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, color = themeColor(ATTR_ON_SURFACE_VARIANT)),
            maxLines = 1,
        )
    }
}

@Composable
private fun TransactionRow(
    row: LedgerRow.Item,
    accounts: Map<Long, String>,
    onClick: (Transaction) -> Unit,
) {
    val tx = row.tx
    val category = row.category
    val marginH = themeDp(R.attr.jizhangListItemMarginHorizontal)
    val marginV = themeDp(R.attr.jizhangListItemMarginVertical)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = marginH, vertical = marginV)
            .clip(RoundedCornerShape(themeDp(R.attr.jizhangListItemCornerRadius)))
            .liquidSurface(RoundedCornerShape(themeDp(R.attr.jizhangListItemCornerRadius)), themeColor(R.attr.jizhangListItemBackground))
            .then(
                if (themeDp(R.attr.jizhangListItemStrokeWidth).value > 0f) {
                    Modifier.border(
                        themeDp(R.attr.jizhangListItemStrokeWidth),
                        themeColor(R.attr.jizhangListItemStrokeColor),
                        RoundedCornerShape(themeDp(R.attr.jizhangListItemCornerRadius)),
                    )
                } else {
                    Modifier
                },
            )
            .clickable { onClick(tx) }
            .padding(
                horizontal = themeDp(R.attr.jizhangListRowPadding),
                vertical = themeDp(R.attr.jizhangListItemVerticalPadding),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconRes = when (tx.kind) {
            TxKind.TRANSFER -> R.drawable.ic_category_transfer
            TxKind.NEUTRAL -> R.drawable.ic_category_default
            else -> CategoryIcons.of(category?.name, category?.emoji)
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(themeColor(R.attr.jizhangListSectionBackground)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(themeColor(ATTR_ON_SURFACE_VARIANT)),
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = rowTitle(tx, category, accounts),
            style = MiuixTheme.textStyles.body2.copy(fontSize = 15.sp, color = themeColor(ATTR_ON_SURFACE)),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = when (tx.kind) {
                TxKind.TRANSFER, TxKind.NEUTRAL -> Money.formatCents(tx.cents)
                else -> Money.formatSigned(tx.cents, tx.kind == TxKind.EXPENSE)
            },
            style = MiuixTheme.textStyles.body2.copy(
                fontSize = 17.sp,
                color = when (tx.kind) {
                    TxKind.EXPENSE -> expenseColor()
                    TxKind.INCOME -> incomeColor()
                    TxKind.TRANSFER -> transferColor()
                    TxKind.NEUTRAL -> themeColor(ATTR_ON_SURFACE_VARIANT)
                },
                textAlign = TextAlign.End,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyView(filtered: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "\uD83D\uDCD6", style = MiuixTheme.textStyles.body2.copy(fontSize = 48.sp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(if (filtered) R.string.ledger_no_result else R.string.ledger_empty),
            style = MiuixTheme.textStyles.body2.copy(
                fontSize = 15.sp,
                color = themeColor(ATTR_ON_SURFACE),
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (filtered) R.string.ledger_no_result_hint else R.string.ledger_empty_hint,
            ),
            style = MiuixTheme.textStyles.body2.copy(
                fontSize = 13.sp,
                color = themeColor(ATTR_ON_SURFACE_VARIANT),
                textAlign = TextAlign.Center,
            ),
        )
    }
}

// ============================================================================
// 小工具
// ============================================================================

@Composable
private fun CardSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.padding(horizontal = themeDp(R.attr.jizhangScreenPadding)),
        cornerRadius = themeDp(R.attr.jizhangListSectionCornerRadius), insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = themeColor(R.attr.jizhangListSectionBackground))) { content() }
}

@Composable
private fun IconButton(onClick: () -> Unit, res: Int, desc: Int) {
    com.jizhang.app.ui.glass.LiquidIconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Image(painterResource(res), stringResource(desc), Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceContainerVariant))
    }
}


/** 一行的标题：交易对象 / 分类 / 商品 / 账户 / 时间 / 来源。 */
private fun rowTitle(
    tx: Transaction,
    category: com.jizhang.app.data.db.entity.Category?,
    accounts: Map<Long, String>,
): String {
    val title = tx.counterparty?.takeIf { it.isNotBlank() }
        ?: category?.name
        ?: tx.item?.takeIf { it.isNotBlank() }
        ?: tx.kind.label
    val details = mutableListOf<String>()
    if (tx.counterparty?.isNotBlank() == true) {
        category?.name?.let { details += it }
        tx.item?.takeIf { it.isNotBlank() }?.let { details += it }
    } else {
        tx.item?.takeIf { it.isNotBlank() }?.let { details += it }
    }
    if (tx.kind == TxKind.TRANSFER) {
        val from = tx.accountId?.let { accounts[it] } ?: "?"
        val to = tx.toAccountId?.let { accounts[it] } ?: "?"
        details += "$from → $to"
    } else {
        tx.accountId?.let { accounts[it] }?.let { details += it }
    }
    tx.note?.takeIf { it.isNotBlank() }?.let { details += it }
    details += Dates.formatTime(tx.occurredAt)
    if (tx.source != TxSource.MANUAL) details += tx.source.label
    return (listOf(title) + details).joinToString(" · ")
}

