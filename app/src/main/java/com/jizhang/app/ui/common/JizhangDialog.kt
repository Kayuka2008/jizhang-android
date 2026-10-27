package com.jizhang.app.ui.common

import top.yukonga.miuix.kmp.basic.TextFieldDefaults

import androidx.compose.ui.graphics.Color
import com.jizhang.app.ui.glass.liquidSurface
import com.jizhang.app.ui.glass.LiquidSurfaceRole


import com.jizhang.app.ui.glass.ProvideWindowLiquidMaterial

import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import com.jizhang.app.ui.glass.LiquidTextButton as TextButton
import top.yukonga.miuix.kmp.basic.TextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.material.R as MaterialR
import com.jizhang.app.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 统一对话框（v1.56）。
 *
 * ## 为什么要有这一层
 *
 * 在此之前全项目有 20 多条 `MaterialAlertDialogBuilder`：确认框、单选列表、输入框、
 * 自定义表单四种形态，每处都重写一遍命令式代码，观感是 Material 而不是 miuix ——
 * 和已经 Compose 化的筛选面板、记账键盘不是一套皮。
 *
 * 收敛方式：**一个 spec + 一个 Host**。
 *
 *   · [JizhangDialogHost] 是 View 侧（Activity / Fragment）的命令式入口，
 *     `dialogs.confirm(...)` / `dialogs.pick(...)` 这样调用，不用碰 Compose；
 *   · [JizhangDialogHostView] 挂在页面**已有的** Compose 树里负责真正渲染；
 *   · 底层用 [CenteredLiquidDialog]（真 Dialog 窗口，遮罩能盖住 Activity 级
 *     悬浮底栏，筛选面板的 LiquidBottomSheet 也使用独立窗口）。
 *
 * ## 为什么不能只是「把 MaterialAlertDialogBuilder 换个函数名」
 *
 * 调用点全在 View 侧的命令式代码里（onClick、协程回调、递归函数），而 Compose
 * 对话框必须挂在组合树上。所以必须有「View → Compose」的桥，[JizhangDialogHost]
 * 就是这个桥 —— 它只是个 state holder，既不是 View 也不是 Fragment。
 *
 * ## 一个页面一个 Host
 *
 * 不要把 Host 做成全局单例：二级页 finish 之后，全局单例持有的 spec 会尝试
 * 在已经销毁的 Activity 上渲染，或者串到下一个页面。每个页面 `private val dialogs
 * = JizhangDialogHost()`，挂进它自己的组合里。
 *
 * ## 退场动画
 *
 * [CenteredLiquidDialog] 靠 `show = false` 播关闭动画。如果在 spec 变 null 的瞬间就把
 * 整棵组合撤掉，窗口会直接销毁、没有动画。所以用 [SideEffect] 留住最后一份非空
 * spec，等 `onDismissFinished` 回调再释放。
 */

/** 列表选择项。[dotColorRes] 非 null 时在文字前画一个实心圆点（配色选择器用）。 */
internal data class DialogOption(val label: String, val dotColorRes: Int? = null)

/** 对话框规格：View 侧命令式构造，交给 [JizhangDialogHostView] 渲染。 */
internal sealed interface DialogSpec {

    val title: String?

    /** 确认框：一段正文 + 最多两个按钮。 */
    class Alert(
        override val title: String?,
        val message: String,
        val confirmText: String,
        val onConfirm: () -> Unit,
        val cancelText: String? = null,
        val onCancel: (() -> Unit)? = null,
        /** 正文下方的补充内容（如恢复出厂设置里那串红色的「将被清空」清单）。 */
        val extra: (@Composable () -> Unit)? = null,
    ) : DialogSpec

    /** 单选列表。 */
    class Pick(
        override val title: String?,
        val options: List<DialogOption>,
        val selectedIndex: Int,
        val cancelText: String? = null,
        val onCancel: (() -> Unit)? = null,
        val onPick: (Int) -> Unit,
    ) : DialogSpec

    /** 单行 / 多行输入框。 */
    class Input(
        override val title: String?,
        val message: String?,
        val initial: String,
        val confirmText: String,
        val onConfirm: (String) -> Unit,
        val cancelText: String? = null,
        val numeric: Boolean = false,
        val maxLines: Int = 1,
        /** false = 点外部/返回键不关闭（导入密码框那种必须先做选择的场景）。 */
        val cancelable: Boolean = true,
    ) : DialogSpec

    /** 任意 Compose 内容（编辑器表单、emoji 网格）。 */
    class Custom(
        override val title: String?,
        val body: @Composable (dismiss: () -> Unit) -> Unit,
    ) : DialogSpec
}

/**
 * 对话框宿主：View 侧的命令式入口。
 *
 * 生命周期跟着持有它的 Activity / Fragment 走 —— 页面销毁时整个 Host 一起被回收，
 * 不会残留窗口。
 */
@Stable
internal class JizhangDialogHost {

    /*
     * ★ 对话框是**栈**，不是单个槽位（v1.56）。
     *
     * 有些流程要在对话框之上再叠一层：账户编辑器里的「账户类型」单选、分类编辑器
     * 里的 emoji 网格。旧版靠 MaterialAlertDialog 天然可以同时存在两个窗口，而本
     * 实现只有一个对话框窗口，所以用栈来表达 —— 只有栈顶被渲染，栈顶弹出
     * 后下面那层自动重新出现。
     *
     * ★ 配套要求：下层表单的输入内容必须**提升到 View 侧**保存，不能留在 Compose
     *   的 remember 里 —— 上层压住时下层会退出组合，remember 会被清掉。
     */
    private var stack by mutableStateOf<List<DialogSpec>>(emptyList())

    /** 当前渲染的对话框（栈顶）。 */
    internal val current: DialogSpec? get() = stack.lastOrNull()

    /*
     * ★ 每个方法都把「回调」放在参数表最后：这样调用处才能写成尾随 lambda。
     *   Kotlin 的尾随 lambda 绑定的是**参数表最后一位**，若回调夹在中间，
     *   lambda 会绑到别的函数参数上去（[confirm] 的 extra 就是个陷阱）。
     */

    /** 确认框。[cancelText] 为 null 时只显示一个按钮。 */
    fun confirm(
        message: String,
        confirmText: String,
        title: String? = null,
        cancelText: String? = null,
        onCancel: (() -> Unit)? = null,
        extra: (@Composable () -> Unit)? = null,
        onConfirm: () -> Unit,
    ) {
        stack = stack + DialogSpec.Alert(
            title, message, confirmText, onConfirm, cancelText, onCancel, extra,
        )
    }

    /** 单按钮提示框（「知道了」）。 */
    fun alert(title: String, message: String, doneText: String, onDone: () -> Unit = {}) {
        stack = stack + DialogSpec.Alert(title, message, doneText, onDone)
    }

    /** 单选列表（带可选圆点）。 */
    fun pick(
        title: String?,
        options: List<DialogOption>,
        selectedIndex: Int = -1,
        cancelText: String? = null,
        onCancel: (() -> Unit)? = null,
        onPick: (Int) -> Unit,
    ) {
        stack = stack + DialogSpec.Pick(title, options, selectedIndex, cancelText, onCancel, onPick)
    }

    /** 单选列表的便捷版：只有纯文本标签。 */
    fun pickLabels(
        title: String?,
        labels: List<String>,
        selectedIndex: Int = -1,
        cancelText: String? = null,
        onCancel: (() -> Unit)? = null,
        onPick: (Int) -> Unit,
    ) = pick(title, labels.map { DialogOption(it) }, selectedIndex, cancelText, onCancel, onPick)

    fun input(
        title: String?,
        initial: String,
        confirmText: String,
        message: String? = null,
        cancelText: String? = null,
        numeric: Boolean = false,
        maxLines: Int = 1,
        cancelable: Boolean = true,
        onConfirm: (String) -> Unit,
    ) {
        stack = stack + DialogSpec.Input(
            title, message, initial, confirmText, onConfirm, cancelText, numeric, maxLines, cancelable,
        )
    }

    fun custom(title: String? = null, body: @Composable (dismiss: () -> Unit) -> Unit) {
        stack = stack + DialogSpec.Custom(title, body)
    }

    /** 关闭栈顶对话框；下层的（如果有）会重新出现。 */
    fun dismiss() {
        stack = stack.dropLast(1)
    }
}

/**
 * 把 [host] 的当前对话框渲染出来。
 *
 * 挂在页面自己的组合树里即可，位置无所谓（真 Dialog 窗口不受父布局约束），
 * 但必须和页面在同一个组合中。
 */
@Composable
internal fun JizhangDialogHostView(host: JizhangDialogHost) {
    val live = host.current
    // 留住最后一份非空 spec —— 见文件头「退场动画」。
    val kept = remember { mutableStateOf<DialogSpec?>(null) }
    SideEffect { if (live != null) kept.value = live }
    val spec = live ?: kept.value ?: return
    val shown = live != null

    ProvideWindowLiquidMaterial(blurVisible = shown) {
        CenteredLiquidDialog(
            show = shown,
            title = spec.title,
            // 点遮罩 / 返回键：cancelable=false 的输入框不接受这种关闭方式。
            onDismissRequest = {
                val top = host.current
                if (top !is DialogSpec.Input || top.cancelable) host.dismiss()
            },
            onDismissFinished = { if (!shown) kept.value = null },
        ) {
            when (spec) {
                is DialogSpec.Alert -> AlertBody(spec, host::dismiss)
                is DialogSpec.Pick -> PickBody(spec, host::dismiss)
                is DialogSpec.Input -> InputBody(spec, host::dismiss)
                is DialogSpec.Custom -> spec.body(host::dismiss)
            }
        }
    }
}

@Composable
private fun AlertBody(spec: DialogSpec.Alert, dismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = spec.message,
            style = MiuixTheme.textStyles.body2,
            color = themeColor(ATTR_ON_SURFACE_VARIANT),
        )
        val extra = spec.extra
        if (extra != null) extra()
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val cancelText = spec.cancelText
            if (cancelText != null) {
                DialogButton(
                    label = cancelText,
                    primary = false,
                    onClick = {
                        dismiss()
                        spec.onCancel?.invoke()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            DialogButton(
                label = spec.confirmText,
                primary = true,
                onClick = {
                    dismiss()
                    spec.onConfirm()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PickBody(spec: DialogSpec.Pick, dismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScrollWithBoundaryHaptics(),
        ) {
            spec.options.forEachIndexed { index, option ->
                val selected = index == spec.selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .selectable(selected = selected, role = Role.RadioButton) {
                            dismiss()
                            spec.onPick(index)
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val dot = option.dotColorRes
                    if (dot != null) {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(colorResource(dot)),
                        )
                    }
                    Text(
                        text = option.label,
                        style = MiuixTheme.textStyles.body1,
                        color = if (selected) themeColor(ATTR_PRIMARY) else themeColor(ATTR_ON_SURFACE),
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    RadioButton(selected = selected, onClick = null)
                }
            }
        }
        val cancelText = spec.cancelText
        if (cancelText != null) {
            Spacer(Modifier.height(12.dp))
            DialogButton(
                label = cancelText,
                primary = false,
                onClick = {
                    dismiss()
                    spec.onCancel?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InputBody(spec: DialogSpec.Input, dismiss: () -> Unit) {
    var text by remember(spec) { mutableStateOf(spec.initial) }

    Column(modifier = Modifier.fillMaxWidth()) {
        val message = spec.message
        if (message != null) {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = themeColor(ATTR_ON_SURFACE_VARIANT),
            )
            Spacer(Modifier.height(12.dp))
        }
        DialogTextField(
            label = null,
            value = text,
            onValueChange = { text = it },
            keyboard = if (spec.numeric) KeyboardType.Number else KeyboardType.Text,
            maxLines = spec.maxLines,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val cancelText = spec.cancelText
            if (cancelText != null) {
                DialogButton(
                    label = cancelText,
                    primary = false,
                    onClick = dismiss,
                    modifier = Modifier.weight(1f),
                )
            }
            DialogButton(
                label = spec.confirmText,
                primary = true,
                onClick = {
                    dismiss()
                    spec.onConfirm(text)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 对话框里的输入框（带可选标签）。
 *
 * 与 [InputBody] 共用官方 miuix TextField，自定义表单
 * （账户 / 分类编辑器）复用它，保证全项目输入区只有一种外观。
 *
 * ★ [label] 为 null 时不画标签 —— [DialogSpec.Input] 的标题已经由对话框本身承担。
 */
@Composable
internal fun DialogTextField(
    label: String?, value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text, maxLines: Int = 1,
) {
    Column(modifier) {
        if (label != null) {
            Text(text = label, style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        TextField(
            colors = TextFieldDefaults.textFieldColors(backgroundColor = androidx.compose.ui.graphics.Color.Transparent),
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth().liquidSurface(RoundedCornerShape(16.dp), role = LiquidSurfaceRole.Field),
            singleLine = maxLines <= 1, maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = MiuixTheme.textStyles.body1,
        )
    }
}

/** 对话框和筛选面板共用官方 miuix 按钮，保留确认触感。 */
@Composable
internal fun DialogButton(
    label: String, primary: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    val context = LocalContext.current
    TextButton(
        text = label, modifier = modifier, enabled = enabled, minHeight = 48.dp,
        onClick = { if (primary) context.hapticConfirm(); onClick() },
        colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
    )
}
