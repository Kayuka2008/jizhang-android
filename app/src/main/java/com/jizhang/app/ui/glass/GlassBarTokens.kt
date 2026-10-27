/*
 * 底栏玻璃的视觉常量集中地（v1.36 重构时建立）。
 *
 * ★ 为什么要有这个文件：以前这些数字散在 LiquidBottomBar / GlassBottomBarHost
 *   的各个角落（BAR_HEIGHT 在文件底部、containerColor 的配比写在 composable 里、
 *   阴影/高光参数埋在 drawBackdrop 的 lambda 里），动一处外观要满文件找；
 *   而且有的参数之间有**隐式耦合**（阴影下沉量 = 半径 / 6），分开放迟早有人只改一半。
 *
 * 分工：改外观只动这里；要动结构（布局 / 交互）去 LiquidBottomBar / GlassTabLayer。
 *
 * 形态：v1.36 曾对齐 SleepDown 做成迷你胶囊，v1.37 按用户要求**改回 v1.35 的
 * 近全宽大胶囊**（68dp），但保留 v1.36 的两样东西：页签图标+文字常驻、
 * 「记一笔」与页签同构。玻璃质感对齐官方 LiquidBottomTabs（vibrancy +
 * blur 8 + lens 24 + 底色 0.4）—— 这是用户给的截图里那种「背景透过胶囊
 * 被透镜弯折」的效果配方。
 */
package com.jizhang.app.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object GlassBarTokens {

    // ---------- 几何 ----------

    /**
     * 玻璃容器高度。
     * 沿革：v1.35 及以前 68dp（近全宽）→ v1.36 迷你化收到 56dp
     * → v1.37 按用户要求**改回 68dp**（官方 LiquidBottomTabs 是 64dp）。
     */
    val BarHeight: Dp = 68.dp

    /**
     * 玻璃内容区内边距：四边各这么多。
     * 水平方向同时参与槽宽计算：unitWidth = (容器宽 - 2×此值) / 槽数。
     * ★ 必须声明在 [ContentHeight] 之前 —— object 里属性按声明顺序初始化。
     */
    val ContentInset: Dp = 4.dp

    /**
     * 玻璃内容区高度 = 容器减去上下 [ContentInset]。
     * 选中胶囊用这个高度 —— 它要比容器小一圈，露出容器自己的玻璃边。
     */
    val ContentHeight: Dp = BarHeight - ContentInset * 2

    /** 胶囊距屏幕左右边的间距（宿主里加，恢复 v1.35 的近全宽排版）。 */
    val BarSideGap: Dp = 14.dp

    /** 胶囊上方的内容间距。 */
    val BarTopGap: Dp = 8.dp

    /** 底边至少留出这段悬浮空间；系统导航/手势区更大时按实时安全区避让。 */
    val MinimumBottomClearance: Dp = 24.dp

    /** 页签里文字行的高度（图标 24dp + 间距 2dp + 文字行）。 */
    val LabelHeight: Dp = 13.dp

    // ---------- 阴影 ----------

    /**
     * 阴影半径：v1.32 8dp → v1.35 2dp → HyperOS 试做 1.5dp。
     * HyperOS 的胶囊几乎没有明显外影，只留一层很薄的空气感。
     */
    val ShadowRadius: Dp = 1.5.dp

    /**
     * 阴影下沉量 = [ShadowRadius] / 此值。
     * 沿用库默认的比例（DpOffset(0, radius/6)），但必须**跟着当前半径现算** ——
     * 库的默认 offset 是构造时按 24dp 算死的，copy(radius) 不会重算（v1.35 踩过）。
     */
    const val ShadowOffsetDivisor = 6f

    // ---------- 高光描边 ----------

    /** 高光宽度；blurRadius 取同值（库默认只有半宽，太锐）。 */
    val HighlightWidth: Dp = 0.5.dp

    /**
     * 高光不透明度。库默认 1.0 是 v1.28 前被嫌弃的那条实边，别抄回去。
     * HyperOS 试做：从 0.45 降到 0.35，白色胶囊上的描边更淡。
     */
    const val HighlightAlpha = 0.35f

    // ---------- 容器底色（对齐 2.9.8 的算法：中性底色混主题色）----------

    /**
     * HyperOS 试做：底栏更「白开水」一点。
     * 中性底色改为纯白 / 深灰，容器不透明度降到 0.35，让磨砂更透。
     */
    val BaseColorLight = Color(0xFFFFFFFF)
    val BaseColorDark = Color(0xFF1C1C1E)

    /** 中性底色向主题色逐通道插值的比例。 */
    const val AccentMixLight = 0.12f
    const val AccentMixDark = 0.16f

    /**
     * 容器整体不透明度。
     *
     * v1.37：0.56/0.62 → **0.40**（明暗同值，对齐官方 LiquidBottomTabs）。
     * 这是「图中折射效果」能看见的前提 —— 上一版底色太浓，透镜弯折的内容
     * 被盖在底下看不出来；官方 0.4 的底色下，lens 扭曲的背景纹路清晰可辨
     * （用户给的截图里"课程"图标和背景文字被弯成弧形，就是它）。
     *
     * HyperOS 试做：降到 0.35，让玻璃更轻。
     */
    const val ContainerAlpha = 0.35f

    /** Clear glass keeps the sampled page visible without the frosted white tint. */
    const val ClearContainerAlphaLight = 0.08f
    const val ClearContainerAlphaDark = 0.12f

    /** 全透浅色底栏的文字颜色。 */
    val ClearLabelColorLight = Color(0xFF242424)

    // ---------- 玻璃效果（对齐官方 LiquidBottomTabs：vibrancy + blur 8 + lens 24）----------

    /** 容器玻璃的模糊半径。 */
    val BlurRadius: Dp = 8.dp

    /** 折射（lens）半径。 */
    val LensRadius: Dp = 24.dp

    // ---------- 交互 ----------

    /** 底栏收起 / 恢复的动画时长（原来叫 REVEAL_ANIM_MS）。 */
    const val RevealAnimMs = 220

    /** 提示退场后，底栏轻微上移并渐显；独立于滚动显隐的时长。 */
    const val SnackbarReturnAnimMs = 280L
    val SnackbarReturnOffset: Dp = 12.dp

    /** 收起超过这个透明度就不再接收触摸（原来叫 TOUCH_ALPHA_CUTOFF）。 */
    const val TouchAlphaCutoff = 0.5f
}
