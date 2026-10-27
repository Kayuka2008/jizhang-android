package com.jizhang.app.ui.pending

import com.jizhang.app.ui.glass.liquidSurface

import com.jizhang.app.ui.glass.LiquidPage
import com.jizhang.app.ui.glass.LocalLiquidBackdrop
import com.jizhang.app.ui.glass.LocalLiquidScene
import com.jizhang.app.ui.glass.StatusBarGlassScrim
import com.jizhang.app.ui.common.PageHeading
import com.jizhang.app.ui.common.PageHeadingStart

import com.jizhang.app.ui.common.scrollBoundaryHaptics
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import com.jizhang.app.ui.glass.LiquidTextButton as TextButton
import com.jizhang.app.ui.glass.LiquidButton as Button
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.R
import com.jizhang.app.data.db.entity.Confidence
import com.jizhang.app.data.db.entity.PendingItem
import com.jizhang.app.data.db.entity.TxKind
import com.jizhang.app.money.Money
import com.jizhang.app.ui.common.ATTR_ON_SURFACE
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.PageScrollReporter
import com.jizhang.app.ui.common.ReportListScrollOffset
import com.jizhang.app.ui.common.expenseColor
import com.jizhang.app.ui.common.incomeColor
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.ui.common.themeDp
import com.jizhang.app.ui.common.transferColor
import com.jizhang.app.ui.settings.JizhangMiuixTheme
import com.jizhang.app.util.Dates
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 待确认队列 —— 纯 Compose（miuix）版（v1.51）。
 *
 * 以前是 Fragment + RecyclerView + item_pending.xml + PendingAdapter。
 * 改成 Compose 之后：
 *   · 行不再需要 DiffUtil / ViewHolder —— `items(rows, key = { it.item.id })` 就够了；
 *   · busy / bulkBusy 这类"某一行的禁用态"直接进组合（原来是 notifyDataSetChanged 全量刷）；
 *   · 页面视觉与明细页共用同一套 `jizhang*` 主题 token（见 ui/common/ComposeTokens.kt）。
 *
 * 理由：弹窗内容是 Activity 级别的命令式弹窗，且设置页目前也还在用同一套，
 * 单独给这一页换一套弹窗只会让应用出现两套弹窗风格。
 */
@Composable
internal fun PendingScreen(
    rows: List<PendingRow>,
    busyIds: Set<Long>,
    bulkBusy: Boolean,
    statusBarHeight: Dp,
    bottomInset: Dp,
    onConfirmAll: () -> Unit,
    onOpenNotifSettings: () -> Unit,
    onConfirm: (PendingItem) -> Unit,
    onIgnore: (PendingItem) -> Unit,
    onPickCategory: (PendingItem) -> Unit,
    onPickAccount: (PendingItem) -> Unit,
    onPickToAccount: (PendingItem) -> Unit,
    /** v1.64：累计滚动偏移上报（驱动悬浮底栏收起/恢复），见 [PageScrollReporter]。 */
    scrollReporter: PageScrollReporter? = null,
) {
    JizhangMiuixTheme {
        val pendingListState = rememberLazyListState()
        // v1.64：把滚动位置喂给 Activity（驱动底栏收起/恢复）
        ReportListScrollOffset(pendingListState, scrollReporter)
        val scene = LocalLiquidScene.current
        val density = LocalDensity.current
        var headerHeightPx by remember { mutableIntStateOf(0) }
        var titleHeightPx by remember { mutableIntStateOf(0) }
        val titleHeight = with(density) { if (titleHeightPx > 0) titleHeightPx.toDp() else 40.sp.toDp() }
        val headerHeight = with(density) {
            if (headerHeightPx > 0) headerHeightPx.toDp() else 52.dp + maxOf(titleHeight, ButtonDefaults.MinHeight)
        }
        val topInset = statusBarHeight + headerHeight
        val blurUntil = statusBarHeight + 32.dp + titleHeight / 2
        Box(Modifier.fillMaxSize()) {
            // The list records once across the whole viewport; the title stays out
            // of its own sampled source, just like the secondary-page overlays.
            LiquidPage(recordContent = true) {
                if (rows.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize().padding(top = topInset),
                        bottomInset = bottomInset,
                        onOpenNotifSettings = onOpenNotifSettings,
                    )
                } else {
                    val listHorizontal = themeDp(R.attr.jizhangListHorizontalPadding)
                    LazyColumn(
                        state = pendingListState,
                        modifier = Modifier
                            .scrollBoundaryHaptics(pendingListState)
                            .fillMaxSize(),
                        // PaddingValues 的参数组合是固定的：(start, top, end, bottom)
                        // 或 (horizontal, vertical) —— horizontal 不能单独和 top/bottom 混用。
                        contentPadding = PaddingValues(
                            start = listHorizontal,
                            top = topInset + 4.dp,
                            end = listHorizontal,
                            bottom = bottomInset + themeDp(R.attr.jizhangListBottomPadding),
                        ),
                    ) {
                        items(rows, key = { it.item.id }) { row ->
                            PendingCard(
                                row = row,
                                busy = bulkBusy || row.item.id in busyIds,
                                fading = row.item.id in busyIds,
                                onConfirm = { onConfirm(row.item) },
                                onIgnore = { onIgnore(row.item) },
                                onPickCategory = { onPickCategory(row.item) },
                                onPickAccount = { onPickAccount(row.item) },
                                onPickToAccount = { onPickToAccount(row.item) },
                            )
                        }
                    }
                }
            }
            if (scene != null) {
                StatusBarGlassScrim(
                    backdrop = scene.contentBackdrop,
                    statusBarHeight = blurUntil,
                    fadeHeight = (topInset - blurUntil).coerceAtLeast(1.dp),
                    maxBlur = 10.dp,
                )
            }
            CompositionLocalProvider(LocalLiquidBackdrop provides scene?.contentBackdrop) {
                TitleRow(
                    showConfirmAll = rows.isNotEmpty(),
                    bulkBusy = bulkBusy,
                    onConfirmAll = onConfirmAll,
                    modifier = Modifier.padding(top = statusBarHeight)
                        .onSizeChanged { headerHeightPx = it.height },
                    onTitleHeight = { titleHeightPx = it },
                )
            }
        }
    }
}

/** 与设置页共用标题的水平留白、顶部留白和字号，标题独立于列表滚动。 */
@Composable
private fun TitleRow(
    showConfirmAll: Boolean,
    bulkBusy: Boolean,
    onConfirmAll: () -> Unit,
    modifier: Modifier,
    onTitleHeight: (Int) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PageHeadingStart, end = 16.dp)
            .padding(top = 32.dp, bottom = 20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PageHeading(
            text = stringResource(R.string.pending_title),
            modifier = Modifier.weight(1f).onSizeChanged { onTitleHeight(it.height) },
        )
        if (showConfirmAll) {
            TextButton(
                text = stringResource(R.string.action_confirm_all), onClick = onConfirmAll, enabled = !bulkBusy,
                colors = ButtonDefaults.textButtonColors(color = Color.Transparent, textColor = MiuixTheme.colorScheme.primary),
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PendingCard(
    row: PendingRow,
    busy: Boolean,
    fading: Boolean,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit,
    onPickCategory: () -> Unit,
    onPickAccount: () -> Unit,
    onPickToAccount: () -> Unit,
) {
    val item = row.item
    val corner = themeDp(R.attr.jizhangListItemCornerRadius)
    val shape = remember(corner) { RoundedCornerShape(corner) }
    val rowBusyAlpha = if (fading) 0.55f else 1f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = themeDp(R.attr.jizhangListItemMarginHorizontal),
                vertical = themeDp(R.attr.jizhangListItemMarginVertical),
            )
            .clip(shape)
            .liquidSurface(shape, themeColor(R.attr.jizhangListItemBackground))
            .padding(
                horizontal = themeDp(R.attr.jizhangListRowPadding),
                vertical = themeDp(R.attr.jizhangListItemVerticalPadding),
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val amount = item.cents
            Text(
                text = when {
                    amount == null || amount <= 0 -> "未识别金额"
                    item.kind == TxKind.EXPENSE -> Money.formatSigned(amount, negative = true)
                    item.kind == TxKind.INCOME -> Money.formatSigned(amount, negative = false)
                    else -> Money.formatCents(amount)
                },
                style = MiuixTheme.textStyles.body2.copy(
                    fontSize = 24.sp,
                    color = when (item.kind) {
                        TxKind.EXPENSE -> expenseColor()
                        TxKind.INCOME -> incomeColor()
                        TxKind.TRANSFER -> transferColor()
                        else -> themeColor(ATTR_ON_SURFACE_VARIANT)
                    }.copy(alpha = rowBusyAlpha),
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            ConfidenceBadge(confidence = item.confidence, alpha = rowBusyAlpha)
        }

        Spacer(Modifier.height(8.dp))

        val isTransfer = item.kind == TxKind.TRANSFER
        if (!isTransfer) {
            Row {
                PickerChip(
                    text = row.categoryName ?: stringResource(R.string.add_pick_category),
                    enabled = !busy,
                    onClick = onPickCategory,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                PickerChip(
                    text = row.accountName ?: stringResource(R.string.add_pick_account),
                    enabled = !busy,
                    onClick = onPickAccount,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row {
                PickerChip(
                    text = row.accountName ?: stringResource(R.string.add_pick_account),
                    enabled = !busy,
                    onClick = onPickAccount,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                PickerChip(
                    text = row.toAccountName ?: stringResource(R.string.add_pick_to_account),
                    enabled = !busy,
                    onClick = onPickToAccount,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.pending_raw_label),
            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
            color = themeColor(ATTR_ON_SURFACE_VARIANT),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.rawTitle.orEmpty(),
            style = MiuixTheme.textStyles.body2.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = themeColor(ATTR_ON_SURFACE).copy(alpha = rowBusyAlpha),
            ),
            maxLines = 2,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = item.rawText.orEmpty(),
            style = MiuixTheme.textStyles.body2.copy(
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = themeColor(ATTR_ON_SURFACE_VARIANT).copy(alpha = rowBusyAlpha),
            ),
            maxLines = 3,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildString {
                append(item.matchedRule?.let { "规则：$it" } ?: "未匹配规则")
                append(" · ")
                append(Dates.fromEpochMillis(item.postedAt).format(DATE_TIME_FORMAT))
            },
            style = MiuixTheme.textStyles.body2.copy(
                fontSize = 12.sp,
                color = themeColor(ATTR_ON_SURFACE_VARIANT).copy(alpha = rowBusyAlpha),
            ),
            maxLines = 1,
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = stringResource(R.string.action_ignore), onClick = onIgnore, enabled = !busy,
                colors = ButtonDefaults.textButtonColors(color = Color.Transparent),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextButton(
                text = stringResource(R.string.action_confirm), onClick = onConfirm,
                enabled = !busy && item.cents != null && item.cents > 0,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                insideMargin = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

/** 置信度角标。颜色沿用原 View 实现：高=绿、中=蓝、低=红。 */
@Composable
private fun ConfidenceBadge(confidence: Confidence, alpha: Float) {
    val color = when (confidence) {
        Confidence.HIGH -> incomeColor()
        Confidence.MEDIUM -> transferColor()
        Confidence.LOW -> expenseColor()
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f * alpha))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.pending_confidence, confidence.label),
            style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp, color = color.copy(alpha = alpha)),
            maxLines = 1,
        )
    }
}

/** 分类 / 账户选择器。视觉上替代原来的 OutlinedButton。 */
@Composable
private fun PickerChip(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier, minHeight = 40.dp, minWidth = 0.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Text(text = text, style = MiuixTheme.textStyles.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    bottomInset: Dp,
    onOpenNotifSettings: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .padding(bottom = bottomInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "\uD83D\uDD14", style = MiuixTheme.textStyles.body2.copy(fontSize = 48.sp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.pending_empty),
            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
            color = themeColor(ATTR_ON_SURFACE),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.pending_empty_hint),
            style = MiuixTheme.textStyles.body2,
            color = themeColor(ATTR_ON_SURFACE_VARIANT),
        )
        Spacer(Modifier.height(16.dp))
        TextButton(
            text = stringResource(R.string.onboarding_grant_now), onClick = onOpenNotifSettings,
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

private val DATE_TIME_FORMAT = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
