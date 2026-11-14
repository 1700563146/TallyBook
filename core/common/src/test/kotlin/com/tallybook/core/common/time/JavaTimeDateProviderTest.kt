package com.tallybook.core.common.time

import com.tallybook.core.model.stats.TimeGranularity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Calendar math tests.
 *
 * These exist because period boundaries are where bookkeeping apps quietly break: an expense at
 * 23:30 on the last day of a month must land in that month, and a "week" must start on the same day
 * for both the chart and the budget or they will disagree with each other.
 *
 * A fixed zone (Asia/Shanghai) is used so results do not depend on the CI machine's locale.
 */
class JavaTimeDateProviderTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val provider = JavaTimeDateProvider(zoneId = zone)

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `start of month is midnight on the first`() {
        val input = millisAt(2025, 11, 6, 14, 30)
        val start = provider.startOfMonth(input)
        assertEquals(millisAt(2025, 11, 1), start)
    }

    @Test
    fun `end of month is exclusive and lands on next month`() {
        val input = millisAt(2025, 11, 6)
        assertEquals(millisAt(2025, 12, 1), provider.endOfMonthExclusive(input))
    }

    @Test
    fun `december rolls over to january of next year`() {
        val input = millisAt(2025, 12, 31, 23, 59)
        assertEquals(millisAt(2026, 1, 1), provider.endOfMonthExclusive(input))
        // The late-night transaction stays in December
        assertEquals(millisAt(2025, 12, 1), provider.startOfMonth(input))
    }

    @Test
    fun `february in a non-leap year has 28 days`() {
        val input = millisAt(2025, 2, 15)
        assertEquals(millisAt(2025, 3, 1), provider.endOfMonthExclusive(input))
    }

    @Test
    fun `february in a leap year has 29 days`() {
        val input = millisAt(2024, 2, 15)
        assertEquals(millisAt(2024, 3, 1), provider.endOfMonthExclusive(input))
    }

    @Test
    fun `week starts on monday`() {
        // 2025-11-06 is a Thursday; the week must start Monday 2025-11-03.
        val input = millisAt(2025, 11, 6, 9, 0)
        assertEquals(millisAt(2025, 11, 3), provider.startOfWeek(input))
        assertEquals(millisAt(2025, 11, 10), provider.endOfWeekExclusive(input))
    }

    @Test
    fun `sunday belongs to the week that started the previous monday`() {
        // 2025-11-09 is a Sunday: it must NOT start a new week.
        val sunday = millisAt(2025, 11, 9, 22, 0)
        assertEquals(millisAt(2025, 11, 3), provider.startOfWeek(sunday))
    }

    @Test
    fun `monday at midnight is its own week start`() {
        val monday = millisAt(2025, 11, 10)
        assertEquals(monday, provider.startOfWeek(monday))
    }

    @Test
    fun `week spanning a month boundary keeps both ends in the same week range`() {
        // 2025-11-01 is a Saturday; its week began 2025-10-27 (Monday).
        val saturday = millisAt(2025, 11, 1, 12, 0)
        assertEquals(millisAt(2025, 10, 27), provider.startOfWeek(saturday))
        assertEquals(millisAt(2025, 11, 3), provider.endOfWeekExclusive(saturday))
    }

    @Test
    fun `rangeFor matches granularity`() {
        val anchor = millisAt(2025, 11, 6, 15, 0)

        val week = provider.rangeFor(TimeGranularity.WEEK, anchor)
        assertEquals(millisAt(2025, 11, 3), week.first)
        assertEquals(millisAt(2025, 11, 10) - 1, week.last)

        val month = provider.rangeFor(TimeGranularity.MONTH, anchor)
        assertEquals(millisAt(2025, 11, 1), month.first)
        assertEquals(millisAt(2025, 12, 1) - 1, month.last)

        val year = provider.rangeFor(TimeGranularity.YEAR, anchor)
        assertEquals(millisAt(2025, 1, 1), year.first)
        assertEquals(millisAt(2026, 1, 1) - 1, year.last)
    }

    @Test
    fun `daysBetweenInclusive counts both endpoints`() {
        val start = millisAt(2025, 11, 1)
        val endExclusive = millisAt(2025, 12, 1)
        assertEquals(30, provider.daysBetweenInclusive(start, endExclusive))
    }

    @Test
    fun `daysElapsed is clamped to the period`() {
        val start = millisAt(2025, 11, 1)
        val endExclusive = millisAt(2025, 12, 1)

        assertEquals(0, provider.daysElapsed(start, endExclusive, millisAt(2025, 10, 15)))
        assertEquals(1, provider.daysElapsed(start, endExclusive, start))
        assertEquals(6, provider.daysElapsed(start, endExclusive, millisAt(2025, 11, 6, 12, 0)))
        assertEquals(30, provider.daysElapsed(start, endExclusive, millisAt(2026, 1, 5)))
    }

    @Test
    fun `dst transition does not break day boundaries`() {
        // Europe/Berlin springs forward on 2025-03-30; a naive +/-86400000 ms would drift.
        val berlin = JavaTimeDateProvider(zoneId = ZoneId.of("Europe/Berlin"))
        val before = LocalDateTime.of(2025, 3, 30, 0, 0)
            .atZone(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()
        val startOfDay = berlin.startOfDay(before)
        assertEquals(before, startOfDay)
        assertTrue(berlin.endOfDayExclusive(before) > before)
    }
}
