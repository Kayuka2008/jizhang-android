package com.jizhang.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveInsetsTest {
    @Test
    fun floatingBarKeepsClearanceWhenTheNavigationHintIsHidden() {
        val page = WindowLayoutFrame(0, 0, 1080, 2400, 1080, 2400)
        val visible = SafeInsetsPx(0, 72, 0, 63)
        val hidden = visible.copy(bottom = 0)

        assertEquals(63, remainingSafeInsets(visible, page, 63).bottom)
        assertEquals(63, remainingSafeInsets(hidden, page, 63).bottom)
    }

    @Test
    fun navigationModeChangesUseCurrentSafetyWithoutRememberingTheOldBar() {
        val page = WindowLayoutFrame(0, 0, 1080, 2400, 1080, 2400)
        val actual = listOf(144, 63, 0, 144, 0).map { bottom ->
            remainingSafeInsets(SafeInsetsPx(0, 72, 0, bottom), page, 63).bottom
        }
        assertEquals(listOf(144, 63, 63, 144, 63), actual)
    }

    @Test
    fun floatingClearanceIsConsumedOnceByParentsAndIme() {
        val partial = WindowLayoutFrame(0, 0, 1080, 2376, 1080, 2400)
        val aboveIme = WindowLayoutFrame(0, 0, 1080, 1560, 1080, 2400)
        assertEquals(39, remainingSafeInsets(SafeInsetsPx(0, 72, 0, 0), partial, 63).bottom)
        assertEquals(0, remainingSafeInsets(SafeInsetsPx(0, 72, 0, 840), aboveIme, 63).bottom)
    }

    @Test
    fun edgeToEdgeContentKeepsTheCurrentNavigationModeInset() {
        val page = WindowLayoutFrame(0, 0, 1080, 2400, 1080, 2400)

        assertEquals(
            "Gesture navigation needs its own small bottom safety.",
            SafeInsetsPx(0, 72, 0, 24),
            remainingSafeInsets(SafeInsetsPx(0, 72, 0, 24), page),
        )
        assertEquals(
            "Switching to three buttons must use the larger reported inset.",
            SafeInsetsPx(0, 72, 0, 144),
            remainingSafeInsets(SafeInsetsPx(0, 72, 0, 144), page),
        )
    }

    @Test
    fun parentConsumedImeAndStatusBarAreNotAddedAgain() {
        // The parent has already placed the content below 72 px and above an 840 px IME.
        val page = WindowLayoutFrame(0, 72, 1080, 1488, 1080, 2400)

        assertEquals(
            SafeInsetsPx(0, 0, 0, 0),
            remainingSafeInsets(SafeInsetsPx(0, 72, 0, 840), page),
        )
    }

    @Test
    fun partialParentInsetsLeaveOnlyTheUnconsumedSpace() {
        // Actual parent gaps: left 30, top 12, right 50, bottom 400.
        val page = WindowLayoutFrame(30, 12, 1120, 1988, 1200, 2400)

        assertEquals(
            SafeInsetsPx(50, 20, 50, 320),
            remainingSafeInsets(SafeInsetsPx(80, 32, 100, 720), page),
        )
    }

    @Test
    fun landscapeCutoutAndSideNavigationAreConsumedIndependently() {
        // Left cutout and right three-button navigation are already outside the page.
        val page = WindowLayoutFrame(84, 48, 2220, 1032, 2400, 1080)

        assertEquals(
            SafeInsetsPx(0, 0, 0, 0),
            remainingSafeInsets(SafeInsetsPx(84, 48, 96, 0), page),
        )
    }

    @Test
    fun splitWindowUsesItsOwnRightAndBottomEdges() {
        // The physical display may be 1080 x 2400; this window is only 720 x 1000.
        val page = WindowLayoutFrame(0, 0, 720, 1000, 720, 1000)
        val safety = SafeInsetsPx(0, 24, 36, 48)

        assertEquals(safety, remainingSafeInsets(safety, page))
    }

    @Test
    fun barReservationIncludesTheRealGapBelowItsLayout() {
        val page = WindowLayoutFrame(0, 80, 1080, 2320, 1080, 2400)
        // A 220 px host ends at 2320, leaving 80 px beneath it. Its top is the obstruction.
        // Fading or translating its paint must continue to pass this stable layout frame.
        val bar = WindowLayoutFrame(14, 2100, 1052, 220, 1080, 2400)

        assertEquals(300, bottomBarOverlap(page, bar))
    }

    @Test
    fun barOverlapNeverExceedsThePageOrBecomesNegative() {
        val page = WindowLayoutFrame(0, 100, 1080, 600, 1080, 1000)
        val belowPage = WindowLayoutFrame(0, 720, 1080, 160, 1080, 1000)
        val abovePage = WindowLayoutFrame(0, 50, 1080, 180, 1080, 1000)

        assertEquals(0, bottomBarOverlap(page, belowPage))
        assertEquals(600, bottomBarOverlap(page, abovePage))
    }

    @Test
    fun imeShrunkPageReservesOnlyItsCurrentBottomBarOverlap() {
        val pageAboveIme = WindowLayoutFrame(0, 72, 1080, 1488, 1080, 2400)
        val barAboveIme = WindowLayoutFrame(0, 1340, 1080, 220, 1080, 2400)

        assertEquals(220, bottomBarOverlap(pageAboveIme, barAboveIme))
    }

    @Test
    fun keyboardAvoidingPageDoesNotReserveTheBarBehindTheKeyboard() {
        val pageAboveIme = WindowLayoutFrame(0, 0, 1080, 1560, 1080, 2400)
        val anchoredBar = WindowLayoutFrame(0, 2158, 1080, 242, 1080, 2400)

        assertEquals(0, bottomBarOverlap(pageAboveIme, anchoredBar))
        assertEquals(242, bottomBarOverlap(
            pageAboveIme.copy(height = 2400), anchoredBar,
        ))
    }
}
