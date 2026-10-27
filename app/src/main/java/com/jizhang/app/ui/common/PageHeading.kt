package com.jizhang.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared by page titles and settings group labels, measured from the screen edge. */
internal val PageHeadingStart = 32.dp

@Composable
internal fun PageHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.Medium, fontSize = 28.sp),
        color = MiuixTheme.colorScheme.onBackground,
    )
}
