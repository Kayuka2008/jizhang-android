package com.jizhang.app.ui.onboarding

import com.jizhang.app.ui.common.verticalScrollWithBoundaryHaptics
import com.jizhang.app.ui.common.DialogButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.R as MaterialR
import com.jizhang.app.R
import com.jizhang.app.ui.common.ATTR_ON_SURFACE
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.HintBanner
import com.jizhang.app.ui.common.SecondaryPage
import com.jizhang.app.ui.common.rememberAdaptiveInsets
import com.jizhang.app.ui.common.TextActionButton
import com.jizhang.app.ui.common.themeColor
import com.jizhang.app.ui.glass.LiquidCard as Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首次启动引导页 —— 纯 Compose（miuix）版（v1.52 rebuild）。
 *
 * 结构照旧 XML：欢迎文案 → 三个步骤卡 → 底部开始使用 / 跳过。
 * 业务状态（通知是否授权）由 Activity 传入，避免在组合体里读系统服务。
 */
@Composable
internal fun OnboardingScreen(
    notifEnabled: Boolean,
    onGrantNotif: () -> Unit,
    onImport: () -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    val bottomInset = rememberAdaptiveInsets().bottom
    SecondaryPage { topInset ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollWithBoundaryHaptics()
                .padding(horizontal = 28.dp)
                .padding(top = topInset + 56.dp, bottom = 28.dp + bottomInset),
        ) {
            Text(
                text = "📘",
                fontSize = 56.sp,
            )

            Text(
                text = stringResource(R.string.onboarding_welcome),
                style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
                color = themeColor(ATTR_ON_SURFACE),
                modifier = Modifier.padding(top = 16.dp),
            )

            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MiuixTheme.textStyles.body1,
                color = themeColor(ATTR_ON_SURFACE_VARIANT),
                modifier = Modifier.padding(top = 6.dp),
            )

            StepCard(
                title = stringResource(R.string.onboarding_step1_title),
                desc = stringResource(R.string.onboarding_step1_desc),
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text(
                    text = stringResource(
                        if (notifEnabled) R.string.onboarding_notif_enabled else R.string.onboarding_notif_not_enabled
                    ),
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                    color = if (notifEnabled) themeColor(MaterialR.attr.colorPrimary) else themeColor(MaterialR.attr.colorError),
                    modifier = Modifier.padding(top = 8.dp),
                )

                if (!notifEnabled) {
                    OnboardingButton(
                        label = stringResource(R.string.onboarding_grant_now),
                        onClick = onGrantNotif,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                HintBanner(
                    text = stringResource(R.string.onboarding_notif_warning),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            StepCard(
                title = stringResource(R.string.onboarding_step2_title),
                desc = stringResource(R.string.onboarding_step2_desc),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                OnboardingButton(
                    label = stringResource(R.string.action_import),
                    onClick = onImport,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            StepCard(
                title = stringResource(R.string.onboarding_step3_title),
                desc = stringResource(R.string.onboarding_step3_desc),
                modifier = Modifier.padding(top = 12.dp),
            )

            Spacer(Modifier.weight(1f))

            OnboardingPrimaryButton(
                label = stringResource(R.string.onboarding_start),
                onClick = onStart,
                modifier = Modifier.padding(top = 24.dp),
            )

            TextActionButton(
                label = stringResource(R.string.onboarding_skip),
                onClick = onSkip,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(
            color = themeColor(R.attr.jizhangCardBackgroundColor),
            contentColor = themeColor(ATTR_ON_SURFACE),
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Medium),
                color = themeColor(ATTR_ON_SURFACE),
            )
            Text(
                text = desc,
                style = MiuixTheme.textStyles.body2,
                color = themeColor(ATTR_ON_SURFACE_VARIANT),
                modifier = Modifier.padding(top = 4.dp),
            )
            content()
        }
    }
}

@Composable
private fun OnboardingButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DialogButton(label, false, onClick, modifier.fillMaxWidth())
}

@Composable
private fun OnboardingPrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DialogButton(label, true, onClick, modifier.fillMaxWidth())
}
