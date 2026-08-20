package com.armsone.stand.boyiso;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/** Runs with Android's real JSONObject implementation, unlike the local JVM test double. */
@RunWith(AndroidJUnit4.class)
public class BoyisoProtocolInstrumentedTest {
    private static final String ROOM_KEY =
            "4ZsyqW7LZ6sLkKLQ7WlUD8u7tAt3tfqZMRLCcCz7nJ0";

    @Test public void stableSourceIdSurvivesLanBleAndRelayPayloadEncoding() throws Exception {
        CryptoCodec codec = new CryptoCodec(ROOM_KEY);
        BoyisoEvent original = new BoyisoEvent(
                "39611124-8D6A-4E0B-8572-8F472015249B",
                "BA65F73F-2B36-4FC4-A109-C39D51228B0C", "거실 태블릿", MonitoringService.ROLE_HOST,
                BoyisoEvent.HEARTBEAT, 123L, null, null, false, 82,
                BoyisoEvent.MODE_MATE, true);

        BoyisoEvent lanDecoded = codec.openText(codec.sealToText(original));
        BoyisoEvent binaryDecoded = codec.openEvent(codec.sealEvent(original));

        assertEquals("ba65f73f-2b36-4fc4-a109-c39d51228b0c", lanDecoded.sourceId);
        assertEquals("ba65f73f-2b36-4fc4-a109-c39d51228b0c", binaryDecoded.sourceId);
        assertEquals("39611124-8d6a-4e0b-8572-8f472015249b", lanDecoded.id);
        assertEquals("39611124-8d6a-4e0b-8572-8f472015249b", binaryDecoded.id);
    }

    @Test public void walkiePressRoundTripsWithRoleAndPressDetail() throws Exception {
        BoyisoEvent original = BoyisoEvent.walkiePress(
                "walkie-1", "현관 무전기", MonitoringService.ROLE_WALKIE, 77,
                BoyisoEvent.MODE_OBJECT, false);

        BoyisoEvent decoded = BoyisoEvent.decode(original.encodeBytes());

        assertEquals(BoyisoEvent.WALKIE, decoded.kind);
        assertEquals(MonitoringService.ROLE_WALKIE, decoded.role);
        assertEquals(BoyisoEvent.DETAIL_WALKIE_PRESS, decoded.detail);
        assertEquals(Double.valueOf(1.0), decoded.intensity);
        assertEquals(false, decoded.monitoring);
    }
}
