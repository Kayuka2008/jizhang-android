package com.jizhang.app.ui.account

import androidx.compose.foundation.lazy.rememberLazyListState
import com.jizhang.app.ui.common.scrollBoundaryHaptics
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.R
import com.jizhang.app.ui.common.ATTR_ON_SURFACE
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.EmptyState
import com.jizhang.app.ui.common.SecondaryPage
import com.jizhang.app.ui.common.rememberAdaptiveInsets
import com.jizhang.app.ui.common.expenseColor
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.ui.common.themeDp
import com.jizhang.app.ui.glass.LiquidCard as Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 账户管理页 —— 纯 Compose（miuix）版（v1.53）。
 *
 * 原 activity_account_manage.xml + item_account_manage.xml + 内嵌 AccountAdapter
 * 全部删除。结构照旧：顶栏 → 净资产卡 → 「添加账户」按钮 → 账户列表。
 *
 * ★ 条目右侧两个图标按钮（编辑 / 删除）保留；内置账户的删除按钮压 0.35 alpha ——
 *   这是旧版行为（点了也会 snackbar 提示「内置账户不可删除」），照搬。
 */
@Composable
internal fun AccountManageScreen(
    netWorthText: String,
    netWorthNegative: Boolean,
    accounts: List<AccountRow>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (AccountRow) -> Unit,
    onDelete: (AccountRow) -> Unit,
) {
    val bottomInset = rememberAdaptiveInsets().bottom
    SecondaryPage(title = stringResource(R.string.account_title), onBack = onBack) { topInset ->
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(topInset))

            // 净资产卡
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = themeDp(R.attr.jizhangScreenPadding))
                    .padding(bottom = 8.dp),
                cornerRadius = themeDp(R.attr.jizhangListSectionCornerRadius),
                insideMargin = PaddingValues(all = themeDp(R.attr.jizhangCardContentPadding)),
                colors = CardDefaults.defaultColors(
                    color = themeColor(R.attr.jizhangCardBackgroundColor),
                    contentColor = themeColor(ATTR_ON_SURFACE),
                ),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.account_net_worth),
                        style = MiuixTheme.textStyles.body2,
                        color = themeColor(ATTR_ON_SURFACE_VARIANT),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = netWorthText,
                        style = MiuixTheme.textStyles.headline1.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = if (netWorthNegative) expenseColor() else themeColor(ATTR_ON_SURFACE),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 28.sp),
                    )
                }
            }

            // 「添加账户」按钮（原 TonalButton + ic_add）
            com.jizhang.app.ui.glass.LiquidButton(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                cornerRadius = 20.dp,
                minHeight = 48.dp,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSecondaryVariant),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.account_add),
                    style = MiuixTheme.textStyles.button,
                    color = MiuixTheme.colorScheme.onSecondaryVariant,
                )
            }

            if (accounts.isEmpty()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        emoji = "\uD83D\uDCB3",
                        title = stringResource(R.string.account_add),
                    )
                }
            } else {
                val boundaryListState = rememberLazyListState()
                LazyColumn(
                    state = boundaryListState,
                    modifier = Modifier.scrollBoundaryHaptics(boundaryListState)
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 4.dp,
                        bottom = themeDp(R.attr.jizhangListBottomPadding) + bottomInset,
                    ),
                ) {
                    items(accounts, key = { it.id }) { row ->
                        AccountCard(
                            row = row,
                            onEdit = { onEdit(row) },
                            onDelete = { onDelete(row) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(row: AccountRow, onEdit: () -> Unit, onDelete: () -> Unit) {
    val rowPadding = themeDp(R.attr.jizhangListRowPadding)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = themeDp(R.attr.jizhangListItemMarginHorizontal),
                vertical = themeDp(R.attr.jizhangListItemMarginVertical),
            ),
        cornerRadius = themeDp(R.attr.jizhangListItemCornerRadius),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = themeColor(R.attr.jizhangListItemBackground),
            contentColor = themeColor(ATTR_ON_SURFACE),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rowPadding, vertical = themeDp(R.attr.jizhangRowVerticalPadding)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.initial,
                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                color = themeColor(ATTR_ON_SURFACE_VARIANT),
                modifier = Modifier.widthIn(min = 28.dp),
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                    color = themeColor(ATTR_ON_SURFACE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = row.typeLabel,
                    style = MiuixTheme.textStyles.body2,
                    color = themeColor(ATTR_ON_SURFACE_VARIANT),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = row.balanceText,
                    modifier = Modifier.fillMaxWidth(),
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                    color = if (row.negative) expenseColor() else themeColor(ATTR_ON_SURFACE),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 17.sp),
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconAction(R.drawable.ic_edit, R.string.action_edit, enabled = true, onClick = onEdit)
                // 内置账户仍交由业务层解释不能删除的原因。
                IconAction(R.drawable.ic_delete, R.string.action_delete, enabled = !row.builtin, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun IconAction(res: Int, desc: Int, enabled: Boolean, onClick: () -> Unit) {
    // 保留原有业务校验：内置条目可点击，由业务层解释不能删除的原因。
    com.jizhang.app.ui.glass.LiquidIconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Image(painterResource(res), stringResource(desc), Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceContainerVariant.copy(alpha = if (enabled) 1f else .35f)))
    }
}

/** 账户列表的行数据，由 Activity 侧组装（含余额格式化）。 */
internal data class AccountRow(
    val id: Long,
    val name: String,
    val typeLabel: String,
    val balanceText: String,
    val negative: Boolean,
    val builtin: Boolean,
) {
    val initial: String get() = name.take(1)
}
