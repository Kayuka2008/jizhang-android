/*
 * 顶部状态栏玻璃 —— 纯 Compose 实现（v1.43）。
 *
 * ============================================================================
 * 为什么要把这一层抽出来
 * ============================================================================
 *
 * 以前只有 [GlassStatusBarHost] 一个宿主：它是一个 Android View，里面塞一个
 * ComposeView，自己用 `contentView.draw(canvas)` 采样。
 *
 * v1.43 要把**页面内容**也改成 Compose（对齐 SleepDown），页面就能用
 * `rememberLayerBackdrop` + `Modifier.layerBackdrop` 走 Compose 自己的
 * 图形层录制来采样 —— 那是**同一帧、同一次绘制遍历**里发生的事，
 * 不需要在玻璃的绘制过程中再去把整棵 View 树重画一遍。
 *
 * 两条采样路径会产生两个不同的 `Backdrop`，但**玻璃本身只有一份实现**。
 * 所以把玻璃抽成这个可组合函数，谁拿到什么 Backdrop 就传什么：
 *
 *   · [GlassStatusBarHost]  → `rememberCanvasBackdrop`（页面还是 View 时的兜底）
 *   · Compose 页面          → `rememberLayerBackdrop`（页面已是 Compose）
 *
 * ============================================================================
 * v1.46：模糊改成自写 AGSL —— 变半径 + shader 内收边（照 SleepDown 的路子）
 * ============================================================================
 *
 * 参考来源：github.com/xiaomanjun233/SleepDown-Schedule（源码已开源，别再反编译它的 APK）
 *   HomeTopGradientBlur → ProgressiveBackdropBlur → nexioProgressiveBlur（自写 AGSL）
 *
 * v1.45 之前这里做的是「**恒定**半径 + 一层独立的 DstIn 遮罩刷」：
 * 半径不动（为了不抖），收边靠遮罩把 alpha 乘成 0。
 * 那套能用，但有两个副作用：
 *
 *   1. 模糊矩形的**下硬边**必须藏在一段全透明尾巴里（[MASK_TAIL] 32dp），
 *      否则取整/插值误差会在渐隐带终点显成一条横线 —— 于是凭空多出一个
 *      「尾巴要留多少余量」的旋钮（32dp 是 1.6 倍半径拍出来的）；
 *   2. 遮罩是一支 `Brush`，每换一次几何就要重建一次（虽然已 remember）。
 *
 * v1.46 换成 shader 里做完两件事：
 *
 *   · 半径按 y 递减：`radius = maxRadius * (1 - smoothstep(fadeStart, 1, t))`
 *     —— 状态栏那一段是满半径，进入渐隐带才开始收，到层底归零；
 *   · 收边也是 shader 内：`color *= 1 - softerstep(fadeStart, 1, t)`。
 *
 * ⇒ 不再有硬边，也就**不再需要尾巴**（[MASK_TAIL] 只在 API<33 的兜底路径里还留着）。
 * ⇒ 半径从 20dp 降到 14dp（SleepDown 只用 7dp；我们要保证系统图标底下够糊，折中取 14dp）。
 *
 * ★ 变半径会不会重新引入抖动？不会：这里的「变」是**沿 y 的几何函数**，
 *   每一帧算出来的都是同一条曲线，跟滚动位置无关。抖动的根源是
 *   「某个量随**时间/滚动**变」，不是「某个量沿**空间**变」。
 *
 * API < 33（没有 AGSL RuntimeShader）自动退回 v1.45 的 blur + 遮罩路径，
 * 两条路径共用同一批常量，观感尽量接近。
 *
 * ============================================================================
 * v1.45：整层恒定 —— 零随帧变量
 * ============================================================================
 *
 * 这一版把 `progress` 参数**整个删掉**了。原因不是"滚动加浓不好看"，
 * 而是它**根本不需要存在**：
 *
 *   顶栏玻璃显形靠的是「内容钻进状态栏底下」带来的纹理，
 *   不是靠某个量跟着滚动变大。静止时状态栏底下是一片纯背景，
 *   而**模糊一片纯色在数学上是恒等变换** —— 那时玻璃是"看不见的玻璃"，
 *   alpha 取 0.85 还是 0.2 都一样看不出。一旦有内容滚进去，
 *   纹理自然就出来了，同样不需要 alpha 帮忙。
 *
 * 于是「滚动加浓」唯一的实际作用，是在系统图标底下把 alpha 从 0.85 抬到 1
 * —— 而这一点点差别换来的代价是：**每帧都有一个量在变**。
 * 抖动的全部可能性，都是从"让某个量随滚动变"那一刻才被引入的
 * （v1.42 那次已经证明：哪怕只变 alpha，只要它引起重组，
 *  `ShapeProvider` 无 equals 就会让 GPU 效果链每帧重建一次）。
 *
 * 所以：**alpha 恒定、blur 恒定、不垫色** ⇒ 整层没有任何随帧变化的量
 * ⇒ 组合体连一次都不会重组 ⇒ 效果链建一次用到页面销毁 ⇒ **零抖动**。
 *
 * 对比 1.40（也就是 1.44）：它也是常驻不抖，但它**垫了 colorSurface 0.85**
 * 才能常驻，静止时顶上就有一层白 —— 用户 v1.32 就嫌过重。
 * 本版不垫色，同样不抖，且静止时是"看不见的玻璃"。
 *
 * ============================================================================
 * 三条规定（都是踩出来的，改之前先读）
 * ============================================================================
 *
 * 0. **这一层里不允许有任何随滚动变化的量**（见上一节）。
 *    谁要加「滚动越强越浓」，先回答：静止时底下连内容都没有，浓给谁看？
 *
 * 1. **effects 里 blur 必须是第一个**。
 *    backdrop 2.0.1 的 `effects/Blur.kt`：
 *        if (edgeTreatment != TileMode.Clamp || renderEffect != null) { padding = radius }
 *    而 `padding` 会让 `DrawBackdropNode` 把录制层放大 `2×padding`，
 *    内容再 `translate(padding, padding)` —— 多出来的一圈是**全透明**的。
 *    blur 前面一旦有别的效果，可见区顶部就会与这圈透明像素混合，
 *    alpha 被拉低 ⇒ 状态栏整条洗掉（v1.40 就是这个）。
 *
 * 2. **所有传给 backdrop 的 lambda / modifier 必须 remember**。
 *    `Modifier.drawBackdrop(...)` 内部第一行是 `ShapeProvider(shape)`，
 *    而 `ShapeProvider` **没有 equals**（引用相等）。只要重组一次，
 *    `DrawBackdropElement.equals` 就为 false ⇒ `node.update()` ⇒
 *    `updateEffects()` ⇒ 重建一次 `BlurEffect` / RenderEffect。
 *    滚动时每帧重组 = 每帧重建 GPU 效果链（v1.42 修的就是这个）。
 *    v1.45 起本层已无任何随帧变量，理论上一次都不会重组；
 *    这些 remember 是**保险**，不是性能优化 —— 别因为"反正不重组"就拆掉。
 *
 * 3. **不垫任何颜色**（禁令 1）。垫底色会让"模糊纯色"变成恒等变换失效，
 *    那才是旧版"状态栏一条白带"的根因。
 */
package com.jizhang.app.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import com.jizhang.app.data.prefs.GlassStyle
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.util.SafeMode
import com.jizhang.app.util.glassEffectsEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 顶栏玻璃的全部视觉常量。改观感只动这里。 */
object GlassStatusBarTokens {
    /**
     * 渐隐区高度：状态栏之下再溶掉这一段。
     *
     * 12dp 的意义是「别让渐隐带本身成为视觉主体」—— 它只要够短，
     * 滚动的主动权就落在内容上，而不是落在那条渐变带自己有多显眼上。
     * （v1.19 曾加到 28dp，带子自己就成了画面上一块可辨认的区域，是错的。）
     */
    val FADE_HEIGHT = 12.dp

    /**
     * 整层的显现度。**恒定**，不随滚动变化（v1.45 起）。
     *
     * 非零是安全的，前提是**不垫底色**：静息时状态栏底下是纯背景，
     * 而模糊一片纯色在数学上是恒等变换 —— alpha 再高也叠不出东西。
     * 有内容钻进去时，纹理自己会出来，也不需要抬 alpha 帮忙。
     *
     * 历史：v1.31 起它是「静息档」，滚动升到 1；v1.45 取消滚动加浓，
     * 只留这一个值。别再把它改回"BASE + 增量"两档结构 ——
     * 多出来的那一档就是抖动的唯一入口。
     */
    const val SCRIM_ALPHA = 0.85f

    /**
     * 模糊半径（层顶的满值）。**沿 y 递减，但绝不随滚动变化。**
     *
     * 两件事要分清，别混：
     *   · 随**滚动**变 ⇒ 抖动的经典根因（阈值 56dp 走完 0→1，半径跨度 39px，
     *     惯性滚动一帧 30~50px ⇒ 半径每帧跳 7~12px，那是硬跳不是渐变）；
     *   · 沿 **y** 变 ⇒ v1.46 引入的变半径，每帧算出的是同一条曲线，与时间无关。
     *
     * v1.46：20dp → 14dp。参照 SleepDown（它只用 7dp，但它底下有壁纸可糊）；
     * 我们要保证系统图标底下那一段足够糊，折中取 14dp。
     */
    val MAX_BLUR = 14.dp

    /**
     * 遮罩盒在渐隐带之下多留的全透明尾巴。
     *
     * 「模糊矩形的下硬边」和「遮罩 alpha 归零处」不能压在同一行，
     * 两个边界重合会把任何取整/插值误差显成一条横线。
     * 必须 >= [MAX_BLUR]，模糊的夹取伪影才会整段落在透明区里；
     * 取 1.6 倍（32dp）留足余量。
     *
     * ★ v1.46：**只有 API < 33 的兜底路径还需要它**。
     *   shader 路径的收边做在 shader 里（见 [progressiveStatusBarBlur]），
     *   整层就没有硬边了，这段尾巴连同「尾巴要留多少余量」这个旋钮一起消失。
     */
    val MASK_TAIL = 32.dp

    /** 曲线采样点数。>= 6 才够密，折线贴得住 smoothstep。 */
    const val MASK_STEPS = 8
}

/**
 * 状态栏玻璃。盖在页面之上，糊住钻到状态栏底下的内容。
 *
 * @param backdrop 采样源。Compose 页面传 `rememberLayerBackdrop()`，
 *                 View 页面传 `rememberCanvasBackdrop { contentView.draw(...) }`。
 * @param statusBarHeight 状态栏高度。**由调用方传入**，不用 `WindowInsets.statusBars` ——
 *                 后者在 Fragment 的 ComposeView 里是否拿得到取决于 inset 分发时机，
 *                 而高度错了整层的几何全错；从 Activity 侧读是确定的。
 */
@Composable
fun StatusBarGlassScrim(
    backdrop: Backdrop,
    statusBarHeight: Dp,
    modifier: Modifier = Modifier,
    fadeHeight: Dp = GlassStatusBarTokens.FADE_HEIGHT,
    maxBlur: Dp = GlassStatusBarTokens.MAX_BLUR,
) {
    val context = LocalContext.current
    if (!remember(context) { SafeMode.ledgerGlassEnabled(context) }) return
    if (!remember(context) { context.glassEffectsEnabled() }) {
        PlainStatusBarScrim(statusBarHeight, fadeHeight, modifier)
        return
    }
    val glassStyle = remember(context) { SettingsStore(context).glassStyle }
    val statusBarDp = statusBarHeight

    // v1.46：AGSL（RuntimeShader）要 API 33。达不到就走 v1.45 的 blur + 遮罩路径，
    // 那条路径还要多留一段全透明尾巴（见 [GlassStatusBarTokens.MASK_TAIL]）。
    val progressive = remember { isRuntimeShaderSupported() }
    val tail = if (progressive) 0.dp else GlassStatusBarTokens.MASK_TAIL

    val totalDp = statusBarDp + fadeHeight + tail
    val solidUntil = if (totalDp.value > 0f) statusBarDp / totalDp else 1f
    val fadeEnd =
        if (totalDp.value > 0f) {
            (statusBarDp + fadeHeight) / totalDp
        } else {
            1f
        }

    // 遮罩只跟几何有关 —— 缓存住。原来每次重组都要新建
    // ArrayList + 11 个 Color + 一个 Brush，滚动时是每帧一份垃圾。
    // v1.46：shader 路径根本不用它（收边在 shader 里），只在兜底路径建。
    val mask = remember(solidUntil, fadeEnd) {
        if (progressive) null else scrimMask(solidUntil, fadeEnd)
    }

    // 半径只跟几何有关 ⇒ 能 remember，模糊结果可整帧缓存。
    val density = LocalDensity.current
    val effectiveBlur = if (glassStyle == GlassStyle.CLEAR) minOf(maxBlur, 4.dp) else maxBlur
    val radiusPx = remember(density, effectiveBlur) { with(density) { effectiveBlur.toPx() } }

    // 规定 2：整段 remember，滚动时不重建。
    val backdropModifier = remember(backdrop, radiusPx, solidUntil, progressive) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = if (progressive) {
                // v1.46：自写 shader，变半径 + shader 内收边（见文件头 v1.46 一节）。
                {
                    progressiveStatusBarBlur(radiusPx, solidUntil)
                }
            } else {
                // 规定 1：只有 blur，且它是第一个（v1.40 的 vibrancy 已撤销）。
                { blur(radiusPx) }
            },
            // drawBackdrop 默认的高光/阴影是给悬浮胶囊描边投影用的，
            // 套到通栏矩形上会在四周画出一圈亮边和投影，显式关掉。
            highlight = null,
            shadow = null,
        )
    }

    val maskModifier = remember(mask) {
        if (mask == null) {
            Modifier
        } else {
            Modifier.drawWithContent {
                drawContent()
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
            }
        }
    }

    // 规定 0：整层恒定。alpha 是常量，所以连 graphicsLayer 都能 remember ——
    // 本函数在页面生命周期内**一次都不会重组**，效果链只会建一次。
    val alpha = GlassStatusBarTokens.SCRIM_ALPHA
    val layerModifier = remember(alpha) {
        Modifier.graphicsLayer {
            this.alpha = alpha
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .then(layerModifier)
            .then(maskModifier),
    ) {
        // 只负责撑出高度：状态栏 + 渐隐带（+ 兜底路径的全透明尾巴）。
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(statusBarHeight))
            Box(Modifier.fillMaxWidth().height(fadeHeight))
            if (tail.value > 0f) Box(Modifier.fillMaxWidth().height(tail))
        }

        Box(Modifier.matchParentSize().then(backdropModifier))
    }
}

/** Ordinary solid toolbar cover; no backdrop nodes, shaders or recorded page. */
@Composable
internal fun PlainStatusBarScrim(
    statusBarHeight: Dp,
    fadeHeight: Dp = GlassStatusBarTokens.FADE_HEIGHT,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.background,
) {
    val totalHeight = statusBarHeight + fadeHeight
    val solidUntil = if (totalHeight.value > 0f) (statusBarHeight / totalHeight).coerceIn(0f, 1f) else 1f
    val brush = remember(color, solidUntil) {
        Brush.verticalGradient(0f to color, solidUntil to color, 1f to color.copy(alpha = 0f))
    }
    Box(modifier.fillMaxWidth().height(totalHeight).drawBehind { drawRect(brush) })
}

/**
 * v1.46 的顶栏模糊：**变半径 + shader 内收边**（AGSL RuntimeShader，API >= 33）。
 *
 * 思路来自 SleepDown 的 `nexioProgressiveBlur`（AGPL，已按其许可注明来源），
 * 但常量和曲线都按本工程的几何重算过，不是照抄。
 *
 * ★★ 为什么这里敢把 `padding` 设成半径（与「规定 1」相反）★★
 *
 *   「blur 前不能有任何 effect」那条禁令的**机理**是：padding 会让录制层
 *   比可见区大一圈，多出来的那圈是**全透明**的，模糊采样把它平均进来
 *   ⇒ 顶边 alpha 被洗掉。
 *
 *   SleepDown 给的是另一条解法：**在 shader 里跳过近透明样本**
 *   （下面的 `if (c.a > 0.02)`）。透明像素不进加权平均，那圈 padding
 *   就只是"给采样留的余地"，不再稀释结果。
 *   ⇒ 于是我们既能拿到 padding（变半径模糊需要它去采层外的像素），
 *     又不会重演 v1.40 的「顶栏上端模糊没了」。
 *
 * @param radius     层顶（状态栏那一段）的满半径，像素。进入渐隐带后按曲线收到 0。
 * @param fadeStart  开始收半径/收边的归一化位置 = 状态栏高度 / 层高。
 */
internal fun BackdropEffectScope.progressiveStatusBarBlur(radius: Float, fadeStart: Float) {
    padding = radius
    val bufferW = size.width + 2f * padding
    val bufferH = size.height + 2f * padding
    // 第一遍：变半径螺旋采样
    runtimeShaderEffect("StatusBarProgressiveBlur", STATUS_BAR_BLUR_SHADER, "content") {
        setFloatUniform("contentOrigin", padding, padding)
        setFloatUniform("contentSize", size.width, size.height)
        setFloatUniform("bufferSize", bufferW, bufferH)
        setFloatUniform("maxRadius", radius)
        setFloatUniform("fadeStart", fadeStart.coerceIn(0f, 0.95f))
    }
    // 第二遍：小半径盒式平均，压掉随机采样留下的细噪
    // （半径与模糊强度成正比，跟第一遍同一条曲线，否则两遍半径不一致会糊出重影）
    runtimeShaderEffect("StatusBarProgressiveDenoise", STATUS_BAR_DENOISE_SHADER, "content") {
        setFloatUniform("contentOrigin", padding, padding)
        setFloatUniform("contentSize", size.width, size.height)
        setFloatUniform("bufferSize", bufferW, bufferH)
        setFloatUniform("maxRadius", radius)
        setFloatUniform("fadeStart", fadeStart.coerceIn(0f, 0.95f))
    }
}

/** 两端导数为 0 的 S 曲线，收尾比 smoothstep 更绵（SleepDown 同名函数）。 */
private const val SOFTER_STEP = """
float softerstep(float a, float b, float x) {
    float s = clamp((x - a) / max(b - a, 0.0001), 0.0, 1.0);
    return s * s * s * (s * (s * 6.0 - 15.0) + 10.0);
}
"""

private const val HASH12 = """
float hash12(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
"""

/**
 * 第一遍：32 抽样点的黄金角螺旋 + 逐像素旋转 + 径向扰动。
 *
 * 为什么不是规整的高斯核：高半径时采样间距大于笔画宽度，固定螺旋会把文字
 * 打成规则星点；逐像素旋转核把点阵打散成细噪（再由第二遍压掉）。
 *
 * `if (c.a > 0.02)` 是整段的关键 —— 见 [progressiveStatusBarBlur] 的说明。
 */
private const val STATUS_BAR_BLUR_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float2 bufferSize;
uniform float maxRadius;
uniform float fadeStart;

$SOFTER_STEP
$HASH12

half4 progressiveBlur(float2 coord, float radius) {
    if (radius < 0.5) {
        return content.eval(coord);
    }
    half4 sum = half4(0.0);
    float wsum = 0.0;
    float spin = hash12(coord) * 6.2831853;
    for (int i = 0; i < 32; i++) {
        float fi = float(i);
        float r = radius * pow((fi + 0.5) / 32.0, 0.5);
        r *= 0.90 + 0.20 * hash12(coord + float2(fi, 1.7));
        float a = fi * 2.39996323 + spin;
        float2 o = float2(cos(a), sin(a)) * r;
        float w = exp(-r * r / max(0.85 * radius * radius, 0.001));
        float2 sc = clamp(coord + o, float2(0.0), max(bufferSize - 1.0, float2(0.0)));
        half4 c = content.eval(sc);
        if (c.a > 0.02) {
            sum += c * w;
            wsum += w;
        }
    }
    if (wsum < 0.0001) {
        return content.eval(coord);
    }
    return sum / wsum;
}

half4 main(float2 coord) {
    // 可见区从 contentOrigin 起算，padding 那一圈不参与 Y 渐变
    float t = clamp((coord.y - contentOrigin.y) / max(contentSize.y, 1.0), 0.0, 1.0);
    float u = 1.0 - smoothstep(fadeStart, 1.0, t);
    half4 color = progressiveBlur(coord, maxRadius * u);
    // 收边也在 shader 里：渐隐带末端 alpha 归零，下面就没有硬边可看，
    // 于是 v1.45 那 32dp 的透明尾巴（MASK_TAIL）不再需要。
    color *= (1.0 - softerstep(fadeStart, 1.0, t));
    return color;
}
"""

/** 第二遍：5 点小盒式平均，半径 = 满半径 × 0.18，只压噪不再糊开。 */
private const val STATUS_BAR_DENOISE_SHADER = """
uniform shader content;
uniform float2 contentOrigin;
uniform float2 contentSize;
uniform float2 bufferSize;
uniform float maxRadius;
uniform float fadeStart;

$SOFTER_STEP

half4 main(float2 coord) {
    float t = clamp((coord.y - contentOrigin.y) / max(contentSize.y, 1.0), 0.0, 1.0);
    float u = 1.0 - smoothstep(fadeStart, 1.0, t);
    float r = maxRadius * u * 0.18;
    if (r < 0.4) {
        return content.eval(coord);
    }
    float2 lo = float2(0.0);
    float2 hi = max(bufferSize - 1.0, float2(0.0));
    half4 sum = content.eval(coord);
    sum += content.eval(clamp(coord + float2(r, 0.0), lo, hi));
    sum += content.eval(clamp(coord - float2(r, 0.0), lo, hi));
    sum += content.eval(clamp(coord + float2(0.0, r), lo, hi));
    sum += content.eval(clamp(coord - float2(0.0, r), lo, hi));
    return sum / 5.0;
}
"""

/**
 * 顶栏遮罩的 alpha 曲线：`[0, solidUntil]` 全实，`[solidUntil, fadeEnd]` 缓出到全透明。
 *
 * 为什么用 smoothstep 而不是两段直线：直线坡道走到终点时斜率还是一条非零常数，
 * 紧接着突然归零 —— 一阶导不连续。人眼对这种斜率突变极其敏感（马赫带效应），
 * 会把再平滑不过的直线坡道的**末端**看成一条横向分界线。
 * 公式：a(u) = 1 - (3u² - 2u³)。
 *
 * 渐变只在色标之间做线性插值，所以按 [GlassStatusBarTokens.MASK_STEPS] 采样打点逼近。
 * 用 DstIn 时只看 alpha，颜色无所谓。
 */
private fun scrimMask(solidUntil: Float, fadeEnd: Float): Brush {
    val start = solidUntil.coerceIn(0f, 1f)
    val end = fadeEnd.coerceIn(start, 1f)
    val span = end - start
    val stops = ArrayList<Pair<Float, Color>>(GlassStatusBarTokens.MASK_STEPS + 2)

    stops += 0f to Color.Black
    stops += start to Color.Black
    for (i in 1..GlassStatusBarTokens.MASK_STEPS) {
        val t = (i / GlassStatusBarTokens.MASK_STEPS.toFloat()).coerceIn(0f, 1f)
        stops += (start + span * (i / GlassStatusBarTokens.MASK_STEPS.toFloat())) to
            Color.Black.copy(alpha = 1f - (3f * t * t - 2f * t * t * t))
    }
    // 尾巴段（end..1）保持全透明：模糊矩形的硬边就藏在这里面。
    if (end < 1f) stops += 1f to Color.Transparent

    // 必须用 vararg 重载：另有一个 verticalGradient(colors: List<Color>, ...) 重载，
    // 直接传 Array<Pair<..>> 两个都匹配不上。
    return Brush.verticalGradient(*stops.toTypedArray())
}

