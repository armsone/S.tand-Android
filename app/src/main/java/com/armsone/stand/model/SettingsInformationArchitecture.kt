package com.armsone.stand.model

enum class SettingsSectionKind {
    MUSIC,
    SCREEN_AND_CLOCK,
    PERMISSIONS,
    SLEEP_SOUNDS,
    BOYISO,
    INFORMATION,
}

object SettingsInformationArchitecture {
    val CardOrder: List<SettingsSectionKind> = listOf(
        SettingsSectionKind.SCREEN_AND_CLOCK,
        SettingsSectionKind.PERMISSIONS,
        SettingsSectionKind.BOYISO,
        SettingsSectionKind.SLEEP_SOUNDS,
        SettingsSectionKind.INFORMATION,
        SettingsSectionKind.MUSIC,
    )
}
