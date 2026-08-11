package com.armsone.stand.model

enum class SettingsSectionKind {
    INTERNET_RADIO,
    SCREEN_AND_CLOCK,
    PERMISSIONS,
    SLEEP_SOUNDS,
    INFORMATION,
}

object SettingsInformationArchitecture {
    val CardOrder: List<SettingsSectionKind> = listOf(
        SettingsSectionKind.INTERNET_RADIO,
        SettingsSectionKind.SCREEN_AND_CLOCK,
        SettingsSectionKind.PERMISSIONS,
        SettingsSectionKind.SLEEP_SOUNDS,
        SettingsSectionKind.INFORMATION,
    )
}
