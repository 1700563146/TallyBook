package com.tallybook.core.model.stats

import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.transaction.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Budget alert policy tests.
 *
 * Notifications are the one feature where a logic bug is *visible to the user as annoyance*: firing
 * twice, firing for a budget already over for days, or firing at 30%. Every rule in the policy has a
 * test here because none of it is observable by looking at the screen once.
 */
class BudgetAlertPolicyTest {

    private val cny = CurrencyCode.CNY
    private val periodStart = 1_760_000_000_000L

    private fun progress(
        id: String = "b1",
        name: String = "餐饮",
        limitMinor: Long = 100_000,
        spentMinor: Long,
        daysElapsed: Int = 15,
        daysTotal: Int = 30,
    ) = BudgetProgress(
        budgetId = id,
        name = name,
        period = BudgetPeriod.MONTHLY,
        limit = Money(limitMinor, cny),
        spent = Money(spentMinor, cny),
        periodStartMillis = periodStart,
        periodEndExclusiveMillis = periodStart + 30L * 86_400_000L,
        daysElapsed = daysElapsed,
        daysTotal = daysTotal,
    )

    @Test
    fun `no alert below the near-limit threshold`() {
        val alerts = BudgetAlertPolicy.evaluate(listOf(progress(spentMinor = 50_000)), emptySet())
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `exactly at the threshold alerts`() {
        val alerts = BudgetAlertPolicy.evaluate(listOf(progress(spentMinor = 80_000)), emptySet())
        assertEquals(1, alerts.size)
        assertEquals(AlertLevel.NEAR_LIMIT, alerts.single().level)
    }

    @Test
    fun `one cent below the threshold stays quiet`() {
        val alerts = BudgetAlertPolicy.evaluate(listOf(progress(spentMinor = 79_999)), emptySet())
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `over budget escalates instead of also firing near-limit`() {
        val alerts = BudgetAlertPolicy.evaluate(listOf(progress(spentMinor = 150_000)), emptySet())
        assertEquals(1, alerts.size)
        assertEquals(AlertLevel.OVER_BUDGET, alerts.single().level)
    }

    @Test
    fun `zero spend never alerts even with a zero limit`() {
        // A brand-new 0-limit budget must not look "over" before anything is recorded.
        val alerts = BudgetAlertPolicy.evaluate(
            listOf(progress(limitMinor = 0, spentMinor = 0)),
            emptySet(),
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `already notified level is suppressed`() {
        val first = BudgetAlertPolicy.evaluate(listOf(progress(spentMinor = 85_000)), emptySet())
        val second = BudgetAlertPolicy.evaluate(
            listOf(progress(spentMinor = 85_000)),
            first.map { it.dedupKey }.toSet(),
        )
        assertEquals(1, first.size)
        assertTrue("same level in the same period must not repeat", second.isEmpty())
    }

    @Test
    fun `near-limit then over-budget both fire once each`() {
        val near = BudgetAlertPolicy.evaluate(listOf(progress(spentMinor = 85_000)), emptySet())
        val notified = near.map { it.dedupKey }.toSet()

        val over = BudgetAlertPolicy.evaluate(listOf(progress(spentMinor = 120_000)), notified)

        assertEquals(1, over.size)
        assertEquals(AlertLevel.OVER_BUDGET, over.single().level)
    }

    @Test
    fun `a new period resets de-duplication`() {
        val current = progress(spentMinor = 90_000)
        val notified = BudgetAlertPolicy.evaluate(listOf(current), emptySet()).map { it.dedupKey }.toSet()

        val nextPeriod = current.copy(
            periodStartMillis = periodStart + 31L * 86_400_000L,
            spent = Money(95_000, cny),
        )
        val alerts = BudgetAlertPolicy.evaluate(listOf(nextPeriod), notified)

        assertEquals("the new period must alert again", 1, alerts.size)
    }

    @Test
    fun `each budget alerts independently`() {
        val alerts = BudgetAlertPolicy.evaluate(
            listOf(
                progress(id = "a", name = "餐饮", spentMinor = 90_000),
                progress(id = "b", name = "购物", spentMinor = 10_000),
                progress(id = "c", name = "交通", spentMinor = 120_000),
            ),
            emptySet(),
        )
        assertEquals(2, alerts.size)
        assertEquals(setOf("a", "c"), alerts.map { it.budgetId }.toSet())
    }

    @Test
    fun `message contains the amounts the user needs`() {
        val alert = BudgetAlertPolicy.evaluate(
            listOf(progress(spentMinor = 132_500)),
            emptySet(),
        ).single()

        assertTrue("title should name the budget: ${alert.title}", alert.title.contains("餐饮"))
        assertTrue("message should show spent and limit: ${alert.message}", alert.message.contains("¥1,325.00"))
        assertTrue("message should show the overrun: ${alert.message}", alert.message.contains("¥325.00"))
    }

    @Test
    fun `period key is stable for the same period and differs across periods`() {
        val a = BudgetAlertPolicy.periodKeyOf(progress(spentMinor = 1))
        val b = BudgetAlertPolicy.periodKeyOf(progress(spentMinor = 99_999))
        val c = BudgetAlertPolicy.periodKeyOf(progress(spentMinor = 1).copy(periodStartMillis = periodStart + 1))

        assertEquals("same period must produce the same key", a, b)
        assertTrue("different period must produce a different key", a != c)
    }
}
