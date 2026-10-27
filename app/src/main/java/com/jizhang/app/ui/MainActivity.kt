package com.jizhang.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jizhang.app.R
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.databinding.ActivityMainBinding
import com.jizhang.app.ui.common.appContainer
import com.jizhang.app.ui.common.PageFlip
import com.jizhang.app.ui.common.ScrollOffsetPage
import com.jizhang.app.ui.common.ThemedActivity
import com.jizhang.app.ui.glass.GlassBottomBarHost
import com.jizhang.app.ui.ledger.LedgerFragment
import com.jizhang.app.util.CrashLog
import com.jizhang.app.ui.pending.PendingFragment
import com.jizhang.app.ui.settings.SettingsFragment
import com.jizhang.app.ui.stats.StatsFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ThemedActivity() {

    private lateinit var binding: ActivityMainBinding

    /**
     * 主页必须边到边。
     *
     * 不开会怎样（实测）：窗口内容在导航栏上沿就被截断，导航栏那 20dp 里没有任何
     * 应用内容，露出来的是 `windowBackground`（= ?attr/colorSurface，近白）——
     * 底栏玻璃画得再高也铺不过去，那就是用户看到的「胶囊与底边之间的白色区域」。
     * 开了之后 `WindowInsets.navigationBars` 才拿得到真实高度（本机 65px / 20dp），
     * 底栏那层磨砂底座也才真的落在小白条上，而不是只有 DOCK_GAP 那 10dp。
     *
     * （v1.27 起基类默认就是 true，这里显式写出来是为了保住这段"不开会怎样"的实测记录。）
     */
    override val edgeToEdge: Boolean get() = true

    /**
     * 主页**不补**状态栏高度 —— 全项目只有这里是 false。
     *
     * 补了会怎样：页面内容被整体推到状态栏之下，状态栏那一条永远是空的（只有纯背景色），
     * 顶部玻璃采到的永远是没有纹理的空白，于是那层玻璃除了把那一块抹得更平滑之外
     * 什么也没做，而"一块比周围更平滑的区域"恰恰就是用户反复投诉的那条「白带」。
     * 详见 [com.jizhang.app.ui.glass.GlassStatusBarHost] 的类注释。
     *
     * 不补之后内容可以滚到状态栏底下，顶部玻璃才真正有东西可糊。
     * 「内容不要一开始就顶到状态栏」改由各页滚动容器自己的 paddingTop 提供
     * （`clipToPadding=false`），那层留白是滚动容器的一部分，滚动时会穿过去。
     */
    override val statusBarTopInset: Boolean get() = false

    override fun imeInsetTarget(content: View): View = content.findViewById(R.id.contentHost)

    /** 当前页签 id，避免重复创建 Fragment。 */
    private var currentTab = R.id.tab_ledger

    /** 上一次读到的滚动偏移，用来判断这一帧是向下滚还是向上滚（驱动底栏显隐）。 */
    private var lastScrollOffset = 0

    /** 同一方向累积的滚动量（px）。> 0 表示累积向下、< 0 表示累积向上，换方向即清零。 */
    private var barScrollAccum = 0

    /**
     * 上一次翻页动画涉及的 View。
     *
     * 每次切页先把它们的状态强制复原 —— 否则连续快速点页签时，上一次动画还跑着，
     * 页面会停在半透明 / 缩小状态变成残影（详见 [com.jizhang.app.ui.common.PageFlip.reset]）。
     */
    private val flippingViews = HashSet<View>()

    /** 向下滚这么多就收起底栏。 */
    private val barHideThresholdPx: Int
        get() = (BAR_HIDE_THRESHOLD_DP * resources.displayMetrics.density).toInt()

    /** 向上滚这么多就恢复底栏。比收起阈值小 = 恢复更灵敏。 */
    private val barShowThresholdPx: Int
        get() = (BAR_SHOW_THRESHOLD_DP * resources.displayMetrics.density).toInt()

    /** 距顶部这个范围内一律视为「停在顶部」，强制显示底栏。 */
    private val barTopSlopPx: Int
        get() = (BAR_TOP_SLOP_DP * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Modern IME insets let content avoid the keyboard without resizing navigation.
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        // 上次崩过：先把堆栈给用户看，再谈别的。
        // 顺序必须是「先跳走、后走正常流程」—— 否则崩溃在下面这段里重演一次，
        // 用户就永远只看到"已停止运行"，拿不到任何线索。
        if (CrashLog.hasPending(this)) {
            startActivity(Intent(this, CrashReportActivity::class.java))
            finish()
            return
        }

        val settings = SettingsStore(this)
        if (!settings.onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        }
        // 状态栏玻璃由基类在 onContentChanged() 里统一装好（含 inset 处理），
        // 所以这里不再自己 applySystemBarInsets / setupGlassStatusBar。
        // 顶部 inset 的开关见 [statusBarTopInset]。
        setContentView(binding.root)

        if (savedInstanceState == null) {
            showTab(R.id.tab_ledger)
        } else {
            currentTab = savedInstanceState.getInt(KEY_TAB, R.id.tab_ledger)
        }

        setupGlassBottomBar()

        observePendingBadge()
    }

    private fun setupGlassBottomBar() {
        val host = binding.glassBottomBar
        host.contentView = binding.contentHost
        host.selectedTabIndex = tabIndexFor(currentTab)
        host.onTabSelected = { index ->
            val tabId = tabIdFor(index)
            if (tabId != currentTab) showTab(tabId)
        }
        host.onAddClick = {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
        host.installCompose()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Older Android needs adjustResize to report IME insets. Keep navigation
            // out of that temporary viewport until the full window returns.
            ViewCompat.setOnApplyWindowInsetsListener(host) { view, insets ->
                view.visibility = if (insets.isVisible(WindowInsetsCompat.Type.ime())) View.INVISIBLE else View.VISIBLE
                insets
            }
            ViewCompat.requestApplyInsets(host)
        }
    }

    /**
     * v1.43：明细页改成 Compose 之后，它自己画顶栏玻璃，基类那一层要关掉。
     *
     * 由 `LedgerFragment.onViewCreated` 回调 —— 只有明细页会调。
     */
    fun onLedgerPageShown() {
        setStatusBarGlassEnabled(false)
    }

    /**
     * v1.43：明细页的滚动偏移由 Compose 侧上报（像素，>= 0）。
     *
     * 页面是 Compose 之后，基类那套 View 滚动探测抓不到 LazyColumn，
     * 所以底栏显隐改走这条链。判据与原来一致（见 [updateBarReveal]）。
     *
     * v1.64：不止明细页了 —— 统计 / 待确认 / 设置三页的滚动同样全在 Compose 里，
     * View 树里一个滚动容器都没有，同样抓不到，所以统一由页面自己上报。
     * 名字也从 onLedgerScroll 泛化成 onPageScroll。
     */
    fun onPageScroll(offsetPx: Float) {
        updateBarReveal(offsetPx.toInt())
    }

    /** 底栏按页面上报的滚动方向收起、恢复；同方向累积到阈值后再切换。 */
    private fun updateBarReveal(offset: Int) {
        val delta = offset - lastScrollOffset
        lastScrollOffset = offset

        // 停在顶部一律恢复显示：这是「滚回顶部却找不到导航」的最后一道保险。
        if (offset <= barTopSlopPx) {
            barScrollAccum = 0
            binding.glassBottomBar.revealProgress = 1f
            return
        }

        if (delta == 0) return

        // 换方向就清零，避免「滚下去一点、又滚上来一点」互相抵消掉。
        if ((delta > 0 && barScrollAccum < 0) || (delta < 0 && barScrollAccum > 0)) {
            barScrollAccum = 0
        }
        barScrollAccum += delta

        // 迟隐快现：向下要滚够多才收起，向上滚一点就立刻回来 ——
        // 收起是「让位给内容」，恢复是「用户要用导航了」，后者必须更灵敏。
        when {
            barScrollAccum >= barHideThresholdPx -> binding.glassBottomBar.revealProgress = 0f
            barScrollAccum <= -barShowThresholdPx -> binding.glassBottomBar.revealProgress = 1f
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    /** 待确认数量显示为角标。 */
    private fun observePendingBadge() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appContainer.pendingRepo.observePendingCount().collectLatest { count ->
                    binding.glassBottomBar.pendingBadgeCount = count
                }
            }
        }
    }

    /** 切换页签。已存在则复用，避免丢失滚动位置。 */
    private fun showTab(tabId: Int) {
        // 动画要在事务之前先把「出门的是哪一页」记下来 —— 事务一执行，它的 View 就 GONE 了。
        val outgoingView = visiblePageView(tabId)
        currentTab = tabId
        // 明细与待确认由页面录制内容并绘制顶部玻璃，避免叠加 View 采样层。
        setStatusBarGlassEnabled(tabId != R.id.tab_ledger && tabId != R.id.tab_pending)
        val tag = tagFor(tabId)
        val manager = supportFragmentManager
        val transaction = manager.beginTransaction()

        manager.fragments.forEach { fragment ->
            if (fragment.tag != tag && !fragment.isHidden) {
                transaction.hide(fragment)
            }
        }

        val existing = manager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.fragmentContainer, createFragment(tabId), tag)
        } else {
            transaction.show(existing)
        }
        transaction.commit()

        binding.glassBottomBar.selectedTabIndex = tabIndexFor(tabId)

        // 新页面的滚动位置和上一页无关：先同步基准偏移、复位方向累加器并把底栏收回显示态，
        // 否则会把上一页的滚动状态带到新页面（例如在 A 页下滚收起了底栏，切到 B 页时
        // 底栏还收着、页面明明停在顶部却看不到导航），随后按新页面自己的偏移重算一次强度。
        binding.root.post {
            val incoming = manager.findFragmentByTag(tag)
            // ★ v1.64：Compose 页面的滚动在 View 树里查不到，基准必须问页面自己拿。
            //   不然会踩这个坑：切到一页「已经滚下去 800px」的页面后往回滚，
            //   delta = 790 - 0 = +790 ⇒ **往上滚反而把底栏收起来**。
            lastScrollOffset = (incoming as? ScrollOffsetPage)
                ?.currentScrollOffsetPx()?.toInt()
                ?: 0
            barScrollAccum = 0
            binding.glassBottomBar.revealProgress = 1f
        }

        // 翻页动画放在 post 里跑（等事务执行完、新页面的 View 已经挂上树）。
        // 首次开应用没有「出门的页面」，不做动画，避免冷启动多一次无意义的淡入。
        binding.root.post { runPageFlip(outgoingView, manager.findFragmentByTag(tag)?.view) }
    }

    /**
     * 当前可见的页签页面。传 [exceptTabId] 时跳过它（用来取「即将被换掉的那一页」）。
     *
     * 只认 `tab_` 前缀的 tag —— 容器里理论上只放页签 Fragment，写严一点免得以后
     * 加别的东西时被误伤。
     */
    private fun visiblePageView(exceptTabId: Int? = null): View? {
        val exceptTag = exceptTabId?.let { tagFor(it) }
        return supportFragmentManager.fragments.firstOrNull { fragment ->
            !fragment.isHidden && fragment.tag != exceptTag && fragment.tag?.startsWith(TAG_PREFIX) == true
        }?.view
    }

    /**
     * 执行一次页签翻页动画，参数见 [com.jizhang.app.ui.common.PageFlip]。
     *
     * 注意 [com.jizhang.app.ui.common.PageFlip.exit] 的收尾要把页面真正设为 GONE ——
     * `transaction.hide()` 早在 post 之前就把可见性改掉了，这里为了让它能演出场，
     * 先把它抬回 VISIBLE，演完再按「它现在是不是还是隐藏的」决定要不要收回去：
     * 用户在 260ms 内又切回来时，[supportFragmentManager] 里它已经不再 hidden，
     * 这时绝不能再设 GONE，否则就把正在显示的页面抹黑了。
     */
    private fun runPageFlip(outgoing: View?, incoming: View?) {
        flippingViews.forEach { PageFlip.reset(it) }
        flippingViews.clear()
        // 先把所有页面拉回「与 Fragment 隐藏状态一致」的干净状态。
        // 这一步是必须的：动画被 cancel 时 `withEndAction` **不会**回调
        // （ViewPropertyAnimator 只在自然跑完时收尾），于是上一页会漏在 VISIBLE 状态，
        // 连点时它就一直盖在新页面上。与其指望终态回调，不如每次开场先对齐真值。
        syncPageVisibility()
        if (incoming == null || outgoing == null || outgoing === incoming) return

        outgoing.visibility = View.VISIBLE
        // 新页面压在上层：它是从「更近的一层」回来，旧页面往后退。
        // FrameLayout 的子 View 顺序取决于 add 的先后，来回切过几次之后并不稳定，所以显式抬一次。
        incoming.bringToFront()

        flippingViews += incoming
        flippingViews += outgoing
        PageFlip.enter(incoming)
        // 收尾只处理出门的那一页：此刻进场动画还没走完（220ms vs 260ms），
        // 在这里跑全量同步会把还在演的新页面一起清掉，最后一帧直接跳变。
        PageFlip.exit(outgoing) {
            val stillHidden = supportFragmentManager.fragments.any { it.view === outgoing && it.isHidden }
            if (stillHidden) outgoing.visibility = View.GONE
            flippingViews.remove(outgoing)
        }
    }

    /** 把所有页签页面的可见性对齐到 Fragment 的 hidden 状态，并清掉残留的动画属性。 */
    private fun syncPageVisibility() {
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment.tag?.startsWith(TAG_PREFIX) != true) return@forEach
            val view = fragment.view ?: return@forEach
            val expected = if (fragment.isHidden) View.GONE else View.VISIBLE
            if (view.visibility != expected) view.visibility = expected
            PageFlip.reset(view)
        }
    }

    private fun tagFor(tabId: Int): String = "$TAG_PREFIX$tabId"

    private fun createFragment(tabId: Int): Fragment = when (tabId) {
        R.id.tab_stats -> StatsFragment()
        R.id.tab_pending -> PendingFragment()
        R.id.tab_settings -> SettingsFragment()
        else -> LedgerFragment()
    }

    private fun tabIndexFor(tabId: Int): Int = when (tabId) {
        R.id.tab_stats -> 1
        R.id.tab_pending -> 2
        R.id.tab_settings -> 3
        else -> 0
    }

    private fun tabIdFor(index: Int): Int = when (index) {
        1 -> R.id.tab_stats
        2 -> R.id.tab_pending
        3 -> R.id.tab_settings
        else -> R.id.tab_ledger
    }

    private companion object {
        const val KEY_TAB = "current_tab"

        /** 页签 Fragment 的 tag 前缀，[visiblePageView] 靠它认页面。 */
        const val TAG_PREFIX = "tab_"

        /** 向下滚这么多（dp）收起底栏。 */
        const val BAR_HIDE_THRESHOLD_DP = 48f

        /** 向上滚这么多（dp）恢复底栏。比收起阈值小 = 恢复更灵敏。 */
        const val BAR_SHOW_THRESHOLD_DP = 20f

        /** 距顶部这个范围内一律视为「停在顶部」，强制显示底栏。 */
        const val BAR_TOP_SLOP_DP = 8f
    }
}
