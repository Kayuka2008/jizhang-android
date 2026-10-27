package com.jizhang.app.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollBoundaryTrackerTest {
    @Test fun initialAndProgrammaticScrollStaySilent() {
        assertFalse(ScrollBoundaryTracker().onScroll(20f, false, true))
    }

    @Test fun topOnlyTicksOnceUntilNextGesture() {
        val tracker = ScrollBoundaryTracker()
        tracker.startGesture()
        assertFalse(tracker.onScroll(20f, true, true))
        assertTrue(tracker.onScroll(20f, false, true))
        assertFalse(tracker.onScroll(20f, false, true))
        tracker.startGesture()
        assertTrue(tracker.onScroll(20f, false, true))
    }

    @Test fun bottomTicksDuringInertiaAndStopsAfterFling() {
        val tracker = ScrollBoundaryTracker()
        tracker.startGesture()
        assertFalse(tracker.onScroll(-20f, true, true))
        assertTrue(tracker.onScroll(-20f, true, false))
        assertFalse(tracker.onScroll(-10f, true, false))
        tracker.finishGesture()
        assertFalse(tracker.onScroll(20f, false, true))
    }

    @Test fun movingAwayFromAnEdgeDoesNotTick() {
        val tracker = ScrollBoundaryTracker()
        tracker.startGesture()
        assertFalse(tracker.onScroll(-20f, false, true))
        assertFalse(tracker.onScroll(20f, true, false))
        assertFalse(tracker.onScroll(0f, false, true))
    }

    @Test fun nonScrollableContentStaysSilent() {
        val tracker = ScrollBoundaryTracker()
        tracker.startGesture()
        assertFalse(tracker.onScroll(20f, false, false))
        assertFalse(tracker.onScroll(-20f, false, false))
    }

    @Test fun oppositeEdgesCanEachTickInOneGesture() {
        val tracker = ScrollBoundaryTracker()
        tracker.startGesture()
        assertTrue(tracker.onScroll(20f, false, true))
        assertTrue(tracker.onScroll(-20f, true, false))
    }
}
