package com.tallybook.core.data.notify

/**
 * Port for "check budgets and notify".
 *
 * Declared in the data layer so feature modules can trigger a check (for example right after saving
 * a transaction, which is when the user most wants to know they just blew a budget) without
 * depending on `:app`, where the Android notification implementation lives.
 */
interface BudgetAlertChecker {
    /**
     * Evaluates budgets and posts any new alerts.
     * @return number of notifications posted; 0 when nothing escalated or permission is missing.
     */
    suspend fun checkAndNotify(): Int

    /** Whether alerts can actually be shown right now. */
    fun canPostNotifications(): Boolean
}
