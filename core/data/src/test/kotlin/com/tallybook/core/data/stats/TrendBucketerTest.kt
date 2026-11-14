package com.tallybook.core.data.stats

import com.tallybook.core.common.time.JavaTimeDateProvider
import com.tallybook.core.model.stats.TimeGranularity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Bucketing tests.
 *
 * These replace an earlier `BucketKeyConsistencyTest` that was worse than useless: it asserted the
 * Kotlin week-key derivation matched *itself*, never comparing against the SQL `strftime('%W')`
 * expression it was supposed to guard. The two disagreed on 377 of 753 days, and the weekly chart
 * silently rendered zeros.
 *
 * The lesson encoded here: a consistency test must exercise BOTH sides of the seam, or it is just a
 * restatement of the implementation. Bucketing now has a single implementation (this one), so the
 * tests below pin behaviour directly — including the year-boundary cases that broke before.
 */
class TrendBucketerTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val provider = JavaTimeDateProvider(zoneId = zone)
    private val bucketer = TrendBucketer(provider)

    private fun date(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)

    private fun millis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun day(y: Int, m: Int, d: Int, expense: Long = 0, income: Long = 0) =
        DayActivity(date(y, m, d), expense, income, count = if (expense + income > 0) 1 else 0)

    /** `start until endExclusive` expressed as the inclusive-ms LongRange the repository uses. */
    private fun range(from: LocalDate, toInclusive: LocalDate): LongRange =
        millis(from) until (millis(toInclusive) + 86_400_000L)

    @Test
    fun `week buckets by the week that starts on monday`() {
        // 2025-11-03 is Monday. Activity spread Mon..Sun must collapse into ONE bucket.
        val days = listOf(
            day(2025, 11, 3, expense = 100),
            day(2025, 11, 5, expense = 200),
            day(2025, 11, 9, expense = 300), // Sunday, same week
            day(2025, 11, 10, expense = 400), // next Monday, new week
        )

        val series = bucketer.bucket(
            range(from = date(2025, 11, 3), toInclusive = date(2025, 11, 16)),
            granularity = TimeGranularity.WEEK,
            days = days,
        )

        assertEquals(2, series.points.size)
        assertEquals(600L, series.points[0].expense.minorUnits)
        assertEquals(400L, series.points[1].expense.minorUnits)
        assertEquals(millis(date(2025, 11, 3)), series.points[0].bucketStartMillis)
        assertEquals(millis(date(2025, 11, 10)), series.points[1].bucketStartMillis)
    }

    /**
     * The exact case that used to break: SQL said "2024-W53" / "2025-W00" while the JVM said
     * "2025-W01", so the week spanning the new year produced two buckets that both read zero.
     */
    @Test
    fun `a week spanning new year stays a single bucket`() {
        val days = listOf(
            day(2024, 12, 30, expense = 111), // Monday
            day(2024, 12, 31, expense = 222),
            day(2025, 1, 1, expense = 333), // Wednesday, same ISO week
            day(2025, 1, 5, expense = 444), // Sunday, same week
        )

        val series = bucketer.bucket(
            range(from = date(2024, 12, 28), toInclusive = date(2025, 1, 12)),
            granularity = TimeGranularity.WEEK,
            days = days,
        )

        val newYearWeek = series.points.first { it.bucketStartMillis == millis(date(2024, 12, 30)) }
        assertEquals("all four days collapse into the week starting Mon 2024-12-30", 1110L, newYearWeek.expense.minorUnits)
    }

    @Test
    fun `month buckets split correctly and include both directions`() {
        val days = listOf(
            day(2025, 11, 1, expense = 100, income = 5000),
            day(2025, 11, 30, expense = 200, income = 1000),
            day(2025, 12, 1, expense = 300),
        )

        val series = bucketer.bucket(
            range(from = date(2025, 11, 1), toInclusive = date(2025, 12, 31)),
            granularity = TimeGranularity.MONTH,
            days = days,
        )

        assertEquals(2, series.points.size)
        assertEquals(300L, series.points[0].expense.minorUnits)
        assertEquals(6000L, series.points[0].income.minorUnits)
        assertEquals(300L, series.points[1].expense.minorUnits)
        assertEquals("11月", series.points[0].label)
    }

    @Test
    fun `empty buckets are zero filled not dropped`() {
        // Only June has data; the series must still contain all 3 months so the chart has no gap.
        val days = listOf(day(2025, 6, 15, expense = 999))

        val series = bucketer.bucket(
            range(from = date(2025, 5, 1), toInclusive = date(2025, 7, 31)),
            granularity = TimeGranularity.MONTH,
            days = days,
        )

        assertEquals(3, series.points.size)
        val amounts = series.points.map { it.expense.minorUnits }
        assertEquals(listOf(0L, 999L, 0L), amounts)
    }

    @Test
    fun `year granularity collapses a full year into one point`() {
        val days = (1..12).map { month -> day(2025, month, 15, expense = 100) }

        val series = bucketer.bucket(
            range(from = date(2025, 1, 1), toInclusive = date(2025, 12, 31)),
            granularity = TimeGranularity.YEAR,
            days = days,
        )

        assertEquals(1, series.points.size)
        assertEquals(1200L, series.points[0].expense.minorUnits)
        assertEquals("2025", series.points[0].label)
    }

    @Test
    fun `days outside the requested range are ignored`() {
        val days = listOf(
            day(2025, 10, 31, expense = 777), // before range
            day(2025, 11, 15, expense = 100),
            day(2025, 12, 1, expense = 888), // after range
        )

        val series = bucketer.bucket(
            range(from = date(2025, 11, 1), toInclusive = date(2025, 11, 30)),
            granularity = TimeGranularity.MONTH,
            days = days,
        )

        assertEquals(1, series.points.size)
        assertEquals(100L, series.points[0].expense.minorUnits)
    }

    @Test
    fun `bucket starts are always the configured week start day`() {
        // Sweep a full year of weeks: every bucket must start on Monday, including the first partial
        // week (which must be snapped back to the Monday before the range start).
        val series = bucketer.bucket(
            range(from = date(2025, 1, 1), toInclusive = date(2025, 12, 31)),
            granularity = TimeGranularity.WEEK,
            days = emptyList(),
        )

        assertTrue(series.points.isNotEmpty())
        series.points.forEach { point ->
            val start = java.time.Instant.ofEpochMilli(point.bucketStartMillis).atZone(zone).toLocalDate()
            assertEquals("bucket start must be a Monday: $start", DayOfWeek.MONDAY, start.dayOfWeek)
        }
    }

    @Test
    fun `bucket count is bounded for absurd ranges`() {
        val series = bucketer.bucket(
            range(from = date(1900, 1, 1), toInclusive = date(2100, 12, 31)),
            granularity = TimeGranularity.MONTH,
            days = emptyList(),
        )
        assertTrue(
            "expected at most ${TrendBucketer.MAX_BUCKETS}, got ${series.points.size}",
            series.points.size <= TrendBucketer.MAX_BUCKETS,
        )
    }

    @Test
    fun `fillDaily includes zero days`() {
        val filled = bucketer.fillDaily(
            range(from = date(2025, 11, 1), toInclusive = date(2025, 11, 3)),
            days = listOf(day(2025, 11, 2, expense = 50)),
        )

        assertEquals(3, filled.size)
        assertEquals(0L, filled.getValue(date(2025, 11, 1)).expenseMinor)
        assertEquals(50L, filled.getValue(date(2025, 11, 2)).expenseMinor)
        assertEquals(0L, filled.getValue(date(2025, 11, 3)).expenseMinor)
    }

    @Test
    fun `empty range yields empty series instead of throwing`() {
        val series = bucketer.bucket(
            range = LongRange.EMPTY,
            granularity = TimeGranularity.MONTH,
            days = emptyList(),
        )
        assertTrue(series.points.isEmpty())
    }

    @Test
    fun `month end and leap day are bucketed to their own month`() {
        val days = listOf(
            day(2024, 2, 29, expense = 100), // leap day
            day(2024, 3, 1, expense = 200),
        )

        val series = bucketer.bucket(
            range(from = date(2024, 2, 1), toInclusive = date(2024, 3, 31)),
            granularity = TimeGranularity.MONTH,
            days = days,
        )

        assertEquals(2, series.points.size)
        assertEquals(100L, series.points[0].expense.minorUnits)
        assertEquals(200L, series.points[1].expense.minorUnits)
    }
}
