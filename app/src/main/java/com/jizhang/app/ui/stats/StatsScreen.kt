package com.jizhang.app.ui.stats

import com.jizhang.app.ui.glass.LiquidPage

import com.jizhang.app.ui.common.verticalScrollWithBoundaryHaptics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import com.jizhang.app.ui.common.AnimateContentChanges
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.R
import com.jizhang.app.ui.common.ATTR_ON_SURFACE
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.expenseColor
import com.jizhang.app.ui.common.incomeColor
import com.jizhang.app.ui.common.MonthNavigation
import com.jizhang.app.ui.common.FlipState
import com.jizhang.app.ui.common.flipContent
import com.jizhang.app.ui.common.rememberFlipState
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.ui.common.PageScrollReporter
import com.jizhang.app.ui.common.ReportScrollOffset
import com.jizhang.app.ui.common.themeDp
import com.jizhang.app.ui.settings.JizhangMiuixTheme
import com.jizhang.app.widget.BarPoint
import com.jizhang.app.widget.DonutSlice
import com.jizhang.app.ui.glass.LiquidCard as Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 统计页：miuix 卡片、主题文本和可交互 Compose 图表。 */
@Composable
internal fun StatsScreen(
    ui: StatsUi,
    statusBarHeight: Dp,
    bottomInset: Dp,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleBreakdown: () -> Unit,
    /** v1.64：累计滚动偏移上报（驱动悬浮底栏收起/恢复），见 [PageScrollReporter]。 */
    scrollReporter: PageScrollReporter? = null,
) {
    JizhangMiuixTheme {
        val scrollState = rememberScrollState()
        // v1.64：把绝对滚动位置喂给 Activity（驱动底栏收起/恢复）
        ReportScrollOffset(scrollState, scrollReporter)
        val screenPadding = themeDp(R.attr.jizhangScreenPadding)
        val sectionSpacing = themeDp(R.attr.jizhangSectionSpacing)
        LiquidPage {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollWithBoundaryHaptics(scrollState)
                    .padding(
                        start = screenPadding,
                        // 原 XML 是 ScrollView 的 paddingTop = 64dp（48dp 状态栏 + 16dp 留白）；
                        // 这里用真实状态栏高度算，矮刘海屏也能正确避让。
                        top = statusBarHeight + 16.dp,
                        end = screenPadding,
                    )
                    .padding(bottom = bottomInset + themeDp(R.attr.jizhangListBottomPadding)),
            ) {
                // 月份切换动画：方向给月份文字，进度给下方卡片（与明细页同一套）。
                val monthFlip = rememberFlipState()
                AnimateContentChanges(ui.monthLabel, monthFlip)

                MonthSwitcher(
                    label = ui.monthLabel,
                    canGoNext = ui.canGoNext,
                    flip = monthFlip,
                    onPrev = onPrevMonth,
                    onNext = onNextMonth,
                )

                Spacer(Modifier.height(sectionSpacing))

                // 月份下方的卡片整体跟着进场 —— 包一层 Box 而不是逐个加：
                // 它们本来就该一起动，也少几份 graphicsLayer。
                Box(Modifier.flipContent(monthFlip)) {
                    Column {
                        SummaryCard(ui)

                        Spacer(Modifier.height(sectionSpacing))

                        if (ui.hasData) {
                            CategoryCard(ui = ui, onToggle = onToggleBreakdown)
                            Spacer(Modifier.height(sectionSpacing))
                            TrendCard(ui = ui)
                        } else {
                            EmptyState()
                        }
                    }
                }
            }
        }
    }
}

/** Uses the same miuix month controls and spacing as the ledger toolbar. */
@Composable
private fun MonthSwitcher(
    label: String,
    canGoNext: Boolean,
    flip: FlipState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        insideMargin = PaddingValues(8.dp),
        colors = CardDefaults.defaultColors(
            color = themeColor(R.attr.jizhangCardBackgroundColor),
            contentColor = themeColor(ATTR_ON_SURFACE),
        ),
    ) {
        MonthNavigation(label, flip, onPrev, onNext, Modifier.fillMaxWidth(), canGoNext)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(ui: StatsUi) {
    CardSurface {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                Metric(
                    label = stringResource(R.string.stats_expense),
                    value = ui.expenseText,
                    valueSize = 24.sp,
                    valueColor = expenseColor(),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Metric(
                    label = stringResource(R.string.stats_income),
                    value = ui.incomeText,
                    valueSize = 24.sp,
                    valueColor = incomeColor(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(themeColor(R.attr.jizhangListSectionDividerColor)),
            )
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < (56.dp * LocalDensity.current.fontScale + 8.dp) * 3) 2 else 3
                FlowRow(
                    Modifier.fillMaxWidth(),
                    maxItemsInEachRow = columns,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Metric(
                        label = stringResource(R.string.stats_balance),
                        value = ui.balanceText,
                        valueSize = 17.sp,
                        valueColor = if (ui.balanceNegative) expenseColor() else incomeColor(),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Metric(
                        label = stringResource(R.string.stats_avg_per_day),
                        value = ui.avgText,
                        valueSize = 17.sp,
                        valueColor = themeColor(ATTR_ON_SURFACE),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Metric(
                        label = stringResource(R.string.stats_count),
                        value = ui.countText,
                        valueSize = 17.sp,
                        valueColor = themeColor(ATTR_ON_SURFACE),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    valueSize: androidx.compose.ui.unit.TextUnit,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = themeColor(ATTR_ON_SURFACE_VARIANT),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.headline1.copy(fontSize = valueSize, fontWeight = FontWeight.Medium),
            color = valueColor,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = valueSize),
        )
    }
}

@Composable
private fun CategoryCard(ui: StatsUi, onToggle: () -> Unit) {
    CardSurface {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    if (ui.byIncome) R.string.stats_by_category_income else R.string.stats_by_category,
                ),
                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
                color = themeColor(ATTR_ON_SURFACE),
                modifier = Modifier.clickable { onToggle() },
            )
            Spacer(Modifier.height(12.dp))
            MiuixDonutChart(
                slices = ui.donutSlices,
                title = ui.donutCenter,
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            ui.ranking.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(row.colorArgb)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = row.name,
                        style = MiuixTheme.textStyles.body2,
                        color = themeColor(ATTR_ON_SURFACE),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = row.percentText,
                        style = MiuixTheme.textStyles.body2,
                        color = themeColor(ATTR_ON_SURFACE_VARIANT),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = row.amountText,
                        style = MiuixTheme.textStyles.body2,
                        color = themeColor(ATTR_ON_SURFACE),
                        modifier = Modifier.weight(.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendCard(ui: StatsUi) {
    CardSurface {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.stats_trend),
                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
                color = themeColor(ATTR_ON_SURFACE),
            )
            Spacer(Modifier.height(12.dp))
            MiuixTrendChart(points = ui.trendPoints, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDot(color = expenseColor())
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.stats_expense),
                    style = MiuixTheme.textStyles.body2,
                    color = themeColor(ATTR_ON_SURFACE_VARIANT),
                )
                Spacer(Modifier.width(16.dp))
                LegendDot(color = incomeColor())
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.stats_income),
                    style = MiuixTheme.textStyles.body2,
                    color = themeColor(ATTR_ON_SURFACE_VARIANT),
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color),
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "\uD83D\uDCCA", style = MiuixTheme.textStyles.title1.copy(fontSize = 44.sp))
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.stats_empty),
            style = MiuixTheme.textStyles.body2,
            color = themeColor(ATTR_ON_SURFACE_VARIANT),
        )
    }
}

/**
 * 与明细页 / 待确认页同一套卡片几何：屏幕边距 + 段落圆角 + 最亮表面色。
 *
 * v1.51.1 起用 miuix 的 [Card] 画（squircle 圆角，HyperOS 观感更正），
 * 颜色/圆角/内边距仍显式传应用 token，不依赖 miuix 默认值。
 */
@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = themeDp(R.attr.jizhangListSectionCornerRadius),
        insideMargin = PaddingValues(all = themeDp(R.attr.jizhangCardContentPadding)),
        colors = CardDefaults.defaultColors(
            color = themeColor(R.attr.jizhangCardBackgroundColor),
            contentColor = themeColor(ATTR_ON_SURFACE),
        ),
    ) {
        content()
    }
}

/** 统计页渲染所需的全部数据，由 Fragment 侧算好（DB 查询 + 格式化都在那里）。 */
internal data class StatsUi(
    val monthLabel: String,
    val canGoNext: Boolean,
    val expenseText: String,
    val incomeText: String,
    val balanceText: String,
    val balanceNegative: Boolean,
    val avgText: String,
    val countText: String,
    val hasData: Boolean,
    val byIncome: Boolean,
    val donutSlices: List<DonutSlice>,
    val donutCenter: String,
    val ranking: List<RankRow>,
    val trendPoints: List<BarPoint>,
)

internal data class RankRow(
    val colorArgb: Int,
    val name: String,
    val percentText: String,
    val amountText: String,
)
