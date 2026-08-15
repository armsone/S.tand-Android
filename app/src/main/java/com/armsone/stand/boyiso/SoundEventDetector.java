package com.armsone.stand.boyiso;

final class SoundEventDetector {
    interface Listener {
        void onDetected(String kind, int level);
    }

    private static final long BIG_SOUND_COOLDOWN_MILLIS = 1_500;
    private static final long FINGER_SNAP_COOLDOWN_MILLIS = 1_000;
    private static final long CONTINUOUS_MILLIS = 2_000;
    private static final long CALIBRATION_MILLIS = 60_000;
    private double baseline = 0.015;
    private long elevatedSince = 0;
    private long lastBigSound = 0;
    private long lastFingerSnap = 0;
    private boolean continuousReported = false;
    private int samplesObserved = 0;
    private long calibrationStartedAt = -1;

    void observe(short[] samples, int count, long nowMillis, Listener listener) {
        if (count <= 0) return;
        double sum = 0;
        double peak = 0;
        for (int index = 0; index < count; index++) {
            double normalized = samples[index] / 32768.0;
            sum += normalized * normalized;
            peak = Math.max(peak, Math.abs(normalized));
        }
        double rms = Math.sqrt(sum / count);
        if (calibrationStartedAt < 0) calibrationStartedAt = nowMillis;
        samplesObserved++;
        boolean calibrating = nowMillis - calibrationStartedAt < CALIBRATION_MILLIS;
        double learningRate = calibrating ? 0.08 : 0.008;
        if (samplesObserved < 20 || rms < baseline * 2.0) {
            baseline = Math.max(0.003, baseline * (1.0 - learningRate) + rms * learningRate);
        }
        if (calibrating) return;

        double bigThreshold = Math.max(0.08, baseline * 3.2);
        double continuousThreshold = Math.max(0.06, baseline * 3.2);
        int level = (int) Math.round(Math.min(100, rms / Math.max(0.08, baseline * 4.0) * 100));

        double fingerSnapPeakThreshold = Math.max(0.16, baseline * 6.0);
        boolean looksLikeFingerSnap = peak >= fingerSnapPeakThreshold
                && peak >= rms * 4.5
                && rms < bigThreshold;
        if (looksLikeFingerSnap && nowMillis - lastFingerSnap >= FINGER_SNAP_COOLDOWN_MILLIS) {
            lastFingerSnap = nowMillis;
            int snapLevel = (int) Math.round(Math.min(100, peak * 140));
            listener.onDetected(BoyisoEvent.DETAIL_FINGER_SNAP, snapLevel);
        }

        if (rms >= bigThreshold
                && nowMillis - lastBigSound >= BIG_SOUND_COOLDOWN_MILLIS) {
            lastBigSound = nowMillis;
            listener.onDetected(BoyisoEvent.DETAIL_BIG_SOUND, level);
        }

        if (rms >= continuousThreshold) {
            if (elevatedSince == 0) elevatedSince = nowMillis;
            if (!continuousReported && nowMillis - elevatedSince >= CONTINUOUS_MILLIS) {
                continuousReported = true;
                listener.onDetected(BoyisoEvent.DETAIL_CONTINUOUS_SOUND, level);
            }
        } else {
            elevatedSince = 0;
            continuousReported = false;
        }
    }
}
