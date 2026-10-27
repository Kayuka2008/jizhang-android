package com.jizhang.app.util

import android.content.Context
import android.os.Build
import com.jizhang.app.data.prefs.GlassStyle
import com.jizhang.app.data.prefs.SettingsStore

/** User appearance and crash recovery are independent switches. */
internal fun Context.glassEffectsEnabled(): Boolean =
    Build.VERSION.SDK_INT >= 33 &&
        SafeMode.ledgerGlassEnabled(this) &&
        SettingsStore(this).glassStyle != GlassStyle.OPAQUE
