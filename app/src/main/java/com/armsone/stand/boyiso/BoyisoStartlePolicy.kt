package com.armsone.stand.boyiso

import com.armsone.stand.model.EnvironmentDisplayMode

object BoyisoStartlePolicy {
    fun shouldRelayMovement(
        localRole: BoyisoRole,
        localSessionActive: Boolean,
        localMode: EnvironmentDisplayMode,
        connectedDevices: List<BoyisoDevice>,
    ): Boolean =
        localRole == BoyisoRole.SPEAKER &&
            localSessionActive &&
            localMode == EnvironmentDisplayMode.MATE &&
            connectedDevices.isNotEmpty() &&
            connectedDevices.all { device ->
                device.sessionActive && device.displayMode == EnvironmentDisplayMode.MATE
            }

    fun shouldActivateForSound(
        localRole: BoyisoRole,
        localSessionActive: Boolean,
        localMode: EnvironmentDisplayMode,
    ): Boolean =
        localRole == BoyisoRole.VIEWER &&
            localSessionActive &&
            localMode == EnvironmentDisplayMode.MATE

    fun shouldActivateForMovement(
        localSessionActive: Boolean,
        localMode: EnvironmentDisplayMode,
    ): Boolean = localSessionActive && localMode == EnvironmentDisplayMode.MATE

    fun shouldShowCryingChild(event: BoyisoEventSummary): Boolean =
        event.kind == "sound" &&
            event.detail in setOf("big_sound", "continuous_sound", "finger_snap")
}
