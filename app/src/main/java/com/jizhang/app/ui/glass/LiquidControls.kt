package com.jizhang.app.ui.glass

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Keep miuix sizing, semantics and interaction, changing only the material underneath. */
@Composable
internal fun LiquidCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(cornerRadius) { RoundedRectangle(cornerRadius) }
    MiuixCard(
        modifier = modifier.liquidSurface(shape, colors.color),
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        colors = colors.copy(color = Color.Transparent),
        onClick = onClick,
        content = content,
    )
}

@Composable
internal fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = remember(cornerRadius) { RoundedRectangle(cornerRadius) }
    MiuixButton(
        onClick = onClick, modifier = modifier.liquidSurface(
            shape, if (enabled) colors.color else colors.disabledColor,
            role = LiquidSurfaceRole.FloatingControl,
        ),
        enabled = enabled, cornerRadius = cornerRadius, minWidth = minWidth, minHeight = minHeight,
        colors = colors.copy(color = Color.Transparent, disabledColor = Color.Transparent),
        insideMargin = insideMargin, content = content,
    )
}

@Composable
internal fun LiquidTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: TextButtonColors = ButtonDefaults.textButtonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    textStyle: TextStyle? = null,
) {
    val shape = remember(cornerRadius) { RoundedRectangle(cornerRadius) }
    MiuixTextButton(
        text = text, onClick = onClick, modifier = modifier.liquidSurface(
            shape, if (enabled) colors.color else colors.disabledColor,
            role = LiquidSurfaceRole.FloatingControl,
        ),
        enabled = enabled, cornerRadius = cornerRadius, minWidth = minWidth, minHeight = minHeight,
        colors = colors.copy(color = Color.Transparent, disabledColor = Color.Transparent),
        insideMargin = insideMargin, textStyle = textStyle,
    )
}

@Composable
internal fun LiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = remember { RoundedRectangle(40.dp) }
    MiuixIconButton(
        onClick = onClick,
        modifier = modifier.liquidSurface(shape, MiuixTheme.colorScheme.surfaceContainer,
            role = LiquidSurfaceRole.FloatingControl),
        enabled = enabled, backgroundColor = Color.Transparent, content = content,
    )
}

/** Keep the switch thumb native while giving its track the same clear-glass treatment as buttons. */
@Composable
internal fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scene = LocalLiquidScene.current
    if (scene?.style != com.jizhang.app.data.prefs.GlassStyle.CLEAR) {
        MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
        return
    }
    val trackColor = if (checked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondary
    MiuixSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.liquidSurface(
            shape = CircleShape,
            tint = trackColor,
            role = LiquidSurfaceRole.FloatingControl,
        ),
        enabled = enabled,
        colors = SwitchDefaults.switchColors(
            checkedTrackColor = Color.Transparent,
            uncheckedTrackColor = Color.Transparent,
            disabledCheckedTrackColor = Color.Transparent,
            disabledUncheckedTrackColor = Color.Transparent,
        ),
    )
}
