package com.jizhang.app.data.prefs

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.jizhang.app.R

/** 主题模式。 */
enum class ThemeMode(val label: String) {
    FOLLOW_SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
    ;

    fun apply() {
        AppCompatDelegate.setDefaultNightMode(
            when (this) {
                FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
    }

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: FOLLOW_SYSTEM
    }
}

/**
 * 配色方案（原 MD3 配色），独立于明暗主题。
 *
 * v1.50：界面风格（经典 / MD3）已删除，外观 token 只有基础主题一份。
 * 配色成为唯一可选的「外观项」，八套 MD3 配色与 HyperOS 默认配色**全部可用**。
 */
enum class ColorPalette(val label: String, val dotColor: Int, val themeOverlay: Int) {
    PURPLE("靛紫", R.color.pal_purple_primary, R.style.ThemeOverlay_Jizhang_Palette_Purple),
    SAND("暖沙", R.color.pal_sand_primary, R.style.ThemeOverlay_Jizhang_Palette_Sand),
    BLUE("湖蓝", R.color.pal_blue_primary, R.style.ThemeOverlay_Jizhang_Palette_Blue),
    GREEN("松绿", R.color.pal_green_primary, R.style.ThemeOverlay_Jizhang_Palette_Green),
    ROSE("绯樱", R.color.pal_rose_primary, R.style.ThemeOverlay_Jizhang_Palette_Rose),
    TEAL("青瓷", R.color.pal_teal_primary, R.style.ThemeOverlay_Jizhang_Palette_Teal),
    INDIGO("星夜", R.color.pal_indigo_primary, R.style.ThemeOverlay_Jizhang_Palette_Indigo),
    CLAY("陶土", R.color.pal_clay_primary, R.style.ThemeOverlay_Jizhang_Palette_Clay),
    HYPEROS("HyperOS", R.color.pal_hyper_primary, R.style.ThemeOverlay_Jizhang_Palette_HyperOS),
    ;

    companion object {
        fun fromName(name: String?): ColorPalette =
            entries.firstOrNull { it.name == name } ?: HYPEROS
    }
}

/**
 * 设置项。用 SharedPreferences，读写都在主线程之外调用。
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jizhang_settings", Context.MODE_PRIVATE)

    /** 是否已完成首次引导 */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** 当前配色方案。 */
    var colorPalette: ColorPalette
        get() = ColorPalette.fromName(prefs.getString(KEY_COLOR_PALETTE, null))
        set(value) {
            prefs.edit().putString(KEY_COLOR_PALETTE, value.name).apply()
        }

    /** 玻璃表面的透明程度；不认识的旧值回到半透。 */
    var glassStyle: GlassStyle
        get() = GlassStyle.fromName(prefs.getString(KEY_GLASS_STYLE, null))
        set(value) = prefs.edit().putString(KEY_GLASS_STYLE, value.name).apply()

    /** 私有 appearance 目录中的图片文件名，不保存外部 URI。 */
    var backgroundImageName: String?
        get() = prefs.getString(KEY_BACKGROUND_IMAGE, null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_BACKGROUND_IMAGE)
            else editor.putString(KEY_BACKGROUND_IMAGE, value)
            editor.apply()
        }

    /**
     * v1.50：清掉历史遗留的「界面风格」记录。
     *
     * 老版本会在 SharedPreferences 里写 `ui_style`（CLASSIC / ELEGANT / HYPEROS）。
     * 界面风格这一层已经删掉了 —— 那条记录既没有读者也没有意义，留着只会在往后
     * 某次改动里被误当成「用户选过的值」（例如拿它去决定动画或字体）。所以这里
     * 一次性删掉；配色 [colorPalette] 保留，那是用户真正选过的东西。
     *
     * 只跑一次：删完之后 `KEY_UI_STYLE` 不再存在，后续启动都不会再进这个分支。
     */
    fun clearLegacyUiStyle() {
        if (!prefs.contains(KEY_UI_STYLE)) return
        prefs.edit().remove(KEY_UI_STYLE).apply()
    }

    /** 是否开启自动入账（关：识别结果先入待确认队列） */
    var autoConfirmNotifications: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONFIRM, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CONFIRM, value).apply()

    /** 只有置信度不低于该值时才自动入账 */
    var autoConfirmMinConfidence: String
        get() = prefs.getString(KEY_AUTO_CONFIDENCE, "HIGH") ?: "HIGH"
        set(value) = prefs.edit().putString(KEY_AUTO_CONFIDENCE, value).apply()

    /** 默认记账账户 id */
    var defaultAccountId: Long
        get() = prefs.getLong(KEY_DEFAULT_ACCOUNT, 0L)
        set(value) = prefs.edit().putLong(KEY_DEFAULT_ACCOUNT, value).apply()

    /** 通知监听是否已提示过授权 */
    var notificationPromptShown: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_PROMPT, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_PROMPT, value).apply()

    /** 主题 */
    var themeMode: ThemeMode
        get() = ThemeMode.fromName(prefs.getString(KEY_THEME, null))
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** 是否在记账时默认沿用上次选择 */
    var rememberLastSelection: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER, value).apply()

    /**
     * 是否开启触感反馈（v1.57）。
     *
     * 默认开：记账键盘每次按键、以及确认/删除这类操作都会震一下。
     * 关掉后全应用静默 —— 判断统一在 `ui/common/Haptics.kt` 里做，调用处不关心。
     */
    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    var lastCategoryId: Long
        get() = prefs.getLong(KEY_LAST_CATEGORY, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CATEGORY, value).apply()

    /** 是否已导入过账单 */
    var hasImported: Boolean
        get() = prefs.getBoolean(KEY_HAS_IMPORTED, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_IMPORTED, value).apply()

    /** 上次备份时间 */
    var lastBackupAt: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP, value).apply()

    /** 工厂重置：清除所有 SharedPreferences，并保留已完成的初始化语义。 */
    fun resetForFactory() {
        // 恢复出厂后应呈现全新安装的默认观感，因此在同一次提交中显式写回默认配色。
        prefs.edit()
            .clear()
            .putBoolean(KEY_ONBOARDING, false)
            .putString(KEY_COLOR_PALETTE, ColorPalette.HYPEROS.name)
            .commit()
    }

    private companion object {
        const val KEY_ONBOARDING = "onboarding_done"
        /** 已废弃：v1.50 删掉了界面风格，保留常量只为从老版本清掉这条记录。 */
        const val KEY_UI_STYLE = "ui_style"
        const val KEY_COLOR_PALETTE = "color_palette"
        const val KEY_GLASS_STYLE = "glass_style"
        const val KEY_BACKGROUND_IMAGE = "background_image"
        const val KEY_AUTO_CONFIRM = "auto_confirm"
        const val KEY_AUTO_CONFIDENCE = "auto_confidence"
        const val KEY_DEFAULT_ACCOUNT = "default_account"
        const val KEY_NOTIF_PROMPT = "notif_prompt"
        const val KEY_THEME = "theme_mode"
        const val KEY_REMEMBER = "remember_selection"
        const val KEY_HAPTIC = "haptic_enabled"
        const val KEY_LAST_CATEGORY = "last_category"
        const val KEY_HAS_IMPORTED = "has_imported"
        const val KEY_LAST_BACKUP = "last_backup"
    }
}
