package com.armsone.stand.boyiso;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertArrayEquals;

public class BoyisoProtocolTest {
    private static final String ROOM_KEY =
            "4ZsyqW7LZ6sLkKLQ7WlUD8u7tAt3tfqZMRLCcCz7nJ0";

    @Test public void encryptionRoundTripsAndTokTokCarriesRole() throws Exception {
        CryptoCodec codec = new CryptoCodec(ROOM_KEY);
        byte[] cleartext = "boyiso-v2-event".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        BoyisoEvent original = BoyisoEvent.tokTok(
                "source-1", "거실 태블릿", MonitoringService.ROLE_HOST, 82,
                BoyisoEvent.MODE_MATE, true);

        byte[] encrypted = codec.sealBytes(cleartext);

        assertArrayEquals(cleartext, codec.openBytes(encrypted));
        assertEquals("거실 태블릿", original.sourceName);
        assertEquals(MonitoringService.ROLE_HOST, original.role);
        assertEquals(BoyisoEvent.TOKTOK, original.kind);
        assertEquals(Integer.valueOf(82), original.batteryPercent);
        assertEquals(BoyisoEvent.MODE_MATE, original.displayMode);
        assertEquals(true, original.sessionActive);
    }

    @Test public void routingChannelIsStableButDoesNotRevealRoomIdentifier() {
        String first = RemoteRelay.routingChannel("room-a", ROOM_KEY);
        String repeated = RemoteRelay.routingChannel("room-a", ROOM_KEY);
        String otherRoom = RemoteRelay.routingChannel("room-b", ROOM_KEY);

        assertEquals(first, repeated);
        assertNotEquals(first, otherRoom);
        assertEquals(43, first.length());
    }

    @Test public void walkiePressCarriesRoleAndPressDetail() {
        BoyisoEvent original = BoyisoEvent.walkiePress(
                "walkie-1", "현관 무전기", MonitoringService.ROLE_WALKIE, 77,
                BoyisoEvent.MODE_OBJECT, false);

        assertEquals(BoyisoEvent.WALKIE, original.kind);
        assertEquals(MonitoringService.ROLE_WALKIE, original.role);
        assertEquals(BoyisoEvent.DETAIL_WALKIE_PRESS, original.detail);
        assertEquals(Double.valueOf(1.0), original.intensity);
        assertEquals(false, original.monitoring);
    }
}
