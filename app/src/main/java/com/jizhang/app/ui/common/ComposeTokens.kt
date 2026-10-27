package com.jizhang.app.ui.common

import android.content.Context
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jizhang.app.R
import com.jizhang.app.data.prefs.GlassStyle
import com.jizhang.app.data.prefs.SettingsStore

/*
 * Compose 页面共用的「读主题」工具。
 *
 * v1.51：从 LedgerScreen 里抽出来共享 —— 待确认页、统计页改成 Compose 之后
 * 也要按同一套 `jizhang*` 主题 token 取形状与颜色，各写一份必然分叉
 * （这跟 v1.50 把三份结构层 overlay 合并进基础主题是同一个道理：token 只有一份）。
 *
 * 为什么这些 app 属性用 Tyrone（Material）前缀而不是 `jizhang*`：
 * 应用只声明了 `jizhang*` 前缀的自定义属性，语义色必须借 Material 的，
 * 详见 MEMORY.md「依赖现状」。
 */

internal val ATTR_ON_SURFACE = com.google.android.material.R.attr.colorOnSurface
internal val ATTR_ON_SURFACE_VARIANT = com.google.android.material.R.attr.colorOnSurfaceVariant
internal val ATTR_PRIMARY = com.google.android.material.R.attr.colorPrimary
internal val ATTR_ON_PRIMARY = com.google.android.material.R.attr.colorOnPrimary
internal val ATTR_SURFACE = com.google.android.material.R.attr.colorSurface

/** 读主题里的颜色属性。 */
@Composable
internal fun themeColor(attr: Int): Color {
    val context = LocalContext.current
    val color = Color(context.themeColor(attr))
    return if (attr == ATTR_ON_SURFACE_VARIANT && rememberGlassStyle() == GlassStyle.CLEAR) {
        // 全透时副文字直接落在图片上，使用更接近正文的实色前景。
        lerp(color, Color(context.themeColor(ATTR_ON_SURFACE)), 0.7f).copy(alpha = 1f)
    } else color
}

/** 根主题建立时尚无玻璃场景，直接读取持久化选择；切档后由 Activity 重建刷新。 */
@Composable
internal fun rememberGlassStyle(): GlassStyle {
    val context = LocalContext.current
    return remember(context) { SettingsStore(context).glassStyle }
}

/** 读主题里的尺寸属性（dp）。 */
@Composable
internal fun themeDp(attr: Int): Dp = LocalContext.current.themeDimen(attr)

/**
 * 读一个 dimension 属性。
 *
 * ★ `TypedValue.resourceId` 不一定指向 dimension（它可能指向任何资源），所以必须
 *   runCatching —— 读不出来宁可当 0，不能让整页因为一个尺寸炸掉。
 *   这条来自 v1.43.2 的血案，详情见 [themeColor] 旁的注释。
 */
internal fun Context.themeDimen(attr: Int): Dp {
    val value = TypedValue()
    if (!theme.resolveAttribute(attr, value, true)) return 0.dp
    val px = runCatching {
        if (value.resourceId != 0) {
            resources.getDimension(value.resourceId)
        } else {
            value.getDimension(resources.displayMetrics)
        }
    }.getOrDefault(0f)
    return Dp(px / resources.displayMetrics.density)
}

/** 支出红 / 收入绿 / 转账蓝 —— 涨红跌绿按 A 股习惯。 */
@Composable
internal fun expenseColor(): Color = colorResource(R.color.expense)

@Composable
internal fun incomeColor(): Color = colorResource(R.color.income)

@Composable
internal fun transferColor(): Color = colorResource(R.color.transfer)

