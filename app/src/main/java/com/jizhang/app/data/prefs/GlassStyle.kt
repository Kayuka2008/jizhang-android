package com.jizhang.app.data.prefs

/** Saved names are independent of the displayed labels and app version. */
enum class GlassStyle(val label: String) {
    CLEAR("全透"),
    FROSTED("半透"),
    OPAQUE("不透");

    companion object {
        fun fromName(name: String?): GlassStyle =
            entries.firstOrNull { it.name == name } ?: FROSTED
    }
}
