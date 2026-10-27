package com.jizhang.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.jizhang.app.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 年月变动时保留日号，月底、大小月和闰年都夹到有效日期。 */
internal fun adjustedDate(date: LocalDate, year: Int = date.year, month: Int = date.monthValue): LocalDate =
    LocalDate.of(year, month, date.dayOfMonth.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()))

/** miuix 没有独立日期控件：组合官方 NumberPicker，复用应用的 miuix 窗口和按钮。 */
@Composable
internal fun MiuixDatePicker(
    initialDate: LocalDate,
    onCancel: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var selected by remember(initialDate) { mutableStateOf(initialDate) }
    val today = remember { LocalDate.now() }
    val cancelLabel = stringResource(R.string.action_cancel)
    val confirmLabel = stringResource(R.string.action_confirm)
    val todayLabel = stringResource(R.string.date_picker_today)
    val availableHeight = LocalDialogContentMaxHeight.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        var summaryHeightPx by remember(maxWidth, density) { mutableIntStateOf(0) }
        var wheelsHeightPx by remember(maxWidth, density) { mutableIntStateOf(0) }
        var todayHeightPx by remember(maxWidth, density, todayLabel) { mutableIntStateOf(0) }
        var actionsHeightPx by remember(maxWidth, density, cancelLabel, confirmLabel) { mutableIntStateOf(0) }
        val measured = summaryHeightPx > 0 && wheelsHeightPx > 0 && todayHeightPx > 0 && actionsHeightPx > 0
        val regularHeight = with(density) {
            (summaryHeightPx + wheelsHeightPx + todayHeightPx + actionsHeightPx).toDp()
        } + 24.dp
        // The shell reveals after two frames, so the regular controls can be measured first.
        val compact = measured && regularHeight > availableHeight
        val visibleItemCount = if (compact) 3 else 5
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = selected.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE")),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                modifier = Modifier.padding(bottom = if (compact) 4.dp else 12.dp)
                    .onSizeChanged { summaryHeightPx = it.height },
            )
            Row(
                Modifier.fillMaxWidth().onSizeChanged { if (!compact) wheelsHeightPx = it.height },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DateWheel(
                    selected.year, minOf(1900, initialDate.year)..maxOf(2100, initialDate.year),
                    "年", visibleItemCount, Modifier.weight(1.25f),
                ) { selected = adjustedDate(selected, year = it) }
                DateWheel(selected.monthValue, 1..12, "月", visibleItemCount, Modifier.weight(1f)) {
                    selected = adjustedDate(selected, month = it)
                }
                key(selected.year, selected.monthValue) {
                    DateWheel(selected.dayOfMonth, 1..selected.lengthOfMonth(), "日", visibleItemCount, Modifier.weight(1f)) {
                        selected = selected.withDayOfMonth(it.coerceIn(1, selected.lengthOfMonth()))
                    }
                }
            }
            if (!compact) {
                TextActionButton(
                    label = todayLabel,
                    onClick = { selected = today },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .onSizeChanged { todayHeightPx = it.height },
                )
            }
            Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
            Row(
                Modifier.fillMaxWidth().onSizeChanged { if (!compact) actionsHeightPx = it.height },
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            ) {
                DialogButton(cancelLabel, false, onCancel, Modifier.weight(1f))
                if (compact) {
                    DialogButton(todayLabel, false, { selected = today }, Modifier.weight(1f))
                }
                DialogButton(confirmLabel, true, { onConfirm(selected) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DateWheel(value: Int, range: IntRange, suffix: String, visibleItemCount: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    // 官方滚轮提供视觉和惯性；额外暴露进度操作，读屏用户也能逐项调整。
    NumberPicker(
        value = value,
        onValueChange = onChange,
        range = range,
        label = { "$it$suffix" },
        visibleItemCount = visibleItemCount,
        itemHeight = 42.dp,
        textStyle = MiuixTheme.textStyles.title3,
        modifier = modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat(), (range.last - range.first - 1).coerceAtLeast(0))
            setProgress { target -> onChange(target.toInt().coerceIn(range)); true }
        },
    )
}
