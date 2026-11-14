package com.tallybook.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.tallybook.core.data.appearance.AppearanceSettings
import com.tallybook.core.data.export.ExportService
import com.tallybook.core.data.export.ImportEnvelope
import com.tallybook.core.data.export.ImportPreview
import com.tallybook.core.data.export.ImportService
import com.tallybook.core.data.initializer.InitialDataSeeder
import com.tallybook.core.data.notify.BudgetAlertChecker
import com.tallybook.core.data.notify.NotificationSettings
import com.tallybook.core.model.settings.ThemeMode
import com.tallybook.core.model.settings.UiStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val busy: Boolean = false,
    val message: String? = null,
    /** The user's own switch (independent of the OS permission). */
    val notificationsEnabled: Boolean = true,
    /** Whether the OS currently allows this app to post notifications. */
    val notificationsPermitted: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val uiStyle: UiStyle = UiStyle.GLASS,
    /** Non-null while the import confirmation dialog is showing. */
    val importPreview: ImportPreview? = null,
)

/**
 * Settings state holder.
 *
 * Currently only hosts the two data-maintenance actions. They exist because a demo needs a way to
 * (re)populate a realistic ledger without an adb shell, and a way to get back to an empty state.
 * Everything else on this screen is still static copy.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val seeder: InitialDataSeeder,
    private val notificationSettings: NotificationSettings,
    private val appearanceSettings: AppearanceSettings,
    private val budgetAlertChecker: BudgetAlertChecker,
    private val exportService: ExportService,
    private val importService: ImportService,
) : ViewModel() {

    /** Parsed file kept between preview and confirmation so the document is read once. */
    private var pendingImport: ImportEnvelope? = null

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            notificationSettings.enabled.collect { enabled ->
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appearanceSettings.preferences.collect { appearance ->
                _uiState.update {
                    it.copy(
                        themeMode = appearance.themeMode,
                        uiStyle = appearance.uiStyle,
                    )
                }
            }
        }
        refreshPermissionState()
    }

    /** Re-read on resume: the user may have granted/revoked the permission outside the app. */
    fun refreshPermissionState() {
        _uiState.update {
            it.copy(notificationsPermitted = budgetAlertChecker.canPostNotifications())
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationSettings.setEnabled(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appearanceSettings.setThemeMode(mode) }
    }

    fun setUiStyle(style: UiStyle) {
        viewModelScope.launch { appearanceSettings.setUiStyle(style) }
    }

    fun exportCsv(uri: Uri) {
        runExclusive(success = "已导出") {
            val count = exportService.exportCsv(uri)
            _uiState.update { it.copy(message = "已导出 $count 条记录（CSV）") }
        }
    }

    fun exportJson(uri: Uri) {
        runExclusive(success = "已导出") {
            val count = exportService.exportJson(uri)
            _uiState.update { it.copy(message = "已导出 $count 条记录（JSON）") }
        }
    }

    /** Reads the chosen file and shows what it would do; nothing is written yet. */
    fun onImportSelected(uri: Uri) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            runCatching { importService.preview(uri) }
                .onSuccess { loaded ->
                    pendingImport = loaded
                    _uiState.update { it.copy(busy = false, importPreview = loaded.preview) }
                }
                .onFailure { error ->
                    pendingImport = null
                    _uiState.update {
                        it.copy(busy = false, message = error.message ?: "无法读取这个文件")
                    }
                }
        }
    }

    fun dismissImport() {
        pendingImport = null
        _uiState.update { it.copy(importPreview = null) }
    }

    fun confirmImport() {
        val loaded = pendingImport ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, importPreview = null) }
            runCatching { importService.apply(loaded) }
                .onSuccess { result ->
                    pendingImport = null
                    _uiState.update {
                        it.copy(
                            busy = false,
                            message = if (result.inserted == 0) {
                                "没有新记录（" + result.skipped + " 条已存在）"
                            } else {
                                "已导入 " + result.inserted + " 条记录"
                            },
                        )
                    }
                }
                .onFailure { error ->
                    pendingImport = null
                    _uiState.update {
                        it.copy(busy = false, message = error.message ?: "导入失败，数据未改变")
                    }
                }
        }
    }

    fun loadDemoData() {
        runExclusive(success = "示例数据已载入") { seeder.loadDemoData(force = false) }
    }

    fun clearAllData() {
        runExclusive(success = "已清空全部数据") { seeder.clearAllData() }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun runExclusive(success: String, block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            val result = runCatching { block() }
            _uiState.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { success },
                        onFailure = { error -> error.message ?: "操作失败" },
                    ),
                )
            }
        }
    }
}
