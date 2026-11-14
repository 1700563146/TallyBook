package com.tallybook.core.data.notify

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationSettingsStore by preferencesDataStore(name = "notification_settings")

/**
 * User-level switch for budget notifications.
 *
 * Separate from the OS permission on purpose: the system permission can be revoked outside the app
 * (or never granted), and a user who granted it may still not want alerts. Both must be true before
 * anything is posted, and the settings screen shows them as two different things because they have
 * two different fixes.
 */
@Singleton
class NotificationSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Defaults to on: a budget the user created implies they want to know when it is blown. */
    val enabled: Flow<Boolean> = context.notificationSettingsStore.data
        .map { it[KEY] ?: true }

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(value: Boolean) {
        context.notificationSettingsStore.edit { it[KEY] = value }
    }

    private companion object {
        val KEY = booleanPreferencesKey("budget_alerts_enabled")
    }
}
