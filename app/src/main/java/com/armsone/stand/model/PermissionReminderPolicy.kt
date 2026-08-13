package com.armsone.stand.model

data class PermissionReminderDecision(
    val shouldShow: Boolean,
    val launchesUntilNextReminder: Int?,
)

object PermissionReminderPolicy {
    const val MINIMUM_LAUNCH_INTERVAL = 3
    const val MAXIMUM_LAUNCH_INTERVAL = 7

    fun activityVisibility(
        isFirstActivityInProcess: Boolean,
        processDecision: Boolean,
        restoredVisibility: Boolean?,
    ): Boolean = if (isFirstActivityInProcess) {
        processDecision
    } else {
        restoredVisibility ?: processDecision
    }

    fun decide(
        hasMissingPermission: Boolean,
        launchesUntilReminder: Int?,
        nextRandomInterval: Int,
    ): PermissionReminderDecision {
        require(nextRandomInterval in MINIMUM_LAUNCH_INTERVAL..MAXIMUM_LAUNCH_INTERVAL)

        if (!hasMissingPermission) {
            return PermissionReminderDecision(
                shouldShow = false,
                launchesUntilNextReminder = null,
            )
        }

        return if (launchesUntilReminder == null || launchesUntilReminder <= 1) {
            PermissionReminderDecision(
                shouldShow = true,
                launchesUntilNextReminder = nextRandomInterval,
            )
        } else {
            PermissionReminderDecision(
                shouldShow = false,
                launchesUntilNextReminder = launchesUntilReminder - 1,
            )
        }
    }
}
