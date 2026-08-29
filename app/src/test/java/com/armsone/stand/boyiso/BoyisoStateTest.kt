package com.armsone.stand.boyiso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoyisoStateTest {
    @Test
    fun `hasRoom is true only when roomId and valid 32-char key exist`() {
        val empty = BoyisoConfiguration()
        assertFalse(empty.hasRoom)

        val missingKey = BoyisoConfiguration(roomId = "room-1", roomKey = "short")
        assertFalse(missingKey.hasRoom)

        val valid = BoyisoConfiguration(
            roomId = "room-1",
            roomKey = "12345678901234567890123456789012",
        )
        assertTrue(valid.hasRoom)
    }

    @Test
    fun `statusText reports not started when room configured but not running`() {
        val noRoomState = BoyisoState(
            configuration = BoyisoConfiguration(),
            running = false,
        )
        assertEquals("설정 필요", noRoomState.statusText)

        val roomConfiguredState = BoyisoState(
            configuration = BoyisoConfiguration(
                roomId = "room-1",
                roomKey = "12345678901234567890123456789012",
            ),
            running = false,
        )
        assertEquals("감시를 시작하지 않았습니다", roomConfiguredState.statusText)
    }

    @Test
    fun `issueMessage takes precedence in statusText`() {
        val state = BoyisoState(
            configuration = BoyisoConfiguration(
                roomId = "room-1",
                roomKey = "12345678901234567890123456789012",
            ),
            running = false,
            issueMessage = "마이크 권한을 허용해 주세요. 설정에서 켤 수 있습니다.",
        )
        assertEquals("마이크 권한을 허용해 주세요. 설정에서 켤 수 있습니다.", state.statusText)
    }

    @Test
    fun `running status differentiates speaker mic status and viewer connection counts`() {
        val speakerReady = BoyisoState(
            configuration = BoyisoConfiguration(
                role = BoyisoRole.SPEAKER,
                roomId = "room-1",
                roomKey = "12345678901234567890123456789012",
            ),
            running = true,
            microphoneMonitoring = true,
        )
        assertEquals("말할 준비됨", speakerReady.statusText)

        val speakerWaiting = BoyisoState(
            configuration = BoyisoConfiguration(
                role = BoyisoRole.SPEAKER,
                roomId = "room-1",
                roomKey = "12345678901234567890123456789012",
            ),
            running = true,
            microphoneMonitoring = false,
        )
        assertEquals("마이크 대기", speakerWaiting.statusText)

        val walkieState = BoyisoState(
            configuration = BoyisoConfiguration(
                role = BoyisoRole.WALKIE,
                roomId = "room-1",
                roomKey = "12345678901234567890123456789012",
            ),
            running = true,
        )
        assertEquals("무전기 대기", walkieState.statusText)

        val viewerWaiting = BoyisoState(
            configuration = BoyisoConfiguration(
                role = BoyisoRole.VIEWER,
                roomId = "room-1",
                roomKey = "12345678901234567890123456789012",
            ),
            running = true,
            devices = emptyList(),
        )
        assertEquals("말할 사람 연결 대기", viewerWaiting.statusText)
    }
}
