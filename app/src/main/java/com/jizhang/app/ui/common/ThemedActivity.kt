package com.jizhang.app.ui.common

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.ui.glass.GlassStatusBarHost

/**
 * 所有 Activity 的统一入口。样式在视图创建前生效，切换后由当前页面重建。
 *
 * ## v1.27：状态栏玻璃下沉到基类
 *
 * 以前状态栏模糊只存在于主页（MainActivity 自己持有 `glassStatusBar`），
 * 其它页面都是「系统栏内缩」的传统排版，切过去顶上就是一条纯色，观感不统一。
 * 现在玻璃层的安装、inset 处理由基类统一做，
 * 子类只需要按自己的排版声明两个开关：
 *
 *   · [edgeToEdge]          是否让窗口铺到系统栏下面（默认开）
 *   · [statusBarTopInset]   是否给内容补状态栏高度（有 Toolbar 的页面补，默认补）
 *
 * ## 为什么玻璃装在 `android.R.id.content` 上，而不是各页面的 XML 里
 *
 * 三个理由，都是**层级**层面的：
 *
 *   1. 它必须画在 Activity 内容**之上**，才能糊住穿到状态栏底下的内容；
 *   2. 它必须**不受内容的内边距影响** —— inset 补在 Activity 布局根上，
 *      而玻璃是 `content` 的**兄弟**节点，所以补多少 top 都不会把它一起推下来
 *      （补在共同父节点上就会，这也是不能给 `android.R.id.content` 加 padding 的原因）；
 *   3. 逐页改 XML 必然漏 —— 现在新增 Activity 只要继承本类就自动有。
 *
 * ## 边到边之后必须自己处理的两件事
 *
 *   1. **顶部**：`setDecorFitsSystemWindows(false)` 之后内容会顶到状态栏底下，
 *      有 Toolbar 的页面必须补 [statusBarTopInset]，否则 Toolbar 会被系统图标压住；
 *   2. **键盘**：边到边后 `adjustResize` 失效，窗口**不再被输入法顶起**，
 *      所以这里统一按 `WindowInsetsCompat.Type.ime()` 给内容补 bottom。
 *      带输入框的页面（记一笔 / 分类管理 / 账户管理）靠这条活着。
 */
abstract class ThemedActivity : AppCompatActivity() {
    protected open val useSecondaryTransitions: Boolean = false
    protected open val composeStatusBarGlass: Boolean = false

    /**
     * 本屏是否让窗口铺到系统栏下方绘制（边到边）。
     *
     * v1.27 起**默认打开**：状态栏玻璃要落在状态栏那一条上，前提是窗口真的画到了那里。
     * 打开之后的代价（键盘避让、顶部避让）由本类统一兜住，见类注释。
     */
    protected open val edgeToEdge: Boolean get() = true

    /**
     * 是否给**内容**补状态栏高度。
     *
     * 默认 **true** —— 绝大多数页面顶部有 Toolbar（`?attr/actionBarSize`），
     * 不补的话 Toolbar 会被状态栏的时间/电量图标压住。
     *
     * 主页是唯一的例外（override 为 false）：它要让内容滚到状态栏底下，
     * 玻璃才有东西可糊 —— 这正是 v1.26 那次改动的意义。
     */
    protected open val statusBarTopInset: Boolean get() = true

    /** Pages may keep floating navigation outside the content that avoids the keyboard. */
    protected open fun imeInsetTarget(content: View): View = content

    private var statusBarScrim: GlassStatusBarHost? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyUiStyle()
        super.onCreate(savedInstanceState)
        if (edgeToEdge) {
            // 必须早于 setContentView：它决定 DecorView 是否消费系统栏 inset。
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        disableNavigationBarScrim()
    }

    /**
     * setContentView 之后回调 —— 只有这时才能拿到 `android.R.id.content` 里的内容 View。
     *
     * 玻璃必须在内容**之后**加进去，才能保证绘制顺序在它之上。
     */
    override fun onContentChanged() {
        super.onContentChanged()
        installStatusBarScrim()
    }

    private fun installStatusBarScrim() {
        if (!edgeToEdge || statusBarScrim != null) return
        val root = findViewById<FrameLayout>(android.R.id.content) ?: return
        val content = root.getChildAt(0) ?: return

        applyWindowInsets(content)
        if (composeStatusBarGlass) return

        val host = GlassStatusBarHost(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            )
        }
        // 顺序要紧：先加 host（画在最上层），采样源是 Activity 自己的布局根。
        root.addView(host)
        host.contentView = content
        host.installCompose()
        statusBarScrim = host
    }

    /**
     * 边到边之后由我们接管系统栏避让。
     *
     * 补丁补在**内容根**而不是 `android.R.id.content`：后者是玻璃的父节点，
     * 给它加 padding 会把玻璃一起推到状态栏下面去，那样就白装了。
     *
     * 底部按 IME 补到 [imeInsetTarget]，顶部按 [statusBarTopInset] 决定。
     * 左右按系统栏安全区补（横屏/挖孔）。
     */
    private fun applyWindowInsets(content: View) {
        val base = intArrayOf(content.paddingLeft, content.paddingTop, content.paddingRight, content.paddingBottom)
        val imeTarget = imeInsetTarget(content)
        val imeBaseBottom = imeTarget.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val top = if (statusBarTopInset) bars.top else 0
            view.setPadding(
                base[0] + bars.left,
                base[1] + top,
                base[2] + bars.right,
                base[3] + if (imeTarget === view) ime.bottom else 0,
            )
            if (imeTarget !== view) {
                imeTarget.setPadding(
                    imeTarget.paddingLeft, imeTarget.paddingTop, imeTarget.paddingRight,
                    imeBaseBottom + ime.bottom,
                )
            }
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    /**
     * v1.43：页面**自带**顶栏玻璃时，关掉基类装的这一层。
     *
     * 页面改成 Compose 之后，玻璃可以画在页面自己的 Compose 层级里，
     * 采样走 `rememberLayerBackdrop`（同一次绘制遍历、不再重画 View 树）。
     * 那时基类这一层（View 宿主 + `contentView.draw` 采样）就是多余的，
     * 两层叠在一起会变成双倍模糊。所以由页面自己声明"我接管了"。
     *
     * 还没改成 Compose 的页面不动，继续用基类这一层。
     */
    protected fun setStatusBarGlassEnabled(enabled: Boolean) {
        statusBarScrim?.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    /**
     * v1.50：所有页面的主题 = 基础结构 + 用户选的配色，没有第三层。
     *
     * 以前这里还要按「界面风格」叠一层 ThemeOverlay.Jizhang.Classic / Elegant / HyperOS，
     * 现在那些值已经并入 Theme.Jizhang（见 res/values/themes.xml），
     * 配色层对所有页面恒定生效（经典风格那个「必须保持品牌绿」的特例随之消失）。
     */
    protected fun applyUiStyle() {
        setTheme(com.jizhang.app.R.style.Theme_Jizhang)
        theme.applyStyle(SettingsStore(this).colorPalette.themeOverlay, true)
        if (useSecondaryTransitions) {
            theme.applyStyle(com.jizhang.app.R.style.ThemeOverlay_Jizhang_SecondaryTransitions, true)
        }
    }

    /**
     * 关掉系统给导航栏补的对比度蒙版（那层深色就是「黑边」）。
     *
     * 主题里已经写了 android:enforceNavigationBarContrast=false，这里再走一次运行时开关，
     * 是因为该属性是 API 29 才有的、且在部分厂商 ROM / 强制边到边的 Android 15+ 上
     * 主题属性不一定被读到；运行时开关是官方给的兜底入口。
     * 关掉之后导航栏区域完全由我们自己的玻璃负责，小白条浮在内容之上。
     */
    private fun disableNavigationBarScrim() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    // v1.45：原 SCRIM_THRESHOLD_DP（56dp）已删除。
    // 它只服务于「滚过多少到达满强度」这个进度概念，而顶栏玻璃已整层恒定
    // —— 留着一个没有消费者的阈值，下一轮必然又有人去调它。
}

/** Reads a theme color directly when possible; non-color resource references safely fall back. */
internal fun Context.themeColor(attr: Int): Int {
    val value = TypedValue()
    if (!theme.resolveAttribute(attr, value, true)) return 0
    if (value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
        return value.data
    }
    if (value.resourceId == 0) return value.data
    return runCatching { getColor(value.resourceId) }.getOrDefault(0)
}
