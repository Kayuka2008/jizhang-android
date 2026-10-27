/*
 * 顶部状态栏模糊层。
 *
 * ============================================================================
 * 简要历史（这段路走了 9 个版本，务必读，否则会重犯）
 * ============================================================================
 *
 * 这个类从 v1.15 到 v1.24 一直是「状态栏磨砂玻璃」，但用户对它的反馈只有一句：
 * 「状态栏那里有一条白带」，连续反馈 5 次、发了 8 个版本都没修掉。
 *
 *   v1.15  引入。静息 alpha 0.45（一层看得见的常驻纱）
 *   v1.16  静息 alpha 压到 0.10
 *   v1.19  又抬回 0.35、渐隐带 18dp -> 28dp
 *   v1.20  遮罩曲线改 smoothstep（治"渐隐带末端那条分界线"）
 *   v1.21  调低底色不透明度
 *   v1.22  渐隐带 28dp -> 12dp
 *   v1.23  删掉「加白三件套」（colorSurface 纱 / vibrancy / 采样前垫色）
 *   v1.24  BASE_ALPHA 归零（静息完全不可见）
 *   v1.25  **整层删除**（空壳）
 *   v1.26  **重新实现**（就是现在这版，见下）
 *
 * ★ 为什么那 8 版全部失败：它们都在改**参数**（颜色、不透明度、高度、曲线），
 *   而真正的原因是**纹理差**。
 *
 *   只要那层玻璃有**任何**非零显现度，它作用的那个矩形（状态栏 + 渐隐带 + 尾巴）
 *   就会被 `blur` **抹平纹理** —— 变成一眼均匀的平滑块。而它上下都是纯背景
 *   （本来就没有纹理，但也不"平滑得反常"）。**眼睛在没有细节的地方，会把
 *   "更平滑的一块"读成"有一层东西盖着"**，这就是用户说的「白纱」。
 *
 *   纹理差**调颜色、调位置都消不掉** —— 这就是那 8 版注定白改的原因。
 *   真机剖面证据：那条"带子"其实是 (238,233,241)，页面背景是 (246,242,249)，
 *   同色系只暗 8/9/8 —— **这点差值根本不足以被读成"一条白带"**，
 *   说明我们一直在修的不是用户看到的东西。
 *
 * ============================================================================
 * v1.26 为什么可以重新加回来，以及和旧版的本质区别
 * ============================================================================
 *
 * 前提变了。旧版失败有一个从未被注意到的**根本原因**：
 *
 *   旧版时期，`contentHost` 有 `paddingTop = 状态栏高`，所以**状态栏下面永远是空的**
 *   （只有纯背景色）。**模糊一片纯色在数学上是恒等变换** —— 那层玻璃采到的
 *   永远是没有纹理的空白，所以它**除了把这块抹得更平滑之外什么也没做**。
 *   也就是说：**旧版的纹理差不是"模糊的副作用"，而是"它无聊到只剩下副作用"。**
 *
 *   v1.26 把 `contentHost` 的 `paddingTop` 拿掉了，内容可以滚到状态栏底下
 *   （见 [com.jizhang.app.ui.MainActivity.applySystemBarInsets]）。
 *   这时玻璃**采到的是真实的文字、金额、卡片边缘** —— 模糊终于作用在有纹理的东西上，
 *   就有了可辨认的内容，眼睛读到的是"透过毛玻璃看到的字"，
 *   而不再是"一块平滑得反常的区域"。
 *
 * 所以旧版的三条禁令依然全部保留，一条都不能破：
 *
 *   1. **不叠任何颜色**（不画 colorSurface 底色、不用 vibrancy、不在采样前垫色）。
 *      尤其"采样前 `drawColor(表面色)`"这一条：它会把要模糊的内容整个替换成一层
 *      不透明纯色，**模糊一个常量恒等失效**（这也是旧版"看不出模糊"的根因）。
 *   2. **不引入"看不清为什么存在"的可调旋钮**（旧版的 SURFACE_ALPHA_REST/FULL
 *      就是这么来的，留下之后每轮都被人调，越调越白）。
 *   3. **边界必须落在会动的内容上**。旧版最大的视觉噪声源是"渐隐带终点那条线"，
 *      因为那是个**悬在画面中间、又没有任何内容解释它为何存在**的边界。
 *      本版仍保留渐隐带（用户明确要的），它只在**有内容穿过时**才被读成一层东西 ——
 *      静止时状态栏底下是纯背景，模糊恒等变换，那层玻璃「看不见」，
 *      也就不存在任何边界可看（见 [BASE_ALPHA]）。
 *
 * ============================================================================
 * 本版的行为契约
 * ============================================================================
 *
 *   · **整层恒定，与滚动无关**（v1.45 契约）：显现度恒为 [SCRIM_ALPHA]，
 *     模糊半径恒为 [MAX_BLUR]，不垫底色。
 *
 *   · 静止停在顶部 → 状态栏底下只有纯背景，而模糊纯色是恒等变换，
 *     ⇒ 表现为"看不出任何东西"的玻璃（不是"一层白纱"）。
 *
 *   · 内容滚进状态栏底下 → 玻璃立刻有纹理可糊，观感是毛玻璃。
 *     **显形靠的是内容，不是跟着滚动变大的某个量** —— 这是本版不再有
 *     `scrimProgress` 的全部理由（见下面 v1.45 一节）。
 *
 *   注意"常驻档"在旧版（v1.15~v1.24）反复横跳过：0.45 -> 0.10 -> 0.35 -> 0，
 *   最后被归零。v1.27 重新给非零值的根据见上一节：**白纱来自垫底色，不来自 alpha**。
 *   本版不垫底色，所以常驻是安全的；谁要调这个值，先看那一节。
 *
 * ============================================================================
 * v1.27：重新允许常驻档（BASE_ALPHA 0 -> 0.85）
 * ============================================================================
 *
 * 用户要的是「状态栏位置一直有模糊」，而不是「滚动才浮现」。
 * 这与旧版（v1.15~v1.24）的常驻档**同名但不同物**，必须说清区别，否则会有人
 * 看到数字非零就以为是在走回头路：
 *
 *   旧版的白纱 = **垫了一层别种颜色的底色**。那时采样前先 `drawColor(colorSurface)`，
 *   于是被模糊的对象整个变成一层不透明暖白 —— 那不是"玻璃"，是"换色"。
 *   色差只有 8/9/8 却能被看见，靠的是**色相不同**，不是亮度差。
 *
 *   本版**没有垫任何底色**（禁令 1 依然有效）。此时静息态采到的是纯背景，
 *   而**模糊一片纯色在数学上是恒等变换** —— 结果与输入同色，alpha 再高也
 *   叠不出东西。所以常驻档非零**不产生白纱**：那一层在静止时是"看不见的玻璃"，
 *   一旦有内容钻进状态栏底下，它立刻就有东西可糊，观感才是毛玻璃。
 *
 * 换句话说：旧版是"常驻一层看得见的纱"，本版是"常驻一层看不见的玻璃"。
 * 判据仍然是类注释里那两条 —— 若真机上又看到一条带子，先量垂直剖面色差：
 *   差值个位数  => 不是颜色问题，回去查是不是又有人垫了底色；
 *   差值两位数以上 => 才轮到调这里的 alpha。
 *
 * ============================================================================
 * v1.33：回到"滚动加浓"基线，修掉剩下的四个 bug
 * ============================================================================
 *
 * v1.32（HyperOS 式常驻白磨砂）验证了「垫底色 + 大半径常驻」这条路走得通，
 * 但成品偏重：静止时顶上也是一层白，与记账 App 的轻盈底色不搭。
 * 所以这一版**回到 v1.31 的滚动加浓契约**（[BASE_ALPHA] 静息档 + 滚动升到满档），
 * 把 v1.32 的 HyperOS 改动整块撤销：
 *
 *   · 恢复 [BASE_ALPHA] / [BLUR_FLOOR]，删掉 v1.32 的 SURFACE_ALPHA 与
 *     `onDrawSurface { drawRect(surface) }`（重新回到「不垫底色」）；
 *   · [MAX_BLUR] 24dp -> 20dp；
 *   · 恢复 [animateFloatAsState]（滚动强度重新需要一个平滑量）。
 *
 * 撤销的只是**观感契约**，v1.31 里没修干净的四个 bug 这一版一起处理：
 *
 *   B1 **采样区没裁剪** —— `content.draw()` 每帧把整个 Fragment 的 View 树
 *      重画一遍，而顶栏只需要顶上那一条。明细页几十个 item，滚动时每帧一次，
 *      这是掉帧的主因。修法：只裁出「状态栏 + 渐隐带 + 一个模糊半径」那一条
 *      （[ScrimContent] 里的 [sampleBottomPx]）。
 *   B2 **模糊半径跟着滚动变化** —— v1.33 曾想用「按 2dp 分档」治它
 *      （[ScrimContent] 里的 [blurStepPx]），方向错了，v1.34 已改为**半径恒定**。
 *      这一条是「滚动时抖动」真正的根因，完整算账见 [MAX_BLUR] 那段 ★★ 说明。
 *   B3 **遮罩每帧重建** —— `scrimMask()` 每次重组都新建 ArrayList + 11 个
 *      Color + 一个 Brush。它只跟几何有关、跟滚动无关，可以 remember。
 *   B4 **尾巴余量为零** —— [MASK_TAIL] 原为 20dp，恰好等于 [MAX_BLUR]，
 *      模糊的边缘伪影正好顶到渐隐带终点。提到 32dp 留 1.6 倍余量。
 *
 * ============================================================================
 * v1.45：整层恒定 —— 零随帧变量（与 StatusBarGlass.kt 同一版改动）
 * ============================================================================
 *
 * `scrimProgress` 这一版**删掉了**。它原来把滚动进度写进 snapshot state，
 * 由本函数读出来抬 alpha；现在顶栏不再有"滚动加浓"这回事：
 *
 *   玻璃显形靠的是「内容钻进状态栏底下」带来的纹理，不是靠 alpha 变大。
 *   静止时状态栏底下只有纯背景，模糊纯色是恒等变换，alpha 抬到 1 也看不见；
 *   有内容滚进去时纹理自己就出来了，同样不需要 alpha 帮忙。
 *
 * 于是删掉它的收益是**结构性**的：本函数在页面生命周期内一次都不会重组，
 * 效果链只建一次，抖动的可能性从根上不存在（v1.42 已经证明
 * 「每帧重组 = 每帧重建 GPU 效果链」，那是抖动与"模糊时有时无"的共同根因）。
 *
 * 对比 1.40：它也是常驻不抖，但靠**垫 colorSurface 0.85** 才常驻，
 * 静止时顶上有一层白（用户 v1.32 就嫌过重）。本版不垫色，同样不抖且更轻盈。
 *
 * ============================================================================
 * v1.41：「顶栏上端的模糊没有了」—— 与架构无关，是 effects 的**顺序**问题
 * ============================================================================
 *
 * v1.40 为了"对齐 SleepDown"把 `effects` 写成 `{ vibrancy(); blur(r) }`，
 * 结果顶栏最上面那一条的模糊没了。曾一度归因于「用 View 采样（contentView.draw）
 * 不是纯 Compose，所以糊不出来」，并打算把整页重写成 Compose —— **这个归因是错的**，
 * 依据见 [ScrimContent] 里 effects 那段（backdrop 2.0.1 的 Blur.kt + DrawBackdropNode）。
 *
 * 一句话版本：**blur 前面不能有任何 effect**，否则库的 padding 机制会给录制层
 * 加一圈透明边框，把顶边洗掉。撤销 vibrancy 即恢复。
 *
 * ============================================================================
 * 如果将来又要动这里
 * ============================================================================
 *
 * 先读 SKILL.md §18.19 / §18.20。两条判据：
 *   a) **量垂直剖面**：如果改动后那条带子与背景只差个位数，说明修错了对象；
 *   b) **先确认状态栏下面真的有内容可模糊**。没有内容时加模糊 =
 *      纯粹制造纹理差，加多少参数都没用。
 */
package com.jizhang.app.ui.glass

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.isRuntimeShaderSupported
import com.jizhang.app.data.prefs.GlassStyle
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.util.SafeMode
import com.jizhang.app.util.glassEffectsEnabled

/**
 * 状态栏模糊层宿主。只覆盖状态栏那一条 + 一小段渐隐带，不参与页面测量。
 *
 * ## 为什么仍然保留"宿主 View"这一层（而不是直接在 Activity 里画 Compose）
 *
 * 因为它需要被放在 `activity_main.xml` 的**根 FrameLayout 的最上层**：
 *
 *   · 采样源 `contentView` 必须**先于**它绘制，它才能读到当前帧的内容；
 *   · 它必须盖在页面之上，才能糊住穿到状态栏底下的内容；
 *   · 它必须贴住窗口顶边（`gravity = top`），所以补丁不能打在 `contentHost` 上
 *     —— 一旦 `contentHost` 有了 `paddingTop`，这一层就会被一起推下来。
 *
 * 这三点都是**布局层级**的要求，用一个独立 View 表达最直接。
 */
class GlassStatusBarHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** 采样源：承载 Fragment 的内容容器，与底栏共用同一套采样方式。 */
    var contentView: android.view.View? = null

    // 顶栏强度恒定；底栏显隐由各页面的 Compose 滚动上报独立驱动。

    private var composeInstalled = false

    init {
        setWillNotDraw(true)
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 与底栏一致：XML 里即使写 match_parent 也按内容高度测量，
        // 否则这层会盖住整屏、把下面的点击全部吃掉。
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
    }

    /**
     * 纯装饰层，不参与交互：本视图永不消费触摸，事件原样交给下层页面。
     *
     * 为什么还要显式写一遍：本层是叠在页面之上的，覆盖范围是「状态栏 + 渐隐带」。
     * 一旦 Compose 子树测到整屏高，或者某些版本下 ComposeView 把落在自身范围内的
     * DOWN 吞掉，顶栏附近的按钮就会出现点不动的死区 —— 这种问题在真机上才暴露，
     * 靠布局检查是看不出来的。返回 false 让父容器继续按 z 序找下一个子 View，
     * 代价为零（本层本来就没有任何可点内容）。
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false

    /** 在 setContentView 之后调用，把 Compose 模糊层挂进本 View。 */
    fun installCompose() {
        if (composeInstalled) return
        composeInstalled = true

        val composeView = ComposeView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
            }
            setContent { ScrimContent() }
        }
        addView(composeView)
    }

    @Composable
    private fun ScrimContent() {
        val host = this
        val density = LocalDensity.current
        val statusBarDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        if (!remember(context) { SafeMode.ledgerGlassEnabled(context) }) return
        if (!remember(context) { context.glassEffectsEnabled() }) {
            PlainStatusBarScrim(
                statusBarDp,
                color = Color(context.themeColor(com.google.android.material.R.attr.colorSurface)),
            )
            return
        }
        val glassStyle = remember(context) { SettingsStore(context).glassStyle }

        // v1.46：与 StatusBarGlass.kt 同一套分支。AGSL 要 API 33，
        // 达不到就退回「blur + 遮罩 + 透明尾巴」那条路（还要多留 MASK_TAIL）。
        val progressive = remember { isRuntimeShaderSupported() }
        val tail = if (progressive) 0.dp else MASK_TAIL

        // 状态栏高度。遮罩的几何只跟它有关，滚动时它是常量 ——
        // 所以下面那几个派生量（含遮罩本身）都能缓存，不必每帧重算。
        val totalDp = statusBarDp + FADE_HEIGHT + tail
        val solidUntil = if (totalDp.value > 0f) statusBarDp / totalDp else 1f
        val fadeEnd = if (totalDp.value > 0f) (statusBarDp + FADE_HEIGHT) / totalDp else 1f

        // B3：遮罩只跟几何有关、跟滚动强度无关 —— 缓存住。
        // 原来每次重组都要新建 ArrayList + 11 个 Color + 一个 Brush，
        // 滚动时是每帧一份垃圾；GC 抖动最后会反过来显成「玻璃在抖」。
        // v1.46：shader 路径不用它（收边在 shader 里）。
        val mask = remember(solidUntil, fadeEnd, progressive) {
            if (progressive) null else scrimMask(solidUntil, fadeEnd)
        }

        // 半径恒定（原因见 [MAX_BLUR] 那段 ★★ 说明）。它不随滚动变化，
        // 所以能 remember 住，底层的模糊结果也就能整帧缓存。
        val effectiveBlur = if (glassStyle == GlassStyle.CLEAR) minOf(MAX_BLUR, 4.dp) else MAX_BLUR
        val blurPx = remember(density, effectiveBlur) { with(density) { effectiveBlur.toPx() } }

        // B1：采样只需要顶上这一条，不是整屏。
        //
        // 看得见的输出只在 y ∈ [0, fadeEnd]（fadeEnd 之后遮罩 alpha 恒为 0，画了也看不见），
        // 而半径 r 的模糊在 y 处要采 [y-r, y+r]，所以真正需要的内容只有 [0, fadeEnd + r]。
        // 不裁剪的话 `content.draw()` 会把整个 Fragment 的 View 树重画一遍 ——
        // 明细页几十个 item，滚动时每帧一次，这是掉帧的主因。
        // 裁剪之后 View 的绘制会对不相交的子树快速拒绝，只画顶上那一小段。
        val sampleBottomPx = remember(density, statusBarDp) {
            with(density) { (statusBarDp + FADE_HEIGHT + MAX_BLUR).toPx() }
        }

        // ========================================================================
        // 采样：为什么这里必须是 `contentView.draw(canvas)`（架构硬差异，别再折腾）
        // ========================================================================
        //
        // 曾三次尝试改成「纯 Compose 采样」（对齐 SleepDown 的
        // `rememberGlassLayerBackdrop` / 官方 LiquidBottomTabs 的 `rememberLayerBackdrop`），
        // v1.40 也真的改了。结论是**这条路对本项目的顶栏不成立**，原因不是实现偷懒：
        //
        //   `rememberLayerBackdrop` 能工作的前提是「被采样的东西本身是一个 Compose
        //   节点」——它由 `Modifier.layerBackdrop(...)` 把该节点的绘制录进 GraphicsLayer。
        //   SleepDown 整页是 Compose，所以成立；本项目页面主体是
        //   **Fragment + RecyclerView（Android View）**，View 的绘制发生在
        //   `AndroidComposeView` 之外，Compose 的 GraphicsLayer 录不到它。
        //   唯一能让 View 内容进入 Compose 绘制管道的办法就是手动
        //   `contentView.draw(canvas)` —— 也就是这一行。
        //
        //   `rememberCanvasBackdrop` 是库为此提供的正规入口（backdrops/CanvasBackdrop.kt：
        //   原样转发 onDraw 到 DrawBackdropNode 的 recordLayer），不是"绕过库"。
        //
        // ★ 换句话说：**顶栏这一层本身早就是纯 Compose**（ComposeView + ScrimContent
        //   + drawBackdrop），非 Compose 的部分是**页面内容**，不是顶栏。
        //   而 v1.41 已证明「模糊消失」与采样架构无关（见 [effects] 那段），
        //   把页面重写成 Compose 修不了它。
        // ========================================================================
        // v1.42：把「静态的那一半」整个 remember 住
        //        —— 这是「抖动」和「模糊时有时无」的共同根因
        // ========================================================================
        //
        // ★★ 结论先行：滚动时本函数**一次都不该重组**。★★
        //
        // 为什么这么严重（读 backdrop 2.0.1 源码才看清）：
        //
        //   `Modifier.drawBackdrop(...)` 做的第一件事是 `ShapeProvider(shape)`。
        //   而 `ShapeProvider`（internal/ShapeProvider.kt）是**没有 equals 的普通类**
        //   —— 引用相等。于是**每重组一次就产生一个新实例**，
        //   于是 `DrawBackdropElement.equals()` 恒为 false，于是走
        //   `node.update()` → `invalidateDrawCache()` → `observeEffects()` →
        //   `updateEffects()`，而它干的是：
        //
        //       effectScope.apply(effects)                   // 先把 renderEffect 置 null
        //       graphicsLayer?.renderEffect = <新的 BlurEffect 实例>
        //
        //   ⇒ **每帧重建一次 GPU 效果链**。滚动时 progress 每帧变、本函数每帧重组，
        //     就变成每帧一次 RenderEffect 换血：
        //       · 效果链重建的开销落到每一帧上 —— 这是「抖动」；
        //       · 换血期间一旦有一帧效果没落上去 —— 就是「有时有模糊，有时没有」。
        //
        //   同样的道理也适用于 `rememberCanvasBackdrop` 的 onDraw lambda：
        //   它内部是 `remember(onDraw)`，lambda 每次重组都是新实例 ⇒ Backdrop 换新
        //   ⇒ 同样触发上面那条链。
        //
        // 修法两条，都要做：
        //   1. 把静态 modifier 整段 remember（下面的 `backdropModifier`）——
        //      这样 `drawBackdrop(...)` 只在依赖变化时才被调用一次，
        //      ShapeProvider / 各 lambda 的实例都稳定 ⇒ equals 为 true
        //      ⇒ 滚动时 `DrawBackdropNode` 完全不被打扰。
        //   2. onDraw lambda 也 remember（抽成 [samplingBlock] + remember），
        //      让 `rememberCanvasBackdrop` 里的 Backdrop 实例同样稳定。
        //
        // ★ 顺带定一条规则：本函数里**唯一**允许随滚动变化的东西，是下面
        //   `graphicsLayer { alpha = ... }` 那一行 —— 它只改 alpha，不碰效果链。
        //   谁要往滚动里加第二个变量，先读这段。
        val sampling = remember(host, sampleBottomPx) { samplingBlock(host, sampleBottomPx) }
        val backdrop = rememberCanvasBackdrop(sampling)

        // 模糊层：半径恒定（[MAX_BLUR]），滚动只驱动 alpha（见上面那段）。
        val backdropModifier = remember(backdrop, blurPx, solidUntil, progressive) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                // v1.41：v1.40 的 `vibrancy(); blur(...)` 已撤销，回到**只有 blur**。
                //
                // ★★ 真实根因（读 backdrop 2.0.1 源码才定位到，务必看完再改）★★
                //
                //   effects/Blur.kt：
                //       if (edgeTreatment != TileMode.Clamp || renderEffect != null) {
                //           if (radius > padding) padding = radius
                //       }
                //
                //   `padding` 会被 DrawBackdropNode 用来**放大录制层**：
                //       recordLayer(layer, size = 节点尺寸 + 2×padding)
                //       canvas.translate(padding, padding)   // 内容往里挪
                //       layer.topLeft = (-padding, -padding) // 再整体移回来
                //
                //   ⇒ 内容四周多出一圈 **padding 宽的全透明边框**。
                //
                //   只有 blur 时：`renderEffect == null` 且默认 Clamp ⇒ padding = 0
                //     ⇒ 没有透明边框，模糊在顶边 Clamp 到真实内容，正常。
                //   v1.40 把 vibrancy() 写在 blur() **前面** ⇒ 进入 blur 时
                //     `renderEffect != null` ⇒ padding = 20dp（= 半径）。
                //     ⇒ 录进来的图上下各有 20dp 透明边，而可见区顶部
                //       恰好整段落在「内容 + 透明边」的混合区里：
                //       Clamp 采样把这 20dp 与透明像素一起平均 ⇒ alpha 被拉低
                //       ⇒ 状态栏（高约 24~28dp）几乎整条洗掉。
                //       这就是用户说的「顶栏**上端**的模糊没有了」——
                //       不是全没了，是**最上面那一条**没了，与现象完全吻合。
                //
                // ★ 两条衍生结论：
                //   1. 顶栏 effects 里 **blur 必须是第一个**，且全程保持
                //      `renderEffect == null` 进入它，否则 padding 一非 0
                //      就重演。想在 blur 之后加效果（如 lens）要先确认 padding
                //      变化不会影响顶边；想加 vibrancy 则要接受透明边框。
                //   2. 这与「采样是 View 还是 Compose」**无关** —— v1.39b 用同样的
                //      `contentView.draw()` 采样、模糊是好的。别再往架构上找原因。
                // v1.46：优先走自写 shader（变半径 + shader 内收边，与
                // StatusBarGlass.kt 共用一份）；API < 33 才退回纯 blur。
                effects = if (progressive) {
                    { progressiveStatusBarBlur(blurPx, solidUntil) }
                } else {
                    { blur(blurPx) }
                },
                // 与底栏同理：drawBackdrop 的默认 highlight/shadow 是给悬浮
                // 胶囊描边投影用的，套到通栏矩形上会在四周画出一圈亮边和投影
                // （状态栏遮罩的底边因此会多一条横线）。这里是纯覆盖层，显式关掉。
                highlight = null,
                shadow = null,
            )
        }

        // 遮罩同理：只跟几何有关，整段 remember，别每帧新建 lambda。
        // v1.46：shader 路径没有遮罩（收边在 shader 里）。
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

        // 显现度：**恒定**（v1.45）。原来是"静息 [SCRIM_ALPHA]，滚满升到 1"，
        // 那一段由 `scrimProgress` 驱动，是这个组合体每帧重组的唯一原因。
        // 玻璃显形靠的是内容钻进状态栏底下带来的纹理，不需要 alpha 跟着滚动变 ——
        // 静止时底下是纯背景，模糊恒等，抬到 1 也看不见。
        // 详见类注释 v1.45 一节。
        val alpha = SCRIM_ALPHA
        val layerModifier = remember(alpha) {
            Modifier.graphicsLayer {
                this.alpha = alpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .then(layerModifier)
                .then(maskModifier),
        ) {
            // 只负责撑出高度：状态栏 + 渐隐带（+ 兜底路径的全透明尾巴）。
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
                Box(Modifier.fillMaxWidth().height(FADE_HEIGHT))
                if (tail.value > 0f) Box(Modifier.fillMaxWidth().height(tail))
            }

            // 模糊层：半径恒定（[MAX_BLUR]），滚动只驱动上面的 alpha。
            Box(Modifier.matchParentSize().then(backdropModifier))
        }
    }

    /**
     * 采样块。抽成函数是为了能 `remember(...)` 住它 ——
     * `rememberCanvasBackdrop` 内部是 `remember(onDraw)`，onDraw 的**实例**一变，
     * Backdrop 就换新，`DrawBackdropNode` 就要重建效果链（详见调用处那段注释）。
     *
     * 所以这里**不能**把 lambda 直接写在组合体里；必须走本函数 + remember。
     *
     * `DrawScope` 本身就是 `Density`，所以 `sampleBottomPx` 直接以像素传入、
     * 由调用方 remember；density 变化会走 remember 的 key，不需要在这里现算。
     */
    private fun samplingBlock(
        host: GlassStatusBarHost,
        sampleBottomPx: Float,
    ): DrawScope.() -> Unit = {
        val content = host.contentView
        if (content != null) {
            val contentLocation = IntArray(2)
            content.getLocationInWindow(contentLocation)
            val hostLocation = IntArray(2)
            host.getLocationInWindow(hostLocation)

            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.withSave {
                    // ★★ 这里**不能**加 `drawColor(...)` 垫底色。★★
                    //
                    // 垫色会把要模糊的内容整个替换成一层不透明纯色，`blur` 采到的
                    // 永远是同一个常量，模糊恒等失效（v1.15~v1.22 的「看不出模糊」
                    // 和「状态栏发白」都是它）。
                    // v1.32 试过垫在**玻璃面上**（drawBackdrop 的 onDrawSurface，
                    // 模糊之后，不参与采样），观感偏重，v1.33 已撤销 —— 现在回到
                    // 「全程不垫色」，即禁令 1 的原样。
                    //
                    // 裁剪必须在 translate **之前**：这一层的坐标系是宿主坐标系，
                    // 先按宿主坐标裁出顶上那一条，再平移到内容坐标系，
                    // 裁剪框随画布一起走，命中的就是内容顶部那一段。
                    clipRect(0f, 0f, size.width, minOf(size.height, sampleBottomPx))
                    translate(
                        (contentLocation[0] - hostLocation[0]).toFloat(),
                        (contentLocation[1] - hostLocation[1]).toFloat(),
                    )
                    content.draw(this)
                }
            }
        }
    }

    /**
     * 顶栏遮罩的 alpha 曲线：`[0, solidUntil]` 全实，`[solidUntil, fadeEnd]` 缓出到全透明，
     * `[fadeEnd, 1]` 恒为 0。
     *
     * 为什么用 smoothstep 而不是"两段直线"（v1.19 及以前的做法）：
     * 直线坡道走到终点时斜率还是一条**非零常数**，紧接着突然归零 —— 一阶导不连续。
     * 人眼对这种斜率突变极其敏感（马赫带效应），会把再平滑不过的直线坡道的**末端**
     * 看成一条横向分界线。
     *
     * 把坡道加长只能把线推远，治不了；必须让**两端斜率都归零**。
     * 公式：a(u) = 1 - (3u² - 2u³)。
     *
     * Compose 的渐变只在色标之间做线性插值，所以按 [MASK_STEPS] 采样打点用折线逼近曲线。
     * 用 DstIn 时只看 alpha，颜色保持黑色无所谓。
     */
    private fun scrimMask(solidUntil: Float, fadeEnd: Float): Brush {
        val start = solidUntil.coerceIn(0f, 1f)
        val end = fadeEnd.coerceIn(start, 1f)
        val span = end - start
        val stops = ArrayList<Pair<Float, Color>>(MASK_STEPS + 2)

        stops += 0f to Color.Black
        stops += start to Color.Black
        for (i in 1..MASK_STEPS) {
            val u = i / MASK_STEPS.toFloat()
            val t = u.coerceIn(0f, 1f)
            stops += (start + span * u) to
                Color.Black.copy(alpha = 1f - (3f * t * t - 2f * t * t * t))
        }
        // 尾巴段（end..1）保持全透明：模糊矩形的硬边就藏在这里面。
        if (end < 1f) stops += 1f to Color.Transparent

        // 注意用 vararg 重载：另有一个 verticalGradient(colors: List<Color>, ...) 重载，
        // 直接传 Array<Pair<..>> 两个都匹配不上。必须用 * 展开。
        return Brush.verticalGradient(*stops.toTypedArray())
    }

    private companion object {
        /**
         * 渐隐区高度：状态栏之下再溶掉这一段（遮罩在这一段里把整层溶掉）。
         *
         * 语义沿革（这段数值史本身就是"误诊史"，别删）：
         *   18dp（v1.15 引入）
         *   -> 28dp（v1.19：用户要求"过渡明显一点"，把带子加长）
         *   -> 12dp（v1.22：用户反馈"14:03 和 9月中间存在一块白色区域"，
         *            当时**错误地**归因于"渐隐带压在月份切换卡上"）
         *   -> 保持 12dp（v1.23~v1.25 那几轮改的是别的东西）
         *
         * v1.26 重新实现时保持 12dp，理由与 v1.22 完全不同（v1.22 的成因已被推翻）：
         *   12dp 这个值的真正意义是"**别让渐隐带本身成为视觉主体**"。
         *   它只要够短，滚动的主动权就落在内容上（内容一动玻璃就出现），
         *   而不是落在"那条渐变带自己有多显眼"上。
         *   v1.19 加到 28dp 时，带子自己就成了画面上一块可辨认的区域 —— 那是错的。
         */
        val FADE_HEIGHT = 12.dp

        /**
         * 整层的显现度。**恒定**，不随滚动变化（v1.45 起）。
         *
         * 非零是安全的，前提是本版**不垫任何底色**（禁令 1）：静息时状态栏底下
         * 只有纯背景，而模糊一片纯色在数学上是恒等变换 —— 结果与输入同色，
         * alpha 再高也叠不出东西。所以这是「常驻一层看不见的玻璃」，
         * 而不是旧版「常驻一层看得见的纱」。
         *
         * 历史数值（勿删，这段就是误诊史）：0.45(v1.15) → 0.10(v1.16) → 0.35(v1.19)
         *   → 0(v1.24) → 0(v1.26) → 0.85(v1.27) → v1.32 整层恒 1（HyperOS 版）
         *   → v1.33 恢复 0.85 + 滚动加浓 → **v1.45 取消滚动加浓，只留这一个值**。
         *
         * 别再改回「BASE + 滚动增量」两档结构：多出来的那一档会让这个组合体
         * 每帧重组，而每帧重组 = 每帧重建 GPU 效果链（见类注释 v1.42 节）。
         */
        const val SCRIM_ALPHA = 0.85f

        /**
         * 模糊半径。**恒定，不随滚动变化。** 20dp 是顶栏玻璃的糊度区间：
         * 再大底下的内容就只剩色块、读不出轮廓（v1.32 试过 24dp，配合垫底色
         * 是 HyperOS 那种「彻底糊白」的路子，已撤销）。
         *
         * ★★ 为什么半径必须恒定 —— 这是「滚动时抖动」的根因，务必读完再动。★★
         *
         *   v1.31 ~ v1.33 让半径跟着滚动从 [BLUR_FLOOR] 档升到满档，出发点是对的
         *   （想做「滚动加浓」），但量级算错了：
         *
         *       阈值只有 56dp（168px @3x）走完 0 -> 1
         *       半径跨度 7dp -> 20dp，即 39px @3x
         *       ⇒ 每滚 1px，半径变 39/168 ≈ 0.23px
         *
         *   惯性滚动一帧位移 30~50px —— **半径每帧要跳 7~12px**。这不是"渐变"，
         *   是每帧一次硬跳，模糊结果的亮度跟着一跳一跳，就是用户说的抖动。
         *
         *   v1.33 试图用「分档」救它（按 2dp 量化），结果只是把**高频小幅跳**
         *   换成**低频大幅跳**（每帧跨一档 = 6px）。跳得少了，但每一跳更大、
         *   更突兀 —— 分档能治的是慢速滚动时在两个相邻整数间横跳的那种抖动，
         *   治不了「跨度本身太大」。方向错了。
         *
         *   真正的解法是别让半径参与滚动：系统 UI（iOS 导航栏、HyperOS 顶栏）
         *   的模糊半径都是**恒定**的，滚动时变化的只有遮盖强度。半径恒定之后
         *   · 没有跳变，抖动源消失；
         *   · 底层模糊结果可以整帧缓存，省掉每帧一次大半径模糊。
         *
         *   那「滚动加浓」还剩下什么？剩下 [BASE_ALPHA] -> 1 这段 alpha，
         *   以及**更主要的**——内容自己钻进状态栏底下带来纹理（见类注释 v1.26 节）。
         *   玻璃显形靠的是后者，不是半径。
         */
        // v1.46：不再自己写死一个数值，直接指向共享的 [GlassStatusBarTokens]，
        // 免得两条路径（Compose 页面 / View 页面）的糊度哪天悄悄跑偏。
        val MAX_BLUR = GlassStatusBarTokens.MAX_BLUR

        // （原 SCRIM_ANIM_MS = 120 已删除）
        //
        /**
         * 遮罩盒在渐隐带之下多留的全透明尾巴。
         *
         * 存在的理由是「模糊矩形的下硬边」和「遮罩 alpha 归零处」原本压在同一行，
         * 两个边界重合会把任何取整/插值误差显成一条横线。
         * 取值必须 >= [MAX_BLUR]，模糊自身的夹取伪影才会整段落在透明区里。
         *
         * v1.33：20dp -> 32dp。原来恰好等于 [MAX_BLUR]（20dp），是**零余量**的下确界：
         * 伪影长度只按「一个模糊半径」估算，而高斯核的实际影响范围比标称半径更大
         * （取决于库怎么映射 radius 与 σ），压线取值在部分 ROM / 不同 density 上
         * 会把伪影顶到渐隐带终点那条线上。取 1.6 倍留足余量。
         * 代价只是离屏 buffer 高 12dp —— 采样量不受它影响（见 [sampleBottomPx]）。
         */
        val MASK_TAIL = 32.dp

        /** 曲线采样点数。>= 6 才够密，折线贴得住 smoothstep（见 [scrimMask]）。 */
        const val MASK_STEPS = 8
    }
}
