package com.tallybook

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tallybook.app.notify.BudgetAlertNotifier
import com.tallybook.app.work.BudgetCheckScheduler
import com.tallybook.core.data.initializer.InitialDataSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Startup work is deliberately fire-and-forget on an application-scoped coroutine: seeding and the
 * one-off budget alert check must never delay the first frame. If either fails, the app still starts
 * and the user can do everything manually.
 */
@HiltAndroidApp
class TallyBookApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var seeder: InitialDataSeeder

    @Inject
    lateinit var budgetAlertNotifier: BudgetAlertNotifier

    @Inject
    lateinit var budgetCheckScheduler: BudgetCheckScheduler

    /**
     * WorkManager must be initialised through Hilt's factory, otherwise `@HiltWorker` workers cannot
     * be constructed. The default initializer is removed in the manifest so this configuration wins.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // The channel must exist before any notify() call; creating it repeatedly is a no-op.
        BudgetAlertNotifier.createChannel(this)

        // Registering the daily check is cheap and idempotent (KEEP policy).
        runCatching { budgetCheckScheduler.ensureScheduled() }

        applicationScope.launch {
            runCatching { seeder.seedIfEmpty() }
            // Check on launch as well as after saves, so a user who crossed a threshold in another
            // session still gets told. De-duplication lives in AlertStateStore, not here.
            runCatching { budgetAlertNotifier.checkAndNotify() }
        }
    }
}
