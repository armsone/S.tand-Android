package com.armsone.stand.boyiso;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WalkiePressPolicyTest {
    @Test public void acceptsOnlyConnectedWalkieAfterCooldown() {
        WalkiePressPolicy policy = new WalkiePressPolicy();

        assertFalse(policy.tryAccept(false, MonitoringService.ROLE_WALKIE, 1_000L));
        assertFalse(policy.tryAccept(true, MonitoringService.ROLE_HOST, 1_000L));
        assertTrue(policy.tryAccept(true, MonitoringService.ROLE_WALKIE, 1_000L));
        assertFalse(policy.tryAccept(true, MonitoringService.ROLE_WALKIE, 3_999L));
        assertTrue(policy.tryAccept(true, MonitoringService.ROLE_WALKIE, 4_000L));
    }
}
