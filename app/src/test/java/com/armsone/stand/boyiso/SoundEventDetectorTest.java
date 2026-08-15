package com.armsone.stand.boyiso;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class SoundEventDetectorTest {
    @Test public void reportsLargeAndContinuousSoundWithoutCallingItCrying() {
        SoundEventDetector detector = new SoundEventDetector();
        List<String> detected = new ArrayList<>();
        short[] quiet = new short[1_024];
        short[] loud = new short[1_024];
        java.util.Arrays.fill(quiet, (short) 200);
        java.util.Arrays.fill(loud, (short) 14_000);
        long time = 0;
        for (int index = 0; index < 601; index++) {
            time += 100;
            detector.observe(quiet, quiet.length, time, (kind, level) -> detected.add(kind));
        }
        for (int index = 0; index < 30; index++) {
            time += 100;
            detector.observe(loud, loud.length, time, (kind, level) -> detected.add(kind));
        }
        assertTrue(detected.contains(BoyisoEvent.DETAIL_BIG_SOUND));
        assertTrue(detected.contains(BoyisoEvent.DETAIL_CONTINUOUS_SOUND));
    }

    @Test public void reportsQuietSharpFingerSnapWithoutCallingItBigSound() {
        SoundEventDetector detector = new SoundEventDetector();
        List<String> detected = new ArrayList<>();
        short[] quiet = new short[1_024];
        java.util.Arrays.fill(quiet, (short) 180);
        long time = 0;
        for (int index = 0; index < 601; index++) {
            time += 100;
            detector.observe(quiet, quiet.length, time, (kind, level) -> detected.add(kind));
        }
        short[] snap = quiet.clone();
        snap[510] = 15_000;
        snap[511] = -13_000;
        time += 100;
        detector.observe(snap, snap.length, time, (kind, level) -> detected.add(kind));

        assertTrue(detected.contains(BoyisoEvent.DETAIL_FINGER_SNAP));
        assertFalse(detected.contains(BoyisoEvent.DETAIL_BIG_SOUND));
    }

    @Test public void calibrationAndReportedQuietClipNeverSendSoundEvents() {
        SoundEventDetector detector = new SoundEventDetector();
        List<String> detected = new ArrayList<>();
        short[] loudDuringCalibration = new short[1_024];
        java.util.Arrays.fill(loudDuringCalibration, (short) 14_000);
        long time = 0;
        for (int index = 0; index < 600; index++) {
            time += 100;
            detector.observe(loudDuringCalibration, loudDuringCalibration.length, time,
                    (kind, level) -> detected.add(kind));
        }
        assertTrue(detected.isEmpty());

        short[] quietRoom = new short[1_024];
        java.util.Arrays.fill(quietRoom, (short) 200);
        time += 100;
        detector.observe(quietRoom, quietRoom.length, time, (kind, level) -> detected.add(kind));

        short[] reportedQuietClip = new short[1_024];
        java.util.Arrays.fill(reportedQuietClip, (short) 152);
        reportedQuietClip[512] = 470;
        for (int index = 0; index < 30; index++) {
            time += 100;
            detector.observe(reportedQuietClip, reportedQuietClip.length, time,
                    (kind, level) -> detected.add(kind));
        }
        assertTrue(detected.isEmpty());
    }
}
