package com.jizhang.app.ui.settings

import com.jizhang.app.ui.glass.LiquidPage
import com.jizhang.app.ui.common.PageHeading
import com.jizhang.app.ui.common.PageHeadingStart

import com.jizhang.app.ui.common.verticalScrollWithBoundaryHaptics
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jizhang.app.BuildConfig
import com.jizhang.app.ui.common.JizhangSnackbarHost
import com.jizhang.app.ui.common.JizhangSnackbarHostView
import com.jizhang.app.ui.common.PageScrollReporter
import com.jizhang.app.ui.common.ReportScrollOffset
import com.jizhang.app.R
import com.jizhang.app.data.prefs.GlassStyle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import com.jizhang.app.ui.glass.LiquidCard as Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.jizhang.app.ui.glass.LiquidSwitch

/**
 * 基于 miuix 库重写的设置页（HyperOS 风格）。
 *
 * 这里只负责 UI 结构；所有状态与点击副作用由 [SettingsFragment] 提供。
 */
@Composable
internal fun MiuixSettingsScreen(
    notifEnabled: Boolean,
    autoConfirm: Boolean,
    rememberSelection: Boolean,
    hapticEnabled: Boolean,
    confidenceLabel: String,
    defaultAccountName: String?,
    themeLabel: String,
    glassStyle: GlassStyle,
    hasCustomBackground: Boolean,
    backgroundImporting: Boolean,
    palettePreview: AnnotatedString?,
    onNotifClick: () -> Unit,
    onAutoConfirmChange: (Boolean) -> Unit,
    onConfidenceClick: () -> Unit,
    onNotifAccountClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onClearClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onAccountClick: () -> Unit,
    onThemeClick: () -> Unit,
    onPaletteClick: () -> Unit,
    onGlassStyleClick: () -> Unit,
    onBackgroundClick: () -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onHapticChange: (Boolean) -> Unit,
    onAppInfoClick: () -> Unit,
    snackbarHost: JizhangSnackbarHost,
    /**
     * 状态栏高度。顶部留白**必须**由这里给，不能补在Fragment 的 XML 根容器上 ——
     * 补在根容器上会把 ComposeView 的顶边整体推下去，内容再也画不进状态栏那条，
     * 顶栏玻璃就没有东西可糊（v1.64 修的就是这个）。
     */
    statusBarHeight: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    /** 累计滚动偏移上报（喂给 Activity 驱动底栏收起/恢复，见 [PageScrollReporter]）。 */
    scrollReporter: PageScrollReporter? = null,
    modifier: Modifier = Modifier,
) {
    JizhangMiuixTheme {
        /*
         * v1.60：外层改 Box + 内部自己滚。
         *
         * 滚动原来在外层 XML 的 ScrollView 里，那会让 ComposeView 拿到无穷高约束；
         * 提示条（SnackbarHost）内部有垂直滚动组件，一挂进来就崩。现在：
         *   · 滚动挪进 Compose（下面这列自己 verticalScroll）；
         *   · 提示条作为覆盖层放在滚动容器之外，拿到的是 fillMaxSize 的有限约束。
         */
        val scrollState = rememberScrollState()
        // v1.64：把绝对滚动位置喂给 Activity（驱动底栏收起/恢复）
        ReportScrollOffset(scrollState, scrollReporter)

        LiquidPage(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollWithBoundaryHaptics(scrollState)
                .padding(horizontal = 16.dp)
                // 状态栏留白在这里给，滚动时它会带着内容一起穿到状态栏底下 ⇒ 玻璃有东西可糊
                .padding(top = statusBarHeight + 32.dp, bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            PageHeading(
                text = stringResource(R.string.settings_title),
                modifier = Modifier.padding(start = PageHeadingStart - 16.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            AppInfoCard(
                versionName = stringResource(R.string.settings_version) + " " + BuildConfig.VERSION_NAME,
                onClick = onAppInfoClick,
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingsGroup(title = stringResource(R.string.settings_group_auto)) {
                ArrowPreference(
                    title = stringResource(R.string.settings_notif_listener),
                    summary = stringResource(R.string.settings_notif_listener_desc),
                    onClick = onNotifClick,
                    endActions = {
                        Text(
                            text = stringResource(
                                if (notifEnabled) R.string.settings_notif_listener_on
                                else R.string.settings_notif_listener_off
                            ),
                            style = MiuixTheme.textStyles.body2,
                            color = if (notifEnabled) Color(0xFF4CAF50)
                            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                )

                LiquidSwitchPreference(
                    title = stringResource(R.string.settings_auto_confirm),
                    summary = stringResource(R.string.settings_auto_confirm_desc),
                    checked = autoConfirm,
                    onCheckedChange = onAutoConfirmChange,
                )

                ArrowPreference(
                    title = stringResource(R.string.settings_auto_confidence),
                    onClick = onConfidenceClick,
                    endActions = {
                        Text(
                            text = confidenceLabel,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                )

                ArrowPreference(
                    title = stringResource(R.string.settings_notif_account),
                    onClick = onNotifAccountClick,
                    endActions = {
                        Text(
                            text = defaultAccountName
                                ?: stringResource(R.string.empty_no_account),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                )

                ArrowPreference(
                    title = stringResource(R.string.settings_diagnostics),
                    summary = stringResource(R.string.settings_diagnostics_desc),
                    onClick = onDiagnosticsClick,
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_data)) {
                ArrowPreference(
                    title = stringResource(R.string.settings_import),
                    summary = stringResource(R.string.settings_import_desc),
                    onClick = onImportClick,
                )

                ArrowPreference(
                    title = stringResource(R.string.settings_export),
                    summary = stringResource(R.string.settings_export_desc),
                    onClick = onExportClick,
                )

                ArrowPreference(
                    title = stringResource(R.string.settings_clear_data),
                    summary = stringResource(R.string.settings_clear_data_desc),
                    titleColor = BasicComponentColors(
                        color = Color(0xFFE94634),
                        disabledColor = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                    ),
                    onClick = onClearClick,
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_category)) {
                ArrowPreference(
                    title = stringResource(R.string.settings_category_manage),
                    onClick = onCategoryClick,
                )

                ArrowPreference(
                    title = stringResource(R.string.settings_account_manage),
                    onClick = onAccountClick,
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_appearance)) {
                ArrowPreference(
                    title = stringResource(R.string.settings_glass_style),
                    summary = stringResource(R.string.settings_glass_style_desc),
                    onClick = onGlassStyleClick,
                    enabled = !backgroundImporting,
                    endActions = {
                        Text(
                            text = glassStyle.label,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                )

                ArrowPreference(
                    title = stringResource(R.string.settings_background),
                    summary = stringResource(
                        when {
                            backgroundImporting -> R.string.settings_background_importing
                            glassStyle != GlassStyle.CLEAR -> R.string.settings_background_clear_only
                            hasCustomBackground -> R.string.settings_background_custom
                            else -> R.string.settings_background_default
                        },
                    ),
                    enabled = glassStyle == GlassStyle.CLEAR && !backgroundImporting,
                    onClick = onBackgroundClick,
                )

                ArrowPreference(
                    title = stringResource(R.string.settings_theme),
                    onClick = onThemeClick,
                    endActions = {
                        Text(
                            text = themeLabel,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                )

                ArrowPreference(
                    title = stringResource(R.string.settings_palette),
                    summary = stringResource(R.string.settings_palette_desc),
                    onClick = onPaletteClick,
                    bottomAction = {
                        Text(
                            text = palettePreview ?: AnnotatedString(""),
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )

                LiquidSwitchPreference(
                    title = stringResource(R.string.settings_remember),
                    summary = stringResource(R.string.settings_remember_desc),
                    checked = rememberSelection,
                    onCheckedChange = onRememberChange,
                )

                LiquidSwitchPreference(
                    title = stringResource(R.string.settings_haptic),
                    summary = stringResource(R.string.settings_haptic_desc),
                    checked = hapticEnabled,
                    onCheckedChange = onHapticChange,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        JizhangSnackbarHostView(
            host = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        }
    }
}

/**
 * Miuix preference row with the native switch replaced by [LiquidSwitch].
 * Keeping the row on BasicComponent avoids SwitchPreference rendering a second switch.
 */
@Composable
private fun LiquidSwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    BasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        endActions = {
            LiquidSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        onClick = { onCheckedChange(!checked) },
        role = Role.Switch,
        enabled = enabled,
    )
}

@Composable
private fun AppInfoCard(
    versionName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
        ),
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorResource(R.color.ic_launcher_background))
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground_photo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MiuixTheme.textStyles.headline1.copy(fontWeight = FontWeight.Medium),
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = versionName,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }

            Image(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SmallTitle(
        text = title,
        modifier = modifier.padding(top = 8.dp),
        insideMargin = PaddingValues(start = PageHeadingStart - 16.dp, top = 8.dp, bottom = 8.dp),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
        ),
        insideMargin = PaddingValues(0.dp),
    ) {
        content()
    }
}

