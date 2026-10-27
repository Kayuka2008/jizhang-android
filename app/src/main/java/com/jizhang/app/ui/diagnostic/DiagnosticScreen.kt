package com.jizhang.app.ui.diagnostic

import androidx.compose.foundation.lazy.rememberLazyListState
import com.jizhang.app.ui.common.scrollBoundaryHaptics
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.R
import com.jizhang.app.ui.common.ATTR_ON_SURFACE
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.Badge
import com.jizhang.app.ui.common.EmptyState
import com.jizhang.app.ui.common.SecondaryPage
import com.jizhang.app.ui.common.rememberAdaptiveInsets
import com.jizhang.app.ui.common.expenseColor
import com.jizhang.app.ui.common.incomeColor
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.ui.common.themeDp
import com.jizhang.app.ui.glass.LiquidCard as Card
import com.jizhang.app.ui.glass.LiquidTextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 通知诊断页 —— 纯 Compose（miuix）版（v1.53）。
 *
 * 原 activity_diagnostic.xml + item_notification_log.xml + 内嵌 LogAdapter 全部删除。
 *
 * 列表贯穿全屏；顶栏与删除按钮在独立玻璃层上采样下方内容。
 *
 * ★ 业务仍在 Activity：观察 logs、复制到剪贴板、清空确认框、
 *   snackbar 提示——Compose 侧只有数据与两个回调。
 */
@Composable
internal fun DiagnosticScreen(
    logs: List<DiagLogRow>,
    onBack: () -> Unit,
    onCopy: (DiagLogRow) -> Unit,
    onClearAll: () -> Unit,
) {
    val bottomInset = rememberAdaptiveInsets().bottom
    val density = LocalDensity.current
    var bottomOverlayHeightPx by remember { mutableIntStateOf(0) }
    val bottomOverlayHeight = with(density) { bottomOverlayHeightPx.toDp() }
    val listState = rememberLazyListState()
    SecondaryPage(
        title = stringResource(R.string.diag_title),
        onBack = onBack,
        overlay = {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomOverlayHeightPx = it.height }
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomInset + 12.dp),
            ) {
                LiquidTextButton(
                    text = stringResource(R.string.action_delete_all),
                    onClick = onClearAll,
                    minHeight = 48.dp,
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        textColor = expenseColor(),
                    ),
                    modifier = Modifier.fillMaxWidth(),
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
                top = topInset,
                bottom = bottomOverlayHeight + 16.dp,
            ),
        ) {
            item(key = "hint") {
                Text(
                    text = stringResource(R.string.diag_hint),
                    style = MiuixTheme.textStyles.body2,
                    color = themeColor(ATTR_ON_SURFACE_VARIANT),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (logs.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillParentMaxHeight(.65f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            emoji = "\uD83D\uDD14",
                            title = stringResource(R.string.diag_empty),
                            hint = stringResource(R.string.diag_empty_hint),
                        )
                    }
                }
            }
            items(logs, key = { it.id }) { log ->
                LogCard(log = log, onCopy = { onCopy(log) })
            }
        }
    }
}

@Composable
private fun LogCard(log: DiagLogRow, onCopy: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = themeDp(R.attr.jizhangListItemMarginHorizontal),
                vertical = themeDp(R.attr.jizhangListItemMarginVertical),
            ),
        cornerRadius = themeDp(R.attr.jizhangListItemCornerRadius),
        insideMargin = PaddingValues(
            horizontal = 16.dp,
            vertical = themeDp(R.attr.jizhangListItemVerticalPadding),
        ),
        colors = CardDefaults.defaultColors(
            color = themeColor(R.attr.jizhangListItemBackground),
            contentColor = themeColor(ATTR_ON_SURFACE),
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = log.timeText,
                    style = MiuixTheme.textStyles.body2,
                    color = themeColor(ATTR_ON_SURFACE_VARIANT),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Badge(
                    text = stringResource(
                        if (log.recognized) R.string.diag_recognized else R.string.diag_unrecognized,
                    ),
                    color = if (log.recognized) incomeColor() else themeColor(ATTR_ON_SURFACE_VARIANT),
                )
                Spacer(Modifier.width(4.dp))
                com.jizhang.app.ui.glass.LiquidIconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(36.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = stringResource(R.string.diag_copy),
                        modifier = Modifier.size(18.dp),
                        colorFilter = ColorFilter.tint(themeColor(ATTR_ON_SURFACE_VARIANT)),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = log.title.ifBlank { log.packageName },
                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
                color = themeColor(ATTR_ON_SURFACE),
            )

            Spacer(Modifier.height(2.dp))
            Text(
                text = log.text,
                style = MiuixTheme.textStyles.body2.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                color = themeColor(ATTR_ON_SURFACE),
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = log.matchRuleLabel,
                style = MiuixTheme.textStyles.body2,
                color = themeColor(ATTR_ON_SURFACE_VARIANT),
            )
        }
    }
}

/** 诊断页渲染所需的单条数据，由 Activity 侧格式化好。 */
internal data class DiagLogRow(
    val id: Long,
    val packageName: String,
    val timeText: String,
    val title: String,
    val text: String,
    val recognized: Boolean,
    /** 原始规则名（复制全文用），null 表示未匹配。 */
    val matchedRule: String?,
) {
    /** 卡片上显示的规则行。 */
    val matchRuleLabel: String get() = matchedRule?.let { "规则：$it" } ?: "未匹配规则"
}
