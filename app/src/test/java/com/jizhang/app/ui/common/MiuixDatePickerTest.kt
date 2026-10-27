package com.jizhang.app.ui.common

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MiuixDatePickerTest {
    @Test fun january31MovesToLeapFebruary29() {
        assertEquals(LocalDate.of(2024, 2, 29), adjustedDate(LocalDate.of(2024, 1, 31), month = 2))
    }

    @Test fun leapDayMovesToFebruary28InCommonYear() {
        assertEquals(LocalDate.of(2023, 2, 28), adjustedDate(LocalDate.of(2024, 2, 29), year = 2023))
    }

    @Test fun centuryLeapRuleIsRespected() {
        assertEquals(LocalDate.of(1900, 2, 28), adjustedDate(LocalDate.of(2000, 2, 29), year = 1900))
        assertEquals(LocalDate.of(2000, 2, 29), adjustedDate(LocalDate.of(2000, 1, 31), month = 2))
    }

    @Test fun shorterMonthsDoNotOverflowIntoFollowingMonth() {
        assertEquals(LocalDate.of(2026, 4, 30), adjustedDate(LocalDate.of(2026, 3, 31), month = 4))
    }

    @Test fun datesThatAlreadyFitKeepTheirDay() {
        assertEquals(LocalDate.of(2025, 12, 17), adjustedDate(LocalDate.of(2026, 10, 17), year = 2025, month = 12))
    }
}
