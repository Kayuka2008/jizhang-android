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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.jizhang.app.ui.common.secondaryComposeRoot
import com.jizhang.app.R
import com.jizhang.app.data.db.entity.Account
import com.jizhang.app.data.db.entity.AccountType
import com.jizhang.app.data.repo.AccountBalance
import com.jizhang.app.data.repo.DeleteResult
import com.jizhang.app.money.Money
import com.jizhang.app.ui.account.AccountManageScreen
import com.jizhang.app.ui.account.AccountRow
import com.jizhang.app.ui.common.ATTR_ON_SURFACE_VARIANT
import com.jizhang.app.ui.common.DialogButton
import com.jizhang.app.ui.common.DialogTextField
import com.jizhang.app.ui.common.JizhangDialogHost
import com.jizhang.app.ui.common.JizhangDialogHostView
import com.jizhang.app.ui.common.JizhangSnackbarHost
import com.jizhang.app.ui.common.JizhangSnackbarHostView
import com.jizhang.app.ui.common.appContainer
import com.jizhang.app.ui.common.themeColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 账户管理：显示各账户余额与净资产。 */
class AccountManageActivity : com.jizhang.app.ui.common.SecondaryActivity() {

    private val netWorthText = mutableStateOf("")
    private val netWorthNegative = mutableStateOf(false)
    private val rows = mutableStateOf<List<AccountRow>>(emptyList())

    /** 统一对话框宿主（v1.56）。 */
    private val dialogs = JizhangDialogHost()

    /** 统一提示条宿主（v1.60）。 */
    private val snackbar = JizhangSnackbarHost()

    /** id → 账户实体，回调里要拿原始对象（Compose 只传行数据）。 */
    private var balances: List<AccountBalance> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = secondaryComposeRoot {
                AccountManageScreen(
                    netWorthText = netWorthText.value,
                    netWorthNegative = netWorthNegative.value,
                    accounts = rows.value,
                    onBack = { finish() },
                    onAdd = { showEditor(null) },
                    onEdit = { row ->
                        balances.firstOrNull { it.account.id == row.id }?.let { showEditor(it.account) }
                    },
                    onDelete = { row ->
                        balances.firstOrNull { it.account.id == row.id }?.let { confirmDelete(it.account) }
                    },
                )
                JizhangDialogHostView(dialogs)
                JizhangSnackbarHostView(snackbar)
        }
        setContentView(root)

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            appContainer.accountRepo.observeActive().collect { list ->
                val data = list.map { AccountBalance(it, appContainer.accountRepo.balanceOf(it)) }
                balances = data
                rows.value = data.map { b ->
                    AccountRow(
                        id = b.account.id,
                        name = b.account.name,
                        typeLabel = b.account.type.label,
                        balanceText = Money.formatCentsWithSymbol(b.cents),
                        negative = b.cents < 0,
                        builtin = b.account.builtin,
                    )
                }
                val net = data.sumOf { it.cents }
                netWorthText.value = Money.formatCentsWithSymbol(net)
                netWorthNegative.value = net < 0
            }
        }
    }

    private fun snack(message: String) {
        snackbar.show(message)
    }

    private fun showEditor(existing: Account?) {
        val types = AccountType.entries
        // ★ 表单状态提升到 Activity：点「账户类型」会往对话框栈上再压一层，
        //   主表单在那一瞬间退出组合，状态留在 Compose 的 remember 里会被清掉。
        val nameState = mutableStateOf(existing?.name.orEmpty())
        val initialState = mutableStateOf(Money.formatCents(existing?.initialCents ?: 0L))
        val typeState = mutableStateOf(existing?.type ?: AccountType.WECHAT)
        var saving = false

        dialogs.custom(
            title = getString(if (existing == null) R.string.account_add else R.string.action_edit),
        ) { dismiss ->
            AccountEditorForm(
                name = nameState.value,
                onNameChange = { nameState.value = it },
                initial = initialState.value,
                onInitialChange = { initialState.value = it },
                typeLabel = typeState.value.label,
                onPickType = {
                    dialogs.pickLabels(
                        title = getString(R.string.account_type),
                        labels = types.map { it.label },
                        selectedIndex = types.indexOf(typeState.value),
                        cancelText = getString(R.string.action_cancel),
                    ) { which ->
                        typeState.value = types[which]
                    }
                },
                onCancel = dismiss,
                onSave = save@{
                    if (saving) return@save
                    val name = nameState.value.trim()
                    if (name.isEmpty()) {
                        snack(getString(R.string.account_need_name))
                        return@save
                    }
                    val initial = Money.parseYuanToCents(initialState.value) ?: 0L
                    saving = true
                    lifecycleScope.launch {
                        try {
                            if (existing == null) {
                                val count = appContainer.accountRepo.listAll().size
                                appContainer.accountRepo.add(
                                    Account(
                                        name = name,
                                        type = typeState.value,
                                        initialCents = initial,
                                        sortOrder = count,
                                    ),
                                )
                            } else {
                                appContainer.accountRepo.update(
                                    existing.copy(
                                        name = name,
                                        type = typeState.value,
                                        initialCents = initial,
                                    ),
                                )
                            }
                            snack(getString(R.string.account_saved))
                            dismiss()
                        } finally {
                            saving = false
                        }
                    }
                },
            )
        }
    }

    private fun confirmDelete(account: Account) {
        if (account.builtin) {
            snack(getString(R.string.account_builtin_locked))
            return
        }
        dialogs.confirm(
            message = getString(R.string.account_delete_confirm, account.name),
            confirmText = getString(R.string.action_delete),
            cancelText = getString(R.string.action_cancel),
        ) {
            lifecycleScope.launch {
                when (val result = appContainer.accountRepo.delete(account)) {
                    DeleteResult.Deleted -> snack(getString(R.string.account_deleted))
                    DeleteResult.Builtin -> snack(getString(R.string.account_builtin_locked))
                    DeleteResult.NotFound -> snack(getString(R.string.delete_not_found))
                    is DeleteResult.Referenced ->
                        snack(getString(R.string.account_delete_referenced, result.count))
                }
            }
        }
    }
}

/**
 * 账户编辑表单（v1.56：原为 MaterialAlertDialog + 两个 TextInputLayout）。
 *
 * 输入内容全部由调用方持有（见 showEditor 里的注释），这里只负责画。
 */
@Composable
private fun AccountEditorForm(
    name: String,
    onNameChange: (String) -> Unit,
    initial: String,
    onInitialChange: (String) -> Unit,
    typeLabel: String,
    onPickType: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DialogTextField(
            label = stringResource(R.string.account_name),
            value = name,
            onValueChange = onNameChange,
        )
        Spacer(Modifier.height(12.dp))
        DialogTextField(
            label = stringResource(R.string.account_initial),
            value = initial,
            onValueChange = onInitialChange,
            keyboard = KeyboardType.Decimal,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.account_initial_desc),
            style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
            color = themeColor(ATTR_ON_SURFACE_VARIANT),
        )
        Spacer(Modifier.height(20.dp))
        DialogButton(
            label = typeLabel,
            primary = false,
            onClick = onPickType,
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
