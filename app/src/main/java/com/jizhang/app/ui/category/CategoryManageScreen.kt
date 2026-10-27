package com.jizhang.app.ui.category

import androidx.compose.foundation.lazy.rememberLazyListState
import com.jizhang.app.ui.common.scrollBoundaryHaptics
import com.jizhang.app.ui.glass.LiquidButton as Button
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jizhang.app.R
import com.jizhang.app.ui.common.ATTR_ON_SURFACE
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.EmptyState
import com.jizhang.app.ui.common.SecondaryPage
import com.jizhang.app.ui.common.rememberAdaptiveInsets
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.ui.common.themeDp
import com.jizhang.app.ui.glass.LiquidCard as Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 分类管理页 —— 纯 Compose（miuix）版（v1.53）。
 *
 * 原 activity_category_manage.xml + item_category_manage.xml + 内嵌 CategoryAdapter
 * 全部删除。结构照旧：顶栏 → 两个「新建分类」按钮（支出 / 收入）→ 平铺列表。
 *
 * ★ 旧版列表是**平铺**的（不分节），两个按钮的区别只在新建时传的 TxKind 不同 ——
 *   截图与布局都确认过，不额外造分组。按钮文案相同（都为「新建分类」），
 *   这是旧版行为（靠左右位置区分），照搬不擅自改文案。
 */
@Composable
internal fun CategoryManageScreen(
    categories: List<CategoryRow>,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onEdit: (CategoryRow) -> Unit,
    onDelete: (CategoryRow) -> Unit,
) {
    val bottomInset = rememberAdaptiveInsets().bottom
    val listState = rememberLazyListState()
    SecondaryPage(
        title = stringResource(R.string.category_title),
        onBack = onBack,
        overlay = { topInset ->
            // Independent consumers: the list can scroll behind both floating actions.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = topInset)
                    .padding(horizontal = themeDp(R.attr.jizhangListRowPadding))
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AddButton(
                    label = stringResource(R.string.category_add),
                    onClick = onAddExpense,
                    modifier = Modifier.weight(1f),
                )
                AddButton(
                    label = stringResource(R.string.category_add),
                    onClick = onAddIncome,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { topInset ->
        LazyColumn(
            state = listState,
            modifier = Modifier.scrollBoundaryHaptics(listState).fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = topInset + 58.dp,
                bottom = bottomInset + themeDp(R.attr.jizhangListBottomPadding),
            ),
        ) {
            if (categories.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillParentMaxHeight(.65f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            emoji = "\uD83C\uDFF7\uFE0F",
                            title = stringResource(R.string.category_add),
                        )
                    }
                }
            }
            items(categories, key = { it.id }) { row ->
                CategoryCard(
                    row = row,
                    onEdit = { onEdit(row) },
                    onDelete = { onDelete(row) },
                )
            }
        }
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier, minHeight = 46.dp) {
        Image(painter = painterResource(R.drawable.ic_add), contentDescription = null,
            modifier = Modifier.size(18.dp), colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSecondaryVariant))
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MiuixTheme.textStyles.button)
    }
}

@Composable
private fun CategoryCard(row: CategoryRow, onEdit: () -> Unit, onDelete: () -> Unit) {
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
                .padding(
                    horizontal = themeDp(R.attr.jizhangListRowPadding),
                    vertical = themeDp(R.attr.jizhangRowVerticalPadding),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标底座：40dp 圆形（colorSurfaceContainerHighest）+ 9dp 内衬 + 图标着色。
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(row.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(themeColor(ATTR_ON_SURFACE_VARIANT)),
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = row.name,
                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                color = themeColor(ATTR_ON_SURFACE),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconAction(R.drawable.ic_edit, R.string.action_edit, enabled = true, onClick = onEdit)
                IconAction(
                    R.drawable.ic_delete,
                    R.string.action_delete,
                    enabled = !row.builtin,
                    onClick = onDelete,
                )
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

/** 分类列表的行数据，由 Activity 侧组装（图标已按 CategoryIcons 解析）。 */
internal data class CategoryRow(
    val id: Long,
    val name: String,
    val iconRes: Int,
    val builtin: Boolean,
)
