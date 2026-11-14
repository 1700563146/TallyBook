package com.tallybook.core.data.appearance

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tallybook.core.model.settings.AppearancePreferences
import com.tallybook.core.model.settings.ThemeMode
import com.tallybook.core.model.settings.UiStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceSettingsStore by preferencesDataStore(name = "appearance_settings")

@Singleton
class AppearanceSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val preferences: Flow<AppearancePreferences> = context.appearanceSettingsStore.data.map { values ->
        AppearancePreferences(
            themeMode = values[KEY_THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            uiStyle = values[KEY_UI_STYLE]
                ?.let { runCatching { UiStyle.valueOf(it) }.getOrNull() }
                ?: UiStyle.GLASS,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appearanceSettingsStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setUiStyle(style: UiStyle) {
        context.appearanceSettingsStore.edit { it[KEY_UI_STYLE] = style.name }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_UI_STYLE = stringPreferencesKey("ui_style")
    }
}
