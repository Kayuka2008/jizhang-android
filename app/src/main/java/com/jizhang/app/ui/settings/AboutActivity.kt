package com.jizhang.app.ui.settings

import com.jizhang.app.ui.common.SecondaryPage
import com.jizhang.app.ui.common.secondaryComposeRoot
import com.jizhang.app.ui.common.rememberAdaptiveInsets

import com.jizhang.app.ui.common.verticalScrollWithBoundaryHaptics
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jizhang.app.BuildConfig
import com.jizhang.app.R
import com.jizhang.app.ui.common.JizhangDialogHost
import com.jizhang.app.ui.common.JizhangDialogHostView
import com.jizhang.app.ui.common.SecondaryActivity
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import com.jizhang.app.ui.glass.LiquidCard as Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 致谢作者对应的开源项目链接。 */
private const val URL_LIQUID_GLASS = "https://github.com/Kyant0/AndroidLiquidGlass"
private const val URL_MIUIX = "https://github.com/compose-miuix-ui/miuix"

/**
 * 「关于应用」页面（miuix 版）。
 *
 * 从设置页应用信息卡片点入，展示版本、开发者、贡献者与隐私说明。
 */
class AboutActivity : SecondaryActivity() {

    /** 统一对话框宿主（v1.56）。 */
    private val dialogs = JizhangDialogHost()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(secondaryComposeRoot {
            AboutScreen(
                onBack = ::finish,
                onContributorsClick = ::showContributorsDialog,
                onPrivacyClick = ::showPrivacyDialog,
                onOpenUrl = ::openUrl,
            )
            JizhangDialogHostView(dialogs)
        })
    }

    private fun showContributorsDialog() {
        dialogs.alert(
            title = getString(R.string.about_contributors_title),
            message = getString(R.string.settings_about_contributors),
            doneText = getString(R.string.action_done),
        )
    }

    private fun showPrivacyDialog() {
        dialogs.alert(
            title = getString(R.string.about_privacy_title),
            message = getString(R.string.settings_about_privacy),
            doneText = getString(R.string.action_done),
        )
    }

    /** 拉起系统浏览器打开链接；本应用不申请网络权限，联网由浏览器完成。 */
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // 设备上没有浏览器，静默忽略。
        }
    }
}

@Composable
private fun AboutScreen(
    onBack: () -> Unit,
    onContributorsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val bottomInset = rememberAdaptiveInsets().bottom
    SecondaryPage(title = stringResource(R.string.about_title), onBack = onBack) { topInset ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollWithBoundaryHaptics()
                .padding(bottom = bottomInset),
        ) {
            Spacer(Modifier.height(topInset))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppHeader()

                Spacer(modifier = Modifier.height(24.dp))

                SmallTitle(text = stringResource(R.string.about_group_info))
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                    ),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.about_version_label),
                        summary = BuildConfig.VERSION_NAME,
                        summaryColor = BasicComponentDefaults.summaryColor(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    )
                    BasicComponent(
                        title = stringResource(R.string.about_developer_label),
                        summary = stringResource(R.string.about_developer_value),
                        summaryColor = BasicComponentDefaults.summaryColor(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                SmallTitle(text = stringResource(R.string.about_group_more))
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                    ),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.about_contributors_title),
                        summary = stringResource(R.string.about_contributors_summary),
                        onClick = onContributorsClick,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.about_privacy_title),
                        summary = stringResource(R.string.about_privacy_summary),
                        onClick = onPrivacyClick,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 致谢作者：参照 miuix 示例「项目信息」卡片的小节行。
                    BasicComponent(
                        endActions = {
                            Text(
                                text = stringResource(R.string.about_acknowledge_type),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.about_acknowledge_label),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    AcknowledgeRow(
                        author = stringResource(R.string.about_ack_kyant0),
                        repo = stringResource(R.string.about_ack_repo_liquid_glass),
                        url = URL_LIQUID_GLASS,
                        onOpenUrl = onOpenUrl,
                    )
                    AcknowledgeRow(
                        author = stringResource(R.string.about_ack_miuix),
                        repo = stringResource(R.string.about_ack_repo_miuix),
                        url = URL_MIUIX,
                        onOpenUrl = onOpenUrl,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 致谢作者行：左侧作者名，右侧 GitHub 图标 + 开源项目名。
 *
 * 整行可点击，效果等同于点击链接 —— 拉起浏览器打开对应仓库。
 */
@Composable
private fun AcknowledgeRow(
    author: String,
    repo: String,
    url: String,
    onOpenUrl: (String) -> Unit,
) {
    BasicComponent(
        title = author,
        endActions = {
            Icon(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = repo,
                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                color = MiuixTheme.colorScheme.primary,
            )
        },
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = { onOpenUrl(url) },
    )
}

@Composable
private fun AppHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colorResource(R.color.ic_launcher_background))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground_photo),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.Medium),
            color = MiuixTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.about_tagline),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}
