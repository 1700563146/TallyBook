package com.tallybook.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tallybook.core.data.notify.BudgetAlertChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Daily background check that posts budget alerts.
 *
 * Why a `CoroutineWorker` and not a foreground service or an alarm: this is exactly the "deferrable,
 * guaranteed-ish" work WorkManager exists for. It survives process death, respects Doze, and — most
 * importantly — does not require the app to be open, which is the whole point (a user who stops
 * opening the app is precisely the one who needs the warning).
 *
 * Failure is reported as [Result.retry] once and then [Result.success]: a permanently broken check
 * (for example, notifications disabled) must not produce an infinite backoff storm.
 */
@HiltWorker
class BudgetCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checker: BudgetAlertChecker,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        checker.checkAndNotify()
        Result.success()
    }.getOrElse {
        if (runAttemptCount >= MAX_ATTEMPTS) Result.success() else Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "budget_alert_daily_check"
        private const val MAX_ATTEMPTS = 3
    }
}
