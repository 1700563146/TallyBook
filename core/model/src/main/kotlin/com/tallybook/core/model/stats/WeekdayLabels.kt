package com.tallybook.core.model.stats

import java.time.DayOfWeek

/**
 * Chinese weekday labels.
 *
 * Hard-coded rather than taken from `Locale.getDefault()`: the app's own strings are all Chinese, and
 * on a device set to English the system formatter returned "Mon"/"Tue" in the middle of an otherwise
 * Chinese screen. A single source here keeps the chart axis, day headers and weekday chips in the same
 * language as the rest of the UI.
 */
object WeekdayLabels {

    private val SHORT = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val NARROW = listOf("一", "二", "三", "四", "五", "六", "日")

    /** "周一" .. "周日". */
    fun short(day: DayOfWeek): String = SHORT[day.value - 1]

    /** "一" .. "日" — for tight chart axes. */
    fun narrow(day: DayOfWeek): String = NARROW[day.value - 1]
}
