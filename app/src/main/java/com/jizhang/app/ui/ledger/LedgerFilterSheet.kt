package com.jizhang.app.ui.ledger

import com.jizhang.app.ui.glass.ProvideWindowLiquidMaterial
import com.jizhang.app.ui.common.LiquidBottomSheet
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import com.jizhang.app.ui.glass.LiquidTextButton as TextButton
import com.jizhang.app.ui.common.DialogButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.R
import com.jizhang.app.data.db.entity.TxKind
import com.jizhang.app.ui.common.ATTR_ON_SURFACE
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.themeColor
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 明细页筛选面板 —— 纯 Compose（miuix）版（v1.55）。
 *
 * 原实现是 `res/layout/sheet_filter.xml` + 三个 Material `ChipGroup`，
 * 由 LedgerFragment 用 `SheetFilterBinding.inflate()` 建出来再塞进
 * MaterialAlertDialog（见该文件旧版的 showFilterSheet()）。那一版的问题：
 *
 *   · Chip 是命令式 new 出来的（`Chip(requireContext())`），选中态只能靠
 *     ChipGroup 的 checkedChipIds 事后回读，等于把「状态」放在 View 树里；
 *   · 每次弹窗都要重新 inflate 一遍全部分类/账户 chip，账户多了会明显卡；
 *   · Material 对话框与页面其它部分的 miuix 观感不一致。
 *
 * 状态全是 Compose 的，弹层用 [LiquidBottomSheet]，把手、标题和选项统一滚动。
 *
 * ★ 草稿态（draft*）：面板里的选择**不立即生效**，点「应用」才交回 ViewModel。
 *   用 `remember(show)` 让每次打开都从当前筛选重新播种 —— 关掉未应用就丢弃。
 *   注意 key 只能是 `show`，不能带 data：data 每次重组都可能是新实例，
 *   带上它会导致每帧重置草稿，chip 点不动。
 */

/** 面板里一组的选项（id + 展示名）。 */
internal data class FilterOption(val id: Long, val label: String)

/**
 * 筛选面板的全部输入（Fragment 侧组装，Compose 只读）。
 *
 * [kinds] / [categoryId] / [accountId] 是**当前已生效**的筛选，用作草稿初值。
 */
internal data class FilterPanelData(
    val kinds: Set<TxKind>,
    val categoryId: Long?,
    val accountId: Long?,
    val categoryOptions: List<FilterOption>,
    val accountOptions: List<FilterOption>,
)

/** 类型分组的固定顺序，与旧版 `listOf(EXPENSE, INCOME, TRANSFER)` 一致。 */
private val KIND_ORDER = listOf(TxKind.EXPENSE, TxKind.INCOME, TxKind.TRANSFER)

@Composable
internal fun LedgerFilterSheet(
    show: Boolean,
    data: FilterPanelData,
    onDismiss: () -> Unit,
    onApply: (kinds: Set<TxKind>, categoryId: Long?, accountId: Long?) -> Unit,
    onReset: () -> Unit,
) {
    var retained by remember { mutableStateOf(false) }
    SideEffect { if (show) retained = true }
    var draftKinds by remember(show) { mutableStateOf(data.kinds) }
    var draftCategoryId by remember(show) { mutableStateOf(data.categoryId) }
    var draftAccountId by remember(show) { mutableStateOf(data.accountId) }

    if (!show && !retained) return
    ProvideWindowLiquidMaterial(blurVisible = show) {
        LiquidBottomSheet(
            show = show,
            onDismissRequest = onDismiss,
            onDismissFinished = { retained = false },
        ) {
            Text(
                text = stringResource(R.string.ledger_filter_title),
                style = MiuixTheme.textStyles.title3.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 19.sp,
                ),
                color = themeColor(ATTR_ON_SURFACE),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // ---- 类型：多选 ----
            FilterSectionTitle(stringResource(R.string.ledger_filter_kind))
            ChipFlow(
                labels = KIND_ORDER.map { it.label },
                selectedIndexes = KIND_ORDER.indices
                    .filter { KIND_ORDER[it] in draftKinds }
                    .toSet(),
                onToggle = { index ->
                    val kind = KIND_ORDER[index]
                    draftKinds = if (kind in draftKinds) draftKinds - kind else draftKinds + kind
                },
            )

            // ---- 分类：单选 ----
            FilterSectionTitle(stringResource(R.string.ledger_filter_category))
            ChipFlow(
                labels = data.categoryOptions.map { it.label },
                selectedIndexes = data.categoryOptions
                    .indexOfFirst { it.id == draftCategoryId }
                    .takeIf { it >= 0 }
                    ?.let { setOf(it) }
                    .orEmpty(),
                onToggle = { index ->
                    val id = data.categoryOptions[index].id
                    draftCategoryId = if (draftCategoryId == id) null else id
                },
            )

            // ---- 账户：单选 ----
            FilterSectionTitle(stringResource(R.string.ledger_filter_account))
            ChipFlow(
                labels = data.accountOptions.map { it.label },
                selectedIndexes = data.accountOptions
                    .indexOfFirst { it.id == draftAccountId }
                    .takeIf { it >= 0 }
                    ?.let { setOf(it) }
                    .orEmpty(),
                onToggle = { index ->
                    val id = data.accountOptions[index].id
                    draftAccountId = if (draftAccountId == id) null else id
                },
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DialogButton(
                    label = stringResource(R.string.action_reset),
                    primary = false,
                    onClick = {
                        draftKinds = emptySet()
                        draftCategoryId = null
                        draftAccountId = null
                        onReset()
                    },
                    modifier = Modifier.weight(1f),
                )
                DialogButton(
                    label = stringResource(R.string.action_apply),
                    primary = true,
                    onClick = { onApply(draftKinds, draftCategoryId, draftAccountId) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FilterSectionTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
        color = themeColor(ATTR_ON_SURFACE_VARIANT),
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/** 一组可换行的 miuix 选择按钮。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(
    labels: List<String>,
    selectedIndexes: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    if (labels.isEmpty()) {
        // 与旧版一致：没有任何可选值时这一组是空的（ChipGroup 没加任何 Chip）。
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                label = label,
                selected = index in selectedIndexes,
                onClick = { onToggle(index) },
            )
        }
    }
}

/**
 * 筛选胶囊。
 *
 * 选中 / 未选中用**明确的两种底色**表达，而不是给一个底色加 `alpha` ——
 * 后者会在部分 GL 路径下把背景层整层丢掉（v1.54.1 保存键的血案，
 * 详见 AddTransactionScreen 里 PrimaryButton 的注释）。
 */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        text = label, onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
        cornerRadius = 12.dp,
        minHeight = 40.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
        textStyle = MiuixTheme.textStyles.body2,
        colors = if (selected) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
    )
}

