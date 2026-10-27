package com.jizhang.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.util.CrashLog
import com.jizhang.app.util.SafeMode
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 崩溃日志查看页（v1.43.1 加的；v1.60 改成 Compose + miuix）。
 *
 * 原来用的是「平台主题 + 纯代码 LinearLayout」，连 layout XML 都不 inflate ——
 * 因为它的职责是：**应用可能一启动就崩**，这个页自身依赖越少越好。
 *
 * 改成 miuix 之后这条底线仍然守着：
 *
 * ★ **不套项目的 `JizhangMiuixTheme`**：那套要读 SettingsStore（配色方案等）。
 *   这里只用 miuix 的默认主题 —— 观感是 miuix 的，但不依赖任何项目状态。
 * ★ Activity 只继承 [ComponentActivity]（单纯为了给 Compose 提供 LifecycleOwner），
 *   不碰 AppCompat，也不读项目主题属性；Manifest 里仍然是平台主题。
 * ★ 启动时机不变：见 `MainActivity.onCreate` —— 只要 `CrashLog.hasPending()` 为真，
 *   就先跳到这里，而不是走正常主页流程（否则又崩一次，用户永远看不到日志）。
 */
class CrashReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = CrashLog.read(this)
        setContent {
            MiuixTheme {
                CrashReportScreen(
                    text = text,
                    onShare = {
                        startActivity(
                            Intent.createChooser(
                                CrashLog.shareIntent(this, text),
                                "发送崩溃日志",
                            ),
                        )
                    },
                    onClear = {
                        CrashLog.clear(this)
                        goMain()
                    },
                    onSafeMode = {
                        CrashLog.clear(this)
                        SafeMode.setLedgerGlassEnabled(this, false)
                        goMain()
                    },
                )
            }
        }
    }

    private fun goMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}

@Composable
private fun CrashReportScreen(
    text: String,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onSafeMode: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Text(
            text = "记账本上次崩溃了",
            style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Medium),
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = "把下面的内容复制发给我，我照着改。\n（点「复制」后到微信里长按粘贴即可）",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // 日志区：吃掉剩余高度，可滚动、可选中复制。
        // ★ weight 必须落在 Box 上、滚动挂在里面那个 Text 上 —— 直接把
        //   weight(1f)+verticalScroll 串在同一个 modifier 上会拿到无穷大高度约束
        //   （IllegalStateException: ... infinity maximum height constraints），
        //   页面一启动就崩，然后又记一条崩溃日志，进死循环。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        CrashActionButton(text = "复制 / 发送", onClick = onShare)
        Spacer(Modifier.height(8.dp))
        CrashActionButton(text = "清除并继续（下次崩溃会再记录）", onClick = onClear)
        Spacer(Modifier.height(8.dp))
        CrashActionButton(text = "清除并用安全模式继续（关掉主页顶部玻璃）", onClick = onSafeMode)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CrashActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = text)
    }
}
