package com.tallybook.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the daily budget check registered.
 *
 * Two deliberate choices:
 * - **`ExistingPeriodicWorkPolicy.KEEP`** on every app start: re-enqueueing with `UPDATE`/`REPLACE`
 *   would reset the period each launch, so a user who opens the app daily would never actually reach
 *   the scheduled run.
 * - **No network constraint**: the check is purely local. Marking it network-dependent would make
 *   offline users stop receiving budget alerts for no reason.
 */
@Singleton
class BudgetCheckScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<BudgetCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BudgetCheckWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
