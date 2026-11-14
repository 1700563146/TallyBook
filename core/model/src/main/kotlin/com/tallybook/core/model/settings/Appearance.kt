package com.tallybook.core.model.settings

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class UiStyle {
    SIMPLE,
    FLAT,
}

data class AppearancePreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val uiStyle: UiStyle = UiStyle.SIMPLE,
)
