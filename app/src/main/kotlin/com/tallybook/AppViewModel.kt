package com.tallybook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tallybook.core.data.appearance.AppearanceSettings
import com.tallybook.core.model.settings.AppearancePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    appearanceSettings: AppearanceSettings,
) : ViewModel() {

    val appearance: StateFlow<AppearancePreferences> = appearanceSettings.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppearancePreferences(),
    )
}
