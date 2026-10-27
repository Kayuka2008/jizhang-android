package com.jizhang.app.ui.common

/** Compose pages draw edge to edge; only their controls and content receive top insets. */
abstract class SecondaryActivity : ThemedActivity() {
    final override val useSecondaryTransitions = true
    final override val statusBarTopInset = false
    final override val composeStatusBarGlass = true
}
