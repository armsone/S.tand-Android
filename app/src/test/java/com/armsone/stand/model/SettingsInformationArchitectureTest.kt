package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsInformationArchitectureTest {
    @Test
    fun `settings cards follow the current iOS product order`() {
        assertEquals(
            listOf(
                SettingsSectionKind.SCREEN_AND_CLOCK,
                SettingsSectionKind.PERMISSIONS,
                SettingsSectionKind.SLEEP_SOUNDS,
                SettingsSectionKind.INFORMATION,
                SettingsSectionKind.INTERNET_RADIO,
            ),
            SettingsInformationArchitecture.CardOrder,
        )
    }
}
