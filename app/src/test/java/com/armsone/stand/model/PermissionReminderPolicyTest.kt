package com.armsone.stand.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionReminderPolicyTest {
    @Test
    fun newProcessUsesFreshDecisionInsteadOfRestoredActivityVisibility() {
        assertTrue(
            PermissionReminderPolicy.activityVisibility(
                isFirstActivityInProcess = true,
                processDecision = true,
                restoredVisibility = false,
            ),
        )
        assertFalse(
            PermissionReminderPolicy.activityVisibility(
                isFirstActivityInProcess = true,
                processDecision = false,
                restoredVisibility = true,
            ),
        )
    }

    @Test
    fun activityRecreationKeepsVisibilityChosenForTheCurrentProcess() {
        assertFalse(
            PermissionReminderPolicy.activityVisibility(
                isFirstActivityInProcess = false,
                processDecision = true,
                restoredVisibility = false,
            ),
        )
        assertTrue(
            PermissionReminderPolicy.activityVisibility(
                isFirstActivityInProcess = false,
                processDecision = false,
                restoredVisibility = true,
            ),
        )
    }

    @Test
    fun firstMissingPermissionLaunchShowsAndSchedulesRandomInterval() {
        val decision = PermissionReminderPolicy.decide(
            hasMissingPermission = true,
            launchesUntilReminder = null,
            nextRandomInterval = 5,
        )

        assertTrue(decision.shouldShow)
        assertEquals(5, decision.launchesUntilNextReminder)
    }

    @Test
    fun interveningLaunchesCountDownWithoutShowing() {
        val decision = PermissionReminderPolicy.decide(
            hasMissingPermission = true,
            launchesUntilReminder = 4,
            nextRandomInterval = 7,
        )

        assertFalse(decision.shouldShow)
        assertEquals(3, decision.launchesUntilNextReminder)
    }

    @Test
    fun dueLaunchShowsAndSchedulesANewRandomInterval() {
        val decision = PermissionReminderPolicy.decide(
            hasMissingPermission = true,
            launchesUntilReminder = 1,
            nextRandomInterval = 3,
        )

        assertTrue(decision.shouldShow)
        assertEquals(3, decision.launchesUntilNextReminder)
    }

    @Test
    fun allPermissionsGrantedClearsReminder() {
        val decision = PermissionReminderPolicy.decide(
            hasMissingPermission = false,
            launchesUntilReminder = 2,
            nextRandomInterval = 6,
        )

        assertFalse(decision.shouldShow)
        assertNull(decision.launchesUntilNextReminder)
    }
}
