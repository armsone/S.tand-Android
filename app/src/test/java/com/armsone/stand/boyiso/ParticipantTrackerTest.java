package com.armsone.stand.boyiso;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ParticipantTrackerTest {
    private static BoyisoEvent heartbeat(String eventId, String sourceId, String sourceName) {
        return new BoyisoEvent(
                eventId,
                sourceId,
                sourceName,
                MonitoringService.ROLE_GUEST,
                BoyisoEvent.HEARTBEAT,
                1L,
                null,
                null,
                true,
                80,
                BoyisoEvent.MODE_MATE,
                true
        );
    }

    @Test public void meshEchoDoesNotRefreshOrRestoreStaleParticipant() {
        ParticipantTracker tracker = new ParticipantTracker();
        BoyisoEvent event = heartbeat("event-1", "stable-phone", "침실폰");

        assertTrue(tracker.recordIfNew(event, "LAN", 1_000L));
        assertFalse(tracker.recordIfNew(event, "BLE", 10_000L));
        assertEquals(1_000L, tracker.lastSeenFor("stable-phone"));
        assertEquals(new HashSet<>(Arrays.asList("LAN")), tracker.pathsFor("stable-phone"));

        assertTrue(tracker.expireStale(16_001L, 15_000L));
        assertEquals(0, tracker.size());
        assertFalse(tracker.recordIfNew(event, "INTERNET", 16_002L));
        assertEquals(0, tracker.size());
    }

    @Test public void oneStableSourceIdCombinesAllTransportPathsButNotSameNameDevices() {
        ParticipantTracker tracker = new ParticipantTracker();
        assertTrue(tracker.recordIfNew(heartbeat("event-lan", "stable-phone", "침실폰"), "LAN", 1L));
        assertTrue(tracker.recordIfNew(heartbeat("event-ble", "stable-phone", "침실폰"), "BLE", 2L));
        assertTrue(tracker.recordIfNew(heartbeat("event-internet", "stable-phone", "침실폰"), "INTERNET", 3L));
        assertTrue(tracker.recordIfNew(heartbeat("event-second", "other-phone", "침실폰"), "LAN", 4L));

        assertEquals(2, tracker.size());
        assertEquals(
                new HashSet<>(Arrays.asList("LAN", "BLE", "INTERNET")),
                tracker.pathsFor("stable-phone")
        );
        assertEquals(1, tracker.pathsFor("other-phone").size());
    }

    @Test public void uppercaseIosUuidAndLowercaseAndroidUuidAreOneParticipantAndOneEvent() {
        ParticipantTracker tracker = new ParticipantTracker();
        String upperId = "BA65F73F-2B36-4FC4-A109-C39D51228B0C";
        String lowerId = "ba65f73f-2b36-4fc4-a109-c39d51228b0c";
        String upperEvent = "39611124-8D6A-4E0B-8572-8F472015249B";
        String lowerEvent = "39611124-8d6a-4e0b-8572-8f472015249b";

        assertTrue(tracker.recordIfNew(heartbeat(upperEvent, upperId, "갤럭시탭"), "LAN", 1L));
        assertFalse(tracker.recordIfNew(heartbeat(lowerEvent, lowerId, "갤럭시탭"), "BLE", 2L));
        assertEquals(1, tracker.size());
        assertEquals(1, tracker.pathsFor(lowerId).size());
        assertEquals(1L, tracker.lastSeenFor(upperId));
    }
}
