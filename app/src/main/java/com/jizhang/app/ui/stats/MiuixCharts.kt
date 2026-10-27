package com.jizhang.app.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.R
import com.jizhang.app.money.Money
import com.jizhang.app.ui.common.expenseColor
import com.jizhang.app.ui.common.incomeColor
import com.jizhang.app.widget.BarPoint
import com.jizhang.app.widget.DonutSlice
import kotlin.math.atan2
import kotlin.math.hypot
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 图表只使用 Compose 绘制；文字、语义色、留白和动画共享 miuix 主题。金额仍以分计。 */
@Composable
internal fun MiuixDonutChart(slices: List<DonutSlice>, title: String, modifier: Modifier = Modifier) {
    val visible = remember(slices) { slices.filter { it.value > 0 } }
    val total = visible.sumOf { it.value }
    var selected by remember(slices) { mutableIntStateOf(-1) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    val track = MiuixTheme.colorScheme.secondaryVariant
    val empty = stringResource(R.string.stats_empty)
    val description = if (total == 0L) empty else "$title，${Money.formatCentsWithSymbol(total)}。" +
        visible.joinToString("；") { "${it.label} ${Money.formatCentsWithSymbol(it.value)}" }
    Box(modifier.semantics {
        contentDescription = description
        customActions = visible.mapIndexed { index, slice ->
            CustomAccessibilityAction(slice.label) { selected = if (selected == index) -1 else index; true }
        }
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().pointerInput(visible) {
            detectTapGestures { point ->
                val dx = point.x - size.width / 2f
                val dy = point.y - size.height / 2f
                val radius = minOf(size.width, size.height) / 2f
                // 中央轻点恢复合计，圈外轻点不改变选择。
                val distance = hypot(dx, dy)
                if (distance < radius * .55f) { selected = -1; return@detectTapGestures }
                if (distance > radius || total <= 0L) return@detectTapGestures
                val angle = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 450.0) % 360.0)
                var end = 0.0
                selected = visible.indexOfFirst { slice -> end += slice.value.toDouble() / total * 360.0; angle < end }
            }
        }) {
            val stroke = 19.dp.toPx()
            val inset = 14.dp.toPx()
            val diameter = size.minDimension - inset * 2
            val origin = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            drawArc(track, -90f, 360f, false, origin, Size(diameter, diameter), style = Stroke(stroke))
            var start = -90f
            visible.forEachIndexed { index, slice ->
                val sweep = (slice.value.toDouble() / total * 360.0).toFloat()
                val gap = if (visible.size > 1) minOf(3f, sweep * .18f) else 0f
                drawArc(
                    color = Color(slice.color).copy(alpha = if (selected < 0 || selected == index) 1f else .28f),
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap) * progress.value,
                    useCenter = false,
                    topLeft = origin,
                    size = Size(diameter, diameter),
                    style = Stroke(if (selected == index) stroke + 4.dp.toPx() else stroke),
                )
                start += sweep
            }
        }
        Column(Modifier.padding(horizontal = 35.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = visible.getOrNull(selected)?.label ?: title,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = Money.formatCents(visible.getOrNull(selected)?.value ?: total),
                style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 23.sp),
            )
        }
    }
}

@Composable
internal fun MiuixTrendChart(points: List<BarPoint>, modifier: Modifier = Modifier) {
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    val expense = expenseColor()
    val income = incomeColor()
    val line = MiuixTheme.colorScheme.dividerLine
    val maxValue = points.maxOfOrNull { maxOf(it.expense, it.income) }?.coerceAtLeast(1L) ?: 1L
    val expenseLabel = stringResource(R.string.stats_expense)
    val incomeLabel = stringResource(R.string.stats_income)
    val summary = points.joinToString("；") {
        "${it.label} $expenseLabel ${Money.formatCentsWithSymbol(it.expense)}，$incomeLabel ${Money.formatCentsWithSymbol(it.income)}"
    }
    Column(modifier.semantics {
        contentDescription = summary
        customActions = points.mapIndexed { index, point ->
            CustomAccessibilityAction(point.label) { selected = index; true }
        }
    }) {
        Text(
            text = Money.formatCentsWithSymbol(if (maxValue == 1L && points.all { it.expense == 0L && it.income == 0L }) 0L else maxValue),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(140.dp).pointerInput(points) {
            detectTapGestures { tap ->
                if (points.isNotEmpty()) selected = (tap.x / size.width * points.size).toInt().coerceIn(points.indices)
            }
        }) {
            if (points.isEmpty()) return@Canvas
            val slot = size.width / points.size
            for (step in 1..2) {
                val y = size.height * step / 2
                drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            points.forEachIndexed { index, point ->
                val center = slot * (index + .5f)
                val width = minOf(15.dp.toPx(), slot * .23f)
                val gap = 3.dp.toPx()
                listOf(point.expense to expense, point.income to income).forEachIndexed { series, (value, color) ->
                    if (value <= 0L) return@forEachIndexed
                    val barHeight = (value.toDouble() / maxValue * size.height * progress.value).toFloat()
                    if (barHeight <= 0f) return@forEachIndexed
                    drawRoundRect(
                        color, Offset(if (series == 0) center - gap / 2 - width else center + gap / 2, size.height - barHeight),
                        Size(width, barHeight), CornerRadius(minOf(width / 2, barHeight / 2)),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            points.forEachIndexed { index, point ->
                Text(
                    text = point.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (index == selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        points.getOrNull(selected)?.let { point ->
            Text(
                text = "${point.label}  $expenseLabel ${Money.formatCentsWithSymbol(point.expense)}  $incomeLabel ${Money.formatCentsWithSymbol(point.income)}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
