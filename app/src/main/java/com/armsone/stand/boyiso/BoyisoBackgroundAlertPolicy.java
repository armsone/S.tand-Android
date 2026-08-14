package com.armsone.stand.boyiso;

/** Background notification rules mirror the visible viewer's mate-mode alert rules. */
final class BoyisoBackgroundAlertPolicy {
    private BoyisoBackgroundAlertPolicy() { }

    static boolean shouldShowSoundAlert(String localRole, boolean sessionActive, String displayMode,
                                        BoyisoEvent event) {
        return MonitoringService.ROLE_HOST.equals(localRole)
                && sessionActive
                && BoyisoEvent.MODE_MATE.equals(displayMode)
                && BoyisoEvent.SOUND.equals(event.kind);
    }

    static boolean shouldShowMovementAlert(boolean sessionActive, String displayMode,
                                           BoyisoEvent event) {
        return sessionActive
                && BoyisoEvent.MODE_MATE.equals(displayMode)
                && BoyisoEvent.MOVEMENT.equals(event.kind);
    }
}
