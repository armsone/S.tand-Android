package com.armsone.stand.boyiso;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoyisoBackgroundAlertPolicyTest {
    private static BoyisoEvent event(String kind) {
        return new BoyisoEvent("event", "speaker", "침실폰", MonitoringService.ROLE_GUEST,
                kind, 1L, null, BoyisoEvent.DETAIL_FINGER_SNAP, true, 80,
                BoyisoEvent.MODE_MATE, true);
    }

    @Test public void soundAlertsOnlyMateViewer() {
        assertTrue(BoyisoBackgroundAlertPolicy.shouldShowSoundAlert(
                MonitoringService.ROLE_HOST, true, BoyisoEvent.MODE_MATE, event(BoyisoEvent.SOUND)));
        assertFalse(BoyisoBackgroundAlertPolicy.shouldShowSoundAlert(
                MonitoringService.ROLE_GUEST, true, BoyisoEvent.MODE_MATE, event(BoyisoEvent.SOUND)));
        assertFalse(BoyisoBackgroundAlertPolicy.shouldShowSoundAlert(
                MonitoringService.ROLE_HOST, false, BoyisoEvent.MODE_MATE, event(BoyisoEvent.SOUND)));
        assertFalse(BoyisoBackgroundAlertPolicy.shouldShowSoundAlert(
                MonitoringService.ROLE_HOST, true, BoyisoEvent.MODE_OBJECT, event(BoyisoEvent.SOUND)));
    }

    @Test public void movementFollowsExistingMateModeRule() {
        assertTrue(BoyisoBackgroundAlertPolicy.shouldShowMovementAlert(
                true, BoyisoEvent.MODE_MATE, event(BoyisoEvent.MOVEMENT)));
        assertFalse(BoyisoBackgroundAlertPolicy.shouldShowMovementAlert(
                false, BoyisoEvent.MODE_MATE, event(BoyisoEvent.MOVEMENT)));
    }

    @Test public void eligibleSoundStillNotifiesInForegroundButOnlyBackgroundPlaysServiceChime() {
        assertTrue(BoyisoBackgroundAlertPolicy.shouldShowSoundAlert(
                MonitoringService.ROLE_HOST, true, BoyisoEvent.MODE_MATE, event(BoyisoEvent.SOUND)));
        assertFalse(BoyisoBackgroundAlertPolicy.shouldPlaySoundChime(true));
        assertTrue(BoyisoBackgroundAlertPolicy.shouldPlaySoundChime(false));
    }

    @Test public void walkieCallIsRecognizedWithoutMateSessionGating() {
        BoyisoEvent walkie = new BoyisoEvent("event", "walkie", "현관", MonitoringService.ROLE_WALKIE,
                BoyisoEvent.WALKIE, 1L, 1.0, BoyisoEvent.DETAIL_WALKIE_PRESS, false, 80,
                BoyisoEvent.MODE_OBJECT, false);
        assertTrue(BoyisoBackgroundAlertPolicy.shouldShowWalkieAlert(walkie));
        assertFalse(BoyisoBackgroundAlertPolicy.shouldShowWalkieAlert(event(BoyisoEvent.SOUND)));
    }
}
