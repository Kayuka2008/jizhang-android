package com.jizhang.app.widget

/** 金额单位为分，图表和统计查询共用的数据契约。 */
data class DonutSlice(val label: String, val value: Long, val color: Int)

data class BarPoint(val label: String, val expense: Long, val income: Long)
