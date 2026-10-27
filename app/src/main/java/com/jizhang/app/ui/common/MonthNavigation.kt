package com.jizhang.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.R
import com.jizhang.app.ui.glass.LiquidIconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared month controls with explicit gaps; only the label participates in month transitions. */
@Composable
internal fun MonthNavigation(
    label: String,
    flip: FlipState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canGoNext: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthArrow(R.drawable.ic_chevron_left, R.string.ledger_prev_month) {
            flip.goPrev()
            onPrev()
        }
        FlipLabel(label, flip, modifier = Modifier.weight(1f)) { text ->
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = MiuixTheme.textStyles.body1,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 17.sp),
            )
        }
        MonthArrow(R.drawable.ic_chevron_right, R.string.ledger_next_month, canGoNext) {
            flip.goNext()
            onNext()
        }
    }
}

@Composable
private fun MonthArrow(res: Int, desc: Int, enabled: Boolean = true, onClick: () -> Unit) {
    LiquidIconButton(onClick, modifier = Modifier.size(40.dp), enabled = enabled) {
        Image(painterResource(res), stringResource(desc), Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceContainerVariant
                .copy(alpha = if (enabled) 1f else .38f)))
    }
}
