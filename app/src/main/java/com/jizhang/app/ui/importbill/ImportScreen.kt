package com.jizhang.app.ui.importbill

import androidx.compose.foundation.lazy.rememberLazyListState
import com.jizhang.app.ui.common.scrollBoundaryHaptics
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import com.jizhang.app.ui.glass.LiquidButton as Button
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.R
import com.jizhang.app.ui.common.ATTR_ON_SURFACE
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.ATTR_PRIMARY
import com.jizhang.app.ui.common.Badge
import com.jizhang.app.ui.common.HintBanner
import com.jizhang.app.ui.common.SecondaryPage
import com.jizhang.app.ui.common.rememberAdaptiveInsets
import com.jizhang.app.ui.common.expenseColor
import com.jizhang.app.ui.common.incomeColor
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.ui.common.themeDp
import com.jizhang.app.ui.glass.LiquidCard as Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 导入微信账单页 —— 纯 Compose（miuix）版（v1.53）。
 *
 * 原 activity_import.xml + item_import_preview.xml + 内嵌 PreviewAdapter 全部删除。
 *
 * ★ 三步状态机原样保留：pickStep / previewStep / loadingStep，同一时刻只显示一个。
 *   这里用 [ImportStep] 枚举表达，比原来 `showStep(View)` 做 View 标识比较更清楚，
 *   互斥性由类型保证（旧版靠三个 visibility 手动对齐，漏一个就会两层叠着显示）。
 *
 * ★ 业务（读文件、解析、写库、弹窗）**全部留在 Activity**。这里只负责：
 *   按 step 渲染 → 把用户动作回调出去。预览行数据由 Activity 组装成 [ImportPreviewRow]。
 *
 * ★ 颜色按 TxKind 区分支出/收入/转账/中性，与旧版 `bind()` 里那套 setTextColor 一致。
 */

/** 当前处于哪一步。三步互斥。 */
internal enum class ImportStep { PICK, PREVIEW, LOADING }

/** 预览列表一行的展示数据（金额文本与颜色已在 Activity 侧按 TxKind 算好）。 */
internal data class ImportPreviewRow(
    val key: String,
    val dateText: String,
    val title: String,
    val subText: String,
    val amountText: String,
    val amountColor: Color,
    val duplicate: Boolean,
)

/** 预览汇总（对应旧版 renderPreview 里往 totalView/newView/dupView/skippedView 塞的那组值）。 */
internal data class ImportSummary(
    val total: Int,
    val newCount: Int,
    val duplicateCount: Int,
    val unrecognized: Int,
    val rangeText: String,
)

@Composable
internal fun ImportScreen(
    step: ImportStep,
    summary: ImportSummary?,
    rows: List<ImportPreviewRow>,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onCommit: () -> Unit,
) {
    val bottomInset = rememberAdaptiveInsets().bottom
    SecondaryPage(title = stringResource(R.string.import_title), onBack = onBack) { topInset ->
        Column(modifier = Modifier.fillMaxSize().padding(bottom = bottomInset)) {
            Spacer(Modifier.height(topInset))

            when (step) {
                ImportStep.PICK -> PickStep(onPickFile = onPickFile)
                ImportStep.PREVIEW -> PreviewStep(
                    summary = summary,
                    rows = rows,
                    onCommit = onCommit,
                )

                ImportStep.LOADING -> LoadingStep()
            }
        }
    }
}

/** 第一步：选文件（大图标 + 主按钮 + 说明条）。 */
@Composable
private fun PickStep(onPickFile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "\uD83D\uDCC4", style = MiuixTheme.textStyles.title1.copy(fontSize = 52.sp))

        Spacer(Modifier.height(20.dp))

        PrimaryButton(
            label = stringResource(R.string.import_pick_file),
            iconRes = R.drawable.ic_add,
            enabled = true,
            onClick = onPickFile,
        )

        Spacer(Modifier.height(20.dp))

        // 原 @drawable/bg_chip 的说明条，与共享 HintBanner 同一几何。
        HintBanner(text = stringResource(R.string.import_help))
    }
}

/** 第二步：预览（汇总卡 + 可滚动列表 + 底部导入按钮）。 */
@Composable
private fun PreviewStep(
    summary: ImportSummary?,
    rows: List<ImportPreviewRow>,
    onCommit: () -> Unit,
) {
    val s = summary ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            cornerRadius = themeDp(R.attr.jizhangListItemCornerRadius),
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(
                color = themeColor(R.attr.jizhangListItemBackground),
                contentColor = themeColor(ATTR_ON_SURFACE),
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = s.rangeText,
                    style = MiuixTheme.textStyles.body2,
                    color = themeColor(ATTR_ON_SURFACE_VARIANT),
                )

                Spacer(Modifier.height(10.dp))

                SummaryStats(s)

                // 旧版：duplicateCount > 0 才显示这行提示。
                if (s.duplicateCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.import_dup_notice),
                        style = MiuixTheme.textStyles.body2,
                        color = themeColor(ATTR_ON_SURFACE_VARIANT),
                    )
                }
            }
        }

        val boundaryListState = rememberLazyListState()
        LazyColumn(
            state = boundaryListState,
            modifier = Modifier.scrollBoundaryHaptics(boundaryListState)
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(rows, key = { it.key }) { row -> PreviewRow(row) }
        }

        PrimaryButton(
            label = stringResource(R.string.import_confirm_button, s.newCount),
            iconRes = null,
            enabled = s.newCount > 0,
            onClick = onCommit,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

/** 第三步：解析中。 */
@Composable
private fun LoadingStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InfiniteProgressIndicator(
            color = themeColor(ATTR_PRIMARY),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.import_reading),
            style = MiuixTheme.textStyles.body2,
            color = themeColor(ATTR_ON_SURFACE_VARIANT),
        )
    }
}

@Composable
private fun SummaryStats(summary: ImportSummary) {
    val stats = listOf(
        Triple(stringResource(R.string.import_stat_total), summary.total, themeColor(ATTR_ON_SURFACE)),
        Triple(stringResource(R.string.import_stat_new), summary.newCount, incomeColor()),
        Triple(stringResource(R.string.import_stat_dup), summary.duplicateCount, themeColor(ATTR_ON_SURFACE)),
    ) + if (summary.unrecognized > 0) {
        listOf(Triple(stringResource(R.string.import_stat_skipped), summary.unrecognized, expenseColor()))
    } else {
        emptyList()
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (stats.size == 4 && (maxWidth < 360.dp || fontScale > 1.15f)) 2 else stats.size
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (statRow in stats.chunked(columns)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((label, value, color) in statRow) {
                        StatCell(label, value.toString(), color, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 汇总卡里的一格：标签保持可读字号，数值按可用宽度收缩。 */
@Composable
private fun StatCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
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
            style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
            color = color,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 18.sp),
        )
    }
}

/** 预览列表的一行：名称、说明和日期分层，金额列限制宽度。 */
@Composable
private fun PreviewRow(row: ImportPreviewRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // 重复行整体淡化。这里没有背景层，alpha 只作用于文字/图标，安全。
            // （凡是「背景之后接 alpha」的写法都要警惕，见 AddTransactionScreen 的保存键注释。）
            .alpha(if (row.duplicate) 0.45f else 1f),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(0.6f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.title,
                style = MiuixTheme.textStyles.body1.copy(fontSize = 14.sp),
                color = themeColor(ATTR_ON_SURFACE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.subText.isNotEmpty()) {
                Text(
                    text = row.subText,
                    style = MiuixTheme.textStyles.body2,
                    color = themeColor(ATTR_ON_SURFACE_VARIANT),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = row.dateText,
                style = MiuixTheme.textStyles.body2,
                color = themeColor(ATTR_ON_SURFACE_VARIANT),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(0.4f), horizontalAlignment = Alignment.End) {
            Text(
                text = row.amountText,
                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
                color = row.amountColor,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 15.sp),
                overflow = TextOverflow.Ellipsis,
            )
            if (row.duplicate) {
                Spacer(Modifier.height(2.dp))
                Badge(
                    text = stringResource(R.string.import_stat_dup),
                    color = themeColor(ATTR_ON_SURFACE_VARIANT),
                )
            }
        }
    }
}

/**
 * 主按钮：铺满 + 52dp 高 + 圆角，底色走主题 primary。
 * 禁用时降透明度（旧版靠 MaterialButton 的 isEnabled 灰化，这里等价表达）。
 */
@Composable
private fun PrimaryButton(
    label: String, iconRes: Int?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(), minHeight = 52.dp,
        colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary,
            contentColor = MiuixTheme.colorScheme.onPrimary)) {
        if (iconRes != null) {
            Image(painterResource(iconRes), null, Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(if (enabled) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.disabledOnPrimaryButton))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = label, style = MiuixTheme.textStyles.button)
    }
}

