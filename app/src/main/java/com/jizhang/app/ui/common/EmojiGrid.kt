package com.jizhang.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.R as MaterialR
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * emoji 选择网格（v1.56）。
 *
 * 替代旧的 `EmojiPicker`（那个用的是 RecyclerView + GridLayoutManager + Material
 * 对话框：给一个 64 项的静态列表套 RecyclerView，纯粹是 XML 时代的惯性）。
 * 现在就是一个固定 8 列的 Compose 网格，嵌在统一的 [JizhangDialogHost] 对话框里。
 *
 * 中文记账 App 的分类图标用 emoji 最自然，也省掉维护几十个矢量资源。
 */

/** 常用图标，按生活场景分组排列。 */
internal val EMOJI_CHOICES: List<String> = listOf(
    "🍜", "🍚", "🍔", "🍕", "🍰", "☕", "🍺", "🍎",
    "🚌", "🚇", "🚕", "🚗", "✈️", "🚄", "⛽", "🅿️",
    "🛍️", "👕", "👟", "💄", "💍", "👜", "🧴", "🛒",
    "💡", "💧", "🔥", "📱", "🌐", "🏠", "🛋️", "🔧",
    "💊", "🏥", "🦷", "👓", "🏃", "⚽", "🎬", "🎮",
    "📚", "✏️", "🎓", "🎁", "🧧", "🐾", "🍼", "👶",
    "💰", "💵", "💳", "🏦", "📈", "📉", "🧾", "↩️",
    "🏆", "🧰", "💼", "🎯", "📦", "🔖", "❤️", "⭐",
)

private const val COLUMNS = 8

/**
 * 固定 8 列的 emoji 选择网格。
 *
 * [selected] 那一格用主色容器底标出来（旧版是给未选中的降透明度 —— 那条路
 * 在部分 GL 路径下会把背景层丢掉，见 v1.54.1 的保存键血案）。
 */
@Composable
internal fun EmojiGrid(
    selected: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .verticalScrollWithBoundaryHaptics(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EMOJI_CHOICES.chunked(COLUMNS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                row.forEach { emoji ->
                    EmojiCell(
                        emoji = emoji,
                        selected = emoji == selected,
                        onClick = { onPick(emoji) },
                    )
                }
                // 最后一行可能不满 8 个，补空位保证前面的格子宽度不变。
                repeat(COLUMNS - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RowScope.EmojiCell(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) themeColor(MaterialR.attr.colorPrimaryContainer) else Color.Transparent,
            )
            .clickable { onClick() }
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            style = TextStyle(fontSize = 21.sp),
            color = themeColor(ATTR_ON_SURFACE),
        )
    }
}
