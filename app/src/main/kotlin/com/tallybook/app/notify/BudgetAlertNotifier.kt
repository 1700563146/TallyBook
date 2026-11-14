package com.tallybook.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tallybook.R
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.data.notify.AlertStateStore
import com.tallybook.core.data.notify.BudgetAlertChecker
import com.tallybook.core.data.notify.NotificationSettings
import com.tallybook.core.data.repository.BudgetRepository
import com.tallybook.core.model.stats.BudgetAlert
import com.tallybook.core.model.stats.BudgetAlertPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns budget progress into notifications.
 *
 * Lives in `:app` rather than `:core:data` because it needs app resources (the notification icon) and
 * the Android notification API — the data layer stays free of both. The *decision* of what to notify
 * is not here: it is [BudgetAlertPolicy], a pure function with its own tests.
 *
 * Scope note: this runs on app start and after a save (foreground). A WorkManager job for
 * background checks is a separate step — see docs/ROADMAP.md Phase 3.
 */
@Singleton
class BudgetAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgetRepository: BudgetRepository,
    private val alertStateStore: AlertStateStore,
    private val notificationSettings: NotificationSettings,
    private val clock: Clock,
) : BudgetAlertChecker {

    /**
     * Evaluates budgets and posts any new alerts.
     *
     * @return number of notifications posted (0 when nothing escalated, or when permission is absent)
     */
    override suspend fun checkAndNotify(): Int {
        // Without the runtime permission nothing shows up on Android 13+, and posting would just be
        // a silent no-op — returning 0 keeps the caller honest about "nothing happened".
        // Two independent gates: what the OS allows, and what the user asked for.
        if (!notificationSettings.isEnabled()) return 0
        if (!canPostNotifications()) return 0

        val now = clock.nowMillis()
        val progress = budgetRepository.observeProgress(now).first()
        if (progress.isEmpty()) return 0

        val alreadyNotified = alertStateStore.notifiedKeys()
        val alerts = BudgetAlertPolicy.evaluate(progress, alreadyNotified)
        if (alerts.isEmpty()) return 0

        val posted = alerts.filter(::post)
        if (posted.isNotEmpty()) {
            alertStateStore.markNotified(posted.map { it.dedupKey }.toSet())
        }
        return posted.size
    }

    override fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun post(alert: BudgetAlert): Boolean {
        if (!canPostNotifications()) return false
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_budget)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            // Same budget + period replaces its own notification instead of stacking a duplicate.
            .setOnlyAlertOnce(true)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(alert.dedupKey.hashCode(), notification)
            true
        }.getOrDefault(false)
    }

    companion object {
        const val CHANNEL_ID = "budget_alerts"

        /**
         * Creates the notification channel. Must run before the first notify, and is safe to call
         * repeatedly (creating an existing channel is a no-op on all supported versions).
         */
        fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_budget_alerts),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_budget_alerts_description)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
