package com.jizhang.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import top.yukonga.miuix.kmp.basic.Text
import androidx.lifecycle.lifecycleScope
import com.jizhang.app.R
import com.jizhang.app.data.prefs.ColorPalette
import com.jizhang.app.data.prefs.GlassStyle
import com.jizhang.app.data.prefs.ThemeMode
import com.jizhang.app.databinding.FragmentSettingsBinding
import com.jizhang.app.notify.NotifAccess
import com.jizhang.app.ui.AccountManageActivity
import com.jizhang.app.ui.CategoryManageActivity
import com.jizhang.app.ui.DiagnosticActivity
import com.jizhang.app.ui.MainActivity
import com.jizhang.app.ui.OnboardingActivity
import com.jizhang.app.ui.common.DialogOption
import com.jizhang.app.ui.common.JizhangDialogHost
import com.jizhang.app.ui.common.JizhangDialogHostView
import com.jizhang.app.ui.common.JizhangSnackbarHost
import com.jizhang.app.ui.common.GlassBarInsets
import com.jizhang.app.ui.common.PageScrollReporter
import com.jizhang.app.ui.common.ScrollOffsetPage
import com.jizhang.app.ui.common.appContainer
import com.jizhang.app.ui.common.expenseColor
import com.jizhang.app.ui.common.hapticKey
import com.jizhang.app.ui.export.ExportUseCase
import com.jizhang.app.ui.importbill.ImportActivity
import com.jizhang.app.util.BackgroundImageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页（miuix 版）。
 *
 * 实现 [ScrollOffsetPage]：页面的滚动发生在 Compose 里，View 树里没有滚动容器，
 * MainActivity 切页时要靠这个接口拿真实偏移来同步底栏方向判据的基准。
 */
class SettingsFragment : Fragment(), ScrollOffsetPage {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // 把状态提升到 Fragment，避免配置变化/系统授权返回后 Compose 丢失状态。
    private val notifEnabled = mutableStateOf(false)
    private val autoConfirm = mutableStateOf(false)
    private val rememberSelection = mutableStateOf(false)
    private val hapticEnabled = mutableStateOf(true)
    private val confidenceLabel = mutableStateOf("")
    private val defaultAccountName = mutableStateOf<String?>(null)
    private val themeLabel = mutableStateOf("")
    private val glassStyle = mutableStateOf(GlassStyle.FROSTED)
    private val hasCustomBackground = mutableStateOf(false)
    private val backgroundImporting = mutableStateOf(false)
    private val palettePreview = mutableStateOf<AnnotatedString?>(null)

    private val backgroundPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importBackground(uri)
    }

    /**
     * 状态栏高度。顶部留白由它交给 Compose（v1.64：不再补在 XML 根容器上，
     * 否则内容画不进状态栏那条，顶栏玻璃就无内容可糊）。
     */
    private val statusBarHeight = mutableStateOf(0.dp)

    /** 累计滚动偏移上报器（v1.64），驱动悬浮底栏的收起/恢复。 */
    private var scrollReporter: PageScrollReporter? = null

    override fun currentScrollOffsetPx(): Float = scrollReporter?.current ?: 0f

    /** 统一对话框宿主（v1.56）。 */
    private val dialogs = JizhangDialogHost()

    /** 统一提示条宿主（v1.60）。 */
    private val snackbar = JizhangSnackbarHost()

    private val glassBarHeight = mutableStateOf(0.dp)
    private var glassBarInsets: GlassBarInsets? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        refreshAllState()

        // ★ 监听挂在 Fragment 根（FrameLayout）上，**不是** ComposeView ——
        //   Compose 建组合时会装自己的 OnApplyWindowInsetsListener 顶掉这个（v1.61 血泪）。
        observeStatusBarHeight(view)
        glassBarInsets = GlassBarInsets(view) { glassBarHeight.value = it }.also { it.start() }

        // v1.64：底栏显隐的唯一数据来源。View 树里没有滚动容器（滚动在 Compose 里），
        // 所以偏移必须由页面自己上报。
        scrollReporter = PageScrollReporter { px ->
            (activity as? MainActivity)?.onPageScroll(px)
        }

        val composeView = binding.composeView
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        composeView.setContent {
            JizhangMiuixTheme {
            MiuixSettingsScreen(
                notifEnabled = notifEnabled.value,
                autoConfirm = autoConfirm.value,
                rememberSelection = rememberSelection.value,
                hapticEnabled = hapticEnabled.value,
                confidenceLabel = confidenceLabel.value,
                defaultAccountName = defaultAccountName.value,
                themeLabel = themeLabel.value,
                glassStyle = glassStyle.value,
                hasCustomBackground = hasCustomBackground.value,
                backgroundImporting = backgroundImporting.value,
                palettePreview = palettePreview.value,
                onNotifClick = { openNotifSettingsOrExplain() },
                onAutoConfirmChange = { checked ->
                    appContainer.settings.autoConfirmNotifications = checked
                    autoConfirm.value = checked
                },
                onConfidenceClick = { showConfidenceDialog() },
                onNotifAccountClick = { showDefaultAccountDialog() },
                onDiagnosticsClick = {
                    startActivity(Intent(requireContext(), DiagnosticActivity::class.java))
                },
                onImportClick = {
                    startActivity(Intent(requireContext(), ImportActivity::class.java))
                },
                onExportClick = { exportData() },
                onClearClick = { confirmFactoryReset() },
                onCategoryClick = {
                    startActivity(Intent(requireContext(), CategoryManageActivity::class.java))
                },
                onAccountClick = {
                    startActivity(Intent(requireContext(), AccountManageActivity::class.java))
                },
                onThemeClick = { showThemeDialog() },
                onPaletteClick = { showPaletteDialog() },
                onGlassStyleClick = { showGlassStyleDialog() },
                onBackgroundClick = { showBackgroundDialog() },
                onRememberChange = { checked ->
                    appContainer.settings.rememberLastSelection = checked
                    rememberSelection.value = checked
                },
                onHapticChange = { checked ->
                    appContainer.settings.hapticEnabled = checked
                    hapticEnabled.value = checked
                    // 打开那一刻立刻震一下 —— 开关本身的手感就是最好的说明。
                    if (checked) requireContext().hapticKey()
                },
                onAppInfoClick = {
                    startActivity(Intent(requireContext(), AboutActivity::class.java))
                },
                statusBarHeight = statusBarHeight.value,
                bottomInset = glassBarHeight.value,
                scrollReporter = scrollReporter,
                snackbarHost = snackbar,
            )
            JizhangDialogHostView(dialogs)
            }
        }
    }

    /** 同理 [com.jizhang.app.ui.stats.StatsFragment.observeStatusBarHeight]。 */
    private fun observeStatusBarHeight(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
            ).top
            statusBarHeight.value = Dp(top / resources.displayMetrics.density)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置/账户管理页返回后刷新。
        refreshNotifRow()
        refreshAccountRow()
    }

    private fun refreshAllState() {
        val settings = appContainer.settings
        autoConfirm.value = settings.autoConfirmNotifications
        rememberSelection.value = settings.rememberLastSelection
        hapticEnabled.value = settings.hapticEnabled
        confidenceLabel.value = confidenceLabelFor(settings.autoConfirmMinConfidence)
        themeLabel.value = settings.themeMode.label
        glassStyle.value = settings.glassStyle
        hasCustomBackground.value = settings.backgroundImageName != null
        palettePreview.value = buildPalettePreview(settings.colorPalette)
        notifEnabled.value = NotifAccess.isEnabled(requireContext())
        refreshAccountRow()
    }

    private fun refreshNotifRow() {
        notifEnabled.value = NotifAccess.syncOnResume(requireContext())
    }

    private fun refreshAccountRow() {
        lifecycleScope.launch {
            val accounts = appContainer.accountRepo.listActive()
            val id = appContainer.settings.defaultAccountId
            defaultAccountName.value = accounts.firstOrNull { it.id == id }?.name
                ?: accounts.firstOrNull()?.name
                ?: getString(R.string.empty_no_account)
        }
    }

    private fun openNotifSettingsOrExplain() {
        if (NotifAccess.isEnabled(requireContext())) {
            NotifAccess.openSettings(requireContext())
        } else {
            dialogs.confirm(
                title = getString(R.string.settings_notif_howto),
                message = getString(R.string.onboarding_step1_desc) + "\n\n" +
                    getString(R.string.onboarding_notif_warning),
                confirmText = getString(R.string.onboarding_grant_now),
                cancelText = getString(R.string.action_cancel),
            ) {
                NotifAccess.openSettings(requireContext())
            }
        }
    }

    private fun showConfidenceDialog() {
        val labels = listOf(
            getString(R.string.confidence_high),
            getString(R.string.confidence_medium),
            getString(R.string.confidence_low),
        )
        val values = listOf("HIGH", "MEDIUM", "LOW")
        val current = values.indexOf(appContainer.settings.autoConfirmMinConfidence)
            .coerceAtLeast(0)

        dialogs.pickLabels(
            title = getString(R.string.settings_auto_confidence),
            labels = labels,
            selectedIndex = current,
            cancelText = getString(R.string.action_cancel),
        ) { which ->
            appContainer.settings.autoConfirmMinConfidence = values[which]
            confidenceLabel.value = labels[which]
        }
    }

    private fun showDefaultAccountDialog() {
        lifecycleScope.launch {
            val accounts = appContainer.accountRepo.listActive()
            if (accounts.isEmpty()) {
                startActivity(Intent(requireContext(), AccountManageActivity::class.java))
                return@launch
            }
            val names = accounts.map { it.name }.toTypedArray()
            val current = accounts.indexOfFirst { it.id == appContainer.settings.defaultAccountId }
            dialogs.pickLabels(
                title = getString(R.string.settings_notif_account),
                labels = names.toList(),
                selectedIndex = current,
                cancelText = getString(R.string.action_cancel),
            ) { which ->
                appContainer.settings.defaultAccountId = accounts[which].id
                defaultAccountName.value = accounts[which].name
            }
        }
    }

    private fun exportData() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ExportUseCase.run(requireContext(), appContainer)
            }
            result.fold(
                onSuccess = { name -> snackbar.show(getString(R.string.settings_exported, name)) },
                onFailure = { e ->
                    val message = if (e is ExportUseCase.EmptyDataException) {
                        getString(R.string.settings_export_empty)
                    } else {
                        getString(R.string.settings_export_failed, e.message.orEmpty())
                    }
                    snackbar.show(message)
                },
            )
        }
    }

    private fun confirmFactoryReset() {
        if (backgroundImporting.value) return
        val checks = listOf(
            R.string.settings_reset_item_tx,
            R.string.settings_reset_item_pending,
            R.string.settings_reset_item_logs,
            R.string.settings_reset_item_rules,
            R.string.settings_reset_item_settings,
        ).joinToString("\n") { "• " + getString(it) }

        dialogs.confirm(
            title = getString(R.string.settings_reset_title),
            message = getString(R.string.settings_reset_confirm),
            confirmText = getString(R.string.settings_reset_action),
            cancelText = getString(R.string.action_cancel),
            // 旧版把「将被清空」清单做成红色 TextView 塞进 setView；
            // 这里走 Alert 的 extra 槽位，位置与颜色保持一致。
            extra = {
                Text(
                    text = checks,
                    style = TextStyle(fontSize = 14.sp, color = expenseColor()),
                    modifier = Modifier.padding(top = 16.dp),
                )
            },
        ) {
            if (backgroundImporting.value) return@confirm
            lifecycleScope.launch {
                appContainer.factoryResetRepo.reset()
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) { BackgroundImageStore.clear(appContext) }
                val intent = Intent(requireContext(), OnboardingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                requireContext().startActivity(intent)
            }
        }
    }

    private fun showThemeDialog() {
        if (backgroundImporting.value) return
        val modes = ThemeMode.entries
        val labels = modes.map { it.label }.toTypedArray()
        val current = modes.indexOf(appContainer.settings.themeMode)

        dialogs.pickLabels(
            title = getString(R.string.settings_theme),
            labels = labels.toList(),
            selectedIndex = current,
            cancelText = getString(R.string.action_cancel),
        ) { which ->
            if (backgroundImporting.value) return@pickLabels
            val picked = modes[which]
            appContainer.settings.themeMode = picked
            picked.apply()
            themeLabel.value = picked.label
        }
    }

    private fun showPaletteDialog() {
        if (backgroundImporting.value) return
        val palettes = ColorPalette.entries
        val current = palettes.indexOf(appContainer.settings.colorPalette)

        // 旧版把彩色圆点拼成 AnnotatedString 塞进 setSingleChoiceItems；
        // 现在交给 DialogOption.dotColorRes，由对话框自己画实心圆点。
        dialogs.pick(
            title = getString(R.string.settings_palette),
            options = palettes.map { DialogOption(it.label, it.dotColor) },
            selectedIndex = current,
            cancelText = getString(R.string.action_cancel),
        ) { which ->
            if (backgroundImporting.value) return@pick
            appContainer.settings.colorPalette = palettes[which]
            palettePreview.value = buildPalettePreview(palettes[which])
            requireActivity().recreate()
        }
    }

    private fun showGlassStyleDialog() {
        if (backgroundImporting.value) return
        val styles = GlassStyle.entries
        val descriptions = listOf(
            getString(R.string.settings_glass_clear_option),
            getString(R.string.settings_glass_frosted_option),
            getString(R.string.settings_glass_opaque_option),
        )
        dialogs.pickLabels(
            title = getString(R.string.settings_glass_style),
            labels = descriptions,
            selectedIndex = styles.indexOf(appContainer.settings.glassStyle),
            cancelText = getString(R.string.action_cancel),
        ) { which ->
            val selected = styles[which]
            if (selected != appContainer.settings.glassStyle) {
                appContainer.settings.glassStyle = selected
                requireActivity().recreate()
            }
        }
    }

    private fun showBackgroundDialog() {
        if (appContainer.settings.glassStyle != GlassStyle.CLEAR || backgroundImporting.value) return
        dialogs.pickLabels(
            title = getString(R.string.settings_background),
            labels = listOf(
                getString(R.string.settings_background_select),
                getString(R.string.settings_background_restore),
            ),
            cancelText = getString(R.string.action_cancel),
        ) { which ->
            if (which == 0) backgroundPicker.launch(arrayOf("image/*"))
            else restoreDefaultBackground()
        }
    }

    private fun importBackground(uri: Uri) {
        val settings = appContainer.settings
        if (settings.glassStyle != GlassStyle.CLEAR || backgroundImporting.value) return
        val sourceBinding = _binding ?: return
        val appContext = requireContext().applicationContext
        backgroundImporting.value = true
        viewLifecycleOwner.lifecycleScope.launch {
            var importedName: String? = null
            var committed = false
            try {
                val name = withContext(Dispatchers.IO) {
                    // 保存返回值以便处理 IO 已完成、主线程恢复前发生的取消。
                    BackgroundImageStore.importImage(appContext, uri).also { importedName = it }
                }
                ensureActive()
                if (_binding !== sourceBinding || !isAdded || settings.glassStyle != GlassStyle.CLEAR) return@launch
                val previous = settings.backgroundImageName
                // 与其他外观操作在主线程串行提交；取消之前不会使旧背景失效。
                settings.backgroundImageName = name
                committed = true
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { BackgroundImageStore.delete(appContext, previous) }
                }
                if (_binding === sourceBinding && isAdded) requireActivity().recreate()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (_binding === sourceBinding && isAdded) {
                    snackbar.show(getString(R.string.settings_background_failed))
                }
            } finally {
                // 只回收尚未提交的新图，已写入设置的新图不会因页面退出而被删除。
                if (!committed) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { BackgroundImageStore.delete(appContext, importedName) }
                    }
                }
                backgroundImporting.value = false
            }
        }
    }

    private fun restoreDefaultBackground() {
        val settings = appContainer.settings
        if (settings.glassStyle != GlassStyle.CLEAR || backgroundImporting.value) return
        val sourceBinding = _binding ?: return
        val appContext = requireContext().applicationContext
        backgroundImporting.value = true
        // 先在主线程切回默认；后续清理即使被取消，也不会留下指向已删文件的设置。
        settings.backgroundImageName = null
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    BackgroundImageStore.clear(appContext)
                }
                if (_binding === sourceBinding && isAdded) requireActivity().recreate()
            } finally {
                backgroundImporting.value = false
            }
        }
    }

    private fun buildPalettePreview(selected: ColorPalette): AnnotatedString = buildAnnotatedString {
        ColorPalette.entries.forEach { palette ->
            withStyle(
                SpanStyle(
                    color = Color(requireContext().getColor(palette.dotColor)),
                ),
            ) {
                append("\u25CF")
            }
        }
        append("  ${selected.label}")
    }

    private fun confidenceLabelFor(value: String): String {
        return when (value) {
            "HIGH" -> getString(R.string.confidence_high)
            "MEDIUM" -> getString(R.string.confidence_medium)
            "LOW" -> getString(R.string.confidence_low)
            else -> getString(R.string.confidence_high)
        }
    }

    override fun onDestroyView() {
        glassBarInsets?.stop()
        glassBarInsets = null
        super.onDestroyView()
        scrollReporter = null
        _binding = null
    }
}
