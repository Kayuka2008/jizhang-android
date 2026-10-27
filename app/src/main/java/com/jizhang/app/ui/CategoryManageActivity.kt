package com.jizhang.app.ui

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jizhang.app.ui.common.secondaryComposeRoot
import com.jizhang.app.R
import com.jizhang.app.data.db.entity.Category
import com.jizhang.app.data.db.entity.TxKind
import com.jizhang.app.data.repo.DeleteResult
import com.jizhang.app.ui.category.CategoryManageScreen
import com.jizhang.app.ui.category.CategoryRow
import com.jizhang.app.ui.common.CategoryIcons
import com.jizhang.app.ui.common.DialogButton
import com.jizhang.app.ui.common.DialogTextField
import com.jizhang.app.ui.common.EmojiGrid
import com.jizhang.app.ui.common.JizhangDialogHost
import com.jizhang.app.ui.common.JizhangDialogHostView
import com.jizhang.app.ui.common.JizhangSnackbarHost
import com.jizhang.app.ui.common.JizhangSnackbarHostView
import com.jizhang.app.ui.common.appContainer
import kotlinx.coroutines.launch

/** 分类管理：支出/收入分组显示，可增删改。 */
class CategoryManageActivity : com.jizhang.app.ui.common.SecondaryActivity() {


    private val rows = mutableStateOf<List<CategoryRow>>(emptyList())

    /** 统一对话框宿主（v1.56）。 */
    private val dialogs = JizhangDialogHost()

    /** 统一提示条宿主（v1.60）。 */
    private val snackbar = JizhangSnackbarHost()

    /** id → 原始实体（回调里要拿整个对象，Compose 只传行数据）。 */
    private var categories: List<Category> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = secondaryComposeRoot {
                CategoryManageScreen(
                    categories = rows.value,
                    onBack = { finish() },
                    onAddExpense = { showEditor(null, TxKind.EXPENSE) },
                    onAddIncome = { showEditor(null, TxKind.INCOME) },
                    onEdit = { row -> categories.firstOrNull { it.id == row.id }?.let { showEditor(it, it.kind) } },
                    onDelete = { row -> categories.firstOrNull { it.id == row.id }?.let(::confirmDelete) },
                )
                JizhangDialogHostView(dialogs)
                JizhangSnackbarHostView(snackbar)
        }
        setContentView(root)

        lifecycleScope.launch {
            appContainer.categoryRepo.observeAll().collect { list ->
                categories = list
                rows.value = list.map { c ->
                    CategoryRow(
                        id = c.id,
                        name = c.name,
                        iconRes = CategoryIcons.of(c.name, c.emoji),
                        builtin = c.builtin,
                    )
                }
            }
        }
    }

    private fun snack(message: String) {
        snackbar.show(message)
    }

    private fun showEditor(existing: Category?, kind: TxKind) {
        // ★ 表单状态提升到 Activity：点「图标」会往对话框栈上再压一层 emoji 网格，
        //   主表单在那一瞬间退出组合，状态留在 Compose 的 remember 里会被清掉。
        val emojiState = mutableStateOf(existing?.emoji ?: "\uD83D\uDCE6")
        val nameState = mutableStateOf(existing?.name.orEmpty())
        var saving = false

        dialogs.custom(
            title = getString(if (existing == null) R.string.category_add else R.string.action_edit),
        ) { dismiss ->
            CategoryEditorForm(
                name = nameState.value,
                onNameChange = { nameState.value = it },
                emoji = emojiState.value,
                onPickEmoji = {
                    dialogs.custom(title = getString(R.string.category_emoji)) { dismissEmoji ->
                        EmojiGrid(
                            selected = emojiState.value,
                            onPick = { picked ->
                                emojiState.value = picked
                                dismissEmoji()
                            },
                        )
                    }
                },
                onCancel = dismiss,
                onSave = save@{
                    if (saving) return@save
                    val name = nameState.value.trim()
                    if (name.isEmpty()) {
                        snack(getString(R.string.category_need_name))
                        return@save
                    }
                    saving = true
                    lifecycleScope.launch {
                        try {
                            val duplicate = appContainer.categoryRepo.findByName(name, kind)
                            if (duplicate != null && duplicate.id != existing?.id) {
                                snack(getString(R.string.category_name_exists))
                                return@launch
                            }
                            if (existing == null) {
                                val count = appContainer.categoryRepo.listByKind(kind).size
                                appContainer.categoryRepo.add(
                                    Category(
                                        name = name,
                                        emoji = emojiState.value,
                                        kind = kind,
                                        sortOrder = count,
                                    ),
                                )
                            } else {
                                appContainer.categoryRepo.update(
                                    existing.copy(name = name, emoji = emojiState.value),
                                )
                            }
                            snack(getString(R.string.category_saved))
                            dismiss()
                        } finally {
                            saving = false
                        }
                    }
                },
            )
        }
    }

    private fun confirmDelete(category: Category) {
        if (category.builtin) {
            snack(getString(R.string.category_builtin_locked))
            return
        }
        dialogs.confirm(
            message = getString(R.string.category_delete_confirm, category.name),
            confirmText = getString(R.string.action_delete),
            cancelText = getString(R.string.action_cancel),
        ) {
            lifecycleScope.launch {
                when (val result = appContainer.categoryRepo.delete(category)) {
                    DeleteResult.Deleted -> snack(getString(R.string.category_deleted))
                    DeleteResult.Builtin -> snack(getString(R.string.category_builtin_locked))
                    DeleteResult.NotFound -> snack(getString(R.string.delete_not_found))
                    is DeleteResult.Referenced ->
                        snack(getString(R.string.category_delete_referenced, result.count))
                }
            }
        }
    }
}

/**
 * 分类编辑表单（v1.56：原为 MaterialAlertDialog + TextInputLayout + EmojiPicker）。
 *
 * 输入内容全部由调用方持有（见 showEditor 里的注释），这里只负责画。
 */
@Composable
private fun CategoryEditorForm(
    name: String,
    onNameChange: (String) -> Unit,
    emoji: String,
    onPickEmoji: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DialogTextField(
            label = stringResource(R.string.category_name),
            value = name,
            onValueChange = onNameChange,
        )
        Spacer(Modifier.height(20.dp))
        DialogButton(
            label = stringResource(R.string.category_emoji) + "  " + emoji,
            primary = false,
            onClick = onPickEmoji,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DialogButton(
                label = stringResource(R.string.action_cancel),
                primary = false,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            DialogButton(
                label = stringResource(R.string.action_save),
                primary = true,
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
