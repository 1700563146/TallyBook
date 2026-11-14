package com.tallybook.core.data.notify

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** One DataStore per process; the delegate guarantees that. */
private val Context.alertDataStore by preferencesDataStore(name = "budget_alert_state")

/**
 * Remembers which budget alerts were already shown.
 *
 * Why persistence is required at all: the alert check runs on every app start and after every save.
 * Without remembering "already told the user this month", the same budget would notify on every
 * launch — the single most common complaint about budget notifications.
 *
 * Keys are `budgetId|periodKey|level`, so a *new period* de-duplicates independently and a *higher
 * escalation* (near-limit -> over) still gets through.
 */
@Singleton
class AlertStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun notifiedKeys(): Set<String> =
        context.alertDataStore.data.first()[KEY].orEmpty()

    /**
     * Remembers [keys] and prunes old ones.
     *
     * Pruning matters: the set otherwise grows forever (12 budgets x 2 levels x 12 months a year).
     * Keeping the most recent [MAX_KEYS] entries is enough because a stale key only ever suppresses
     * an alert for a period that is long over.
     */
    suspend fun markNotified(keys: Set<String>) {
        if (keys.isEmpty()) return
        context.alertDataStore.edit { prefs ->
            val merged = (prefs[KEY].orEmpty() + keys).toList()
            prefs[KEY] = if (merged.size <= MAX_KEYS) merged.toSet() else merged.takeLast(MAX_KEYS).toSet()
        }
    }

    /** Used by the settings "clear data" action. */
    suspend fun clear() {
        context.alertDataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("notified_alert_keys")
        const val MAX_KEYS = 200
    }
}
