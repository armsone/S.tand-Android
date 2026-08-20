package com.armsone.stand.boyiso;

/** Prevents disconnected, wrong-role, and repeated walkie button presses from being sent. */
final class WalkiePressPolicy {
    static final long COOLDOWN_MILLIS = 3_000L;

    private boolean hasSent;
    private long lastSentAtMillis;

    boolean tryAccept(boolean running, String role, long nowMillis) {
        if (!running || !MonitoringService.ROLE_WALKIE.equals(role)) return false;
        if (hasSent && nowMillis - lastSentAtMillis < COOLDOWN_MILLIS) return false;
        hasSent = true;
        lastSentAtMillis = nowMillis;
        return true;
    }
}
