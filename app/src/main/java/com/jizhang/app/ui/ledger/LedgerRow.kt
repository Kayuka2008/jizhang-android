package com.jizhang.app.ui.ledger

import com.jizhang.app.data.db.entity.Category
import com.jizhang.app.data.db.entity.Transaction
import java.time.LocalDate

/**
 * 明细列表的一行：日期分节头或一条账目。
 *
 * ★ v1.52：本文件原为 `LedgerAdapter.kt`（RecyclerView 时代的 ListAdapter）。
 *   明细页 v1.43 改成 Compose 后，Adapter 本体已无人引用，但 [LedgerRow]
 *   仍被 `LedgerScreen` 与 `LedgerViewModel` 当数据类型用 ——
 *   清理时**不能整个文件删掉**（删了 LedgerScreen 直接编译不过），
 *   于是只摘出仍在使用的类型，Adapter 部分删除。
 */
sealed interface LedgerRow {
    data class Header(val date: LocalDate, val title: String, val expense: Long, val income: Long) : LedgerRow

    data class Item(val tx: Transaction, val category: Category?) : LedgerRow
}
