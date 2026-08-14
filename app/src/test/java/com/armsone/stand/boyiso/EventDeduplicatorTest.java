package com.armsone.stand.boyiso;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventDeduplicatorTest {
    @Test public void sameEventFromLanAndBleIsAcceptedOnce() {
        EventDeduplicator deduplicator = new EventDeduplicator();
        assertTrue(deduplicator.accept("event-1", 1_000));
        assertFalse(deduplicator.accept("event-1", 1_001));
        assertTrue(deduplicator.accept("event-2", 1_002));
    }

    @Test public void expiredEventCanBeAcceptedAgain() {
        EventDeduplicator deduplicator = new EventDeduplicator();
        assertTrue(deduplicator.accept("event-1", 1_000));
        assertTrue(deduplicator.accept("event-1", 10 * 60 * 1_000L + 1_001));
    }

    @Test public void uuidEventIdIsCaseInsensitiveButLegacyIdIsPreserved() {
        EventDeduplicator deduplicator = new EventDeduplicator();
        assertTrue(deduplicator.accept("BA65F73F-2B36-4FC4-A109-C39D51228B0C", 1_000));
        assertFalse(deduplicator.accept("ba65f73f-2b36-4fc4-a109-c39d51228b0c", 1_001));
        assertTrue(deduplicator.accept("Legacy-Event-ID", 1_002));
        assertTrue(deduplicator.accept("legacy-event-id", 1_003));
    }
}
