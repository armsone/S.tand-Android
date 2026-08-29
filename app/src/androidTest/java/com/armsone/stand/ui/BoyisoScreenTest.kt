package com.armsone.stand.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.armsone.stand.boyiso.BoyisoConfiguration
import com.armsone.stand.boyiso.BoyisoDevice
import com.armsone.stand.boyiso.BoyisoRole
import com.armsone.stand.boyiso.BoyisoState
import com.armsone.stand.model.EnvironmentDisplayMode
import com.armsone.stand.ui.theme.STandTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BoyisoScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun firstScreenChoosesRoleNameAndQrMethodWithoutConnectionStart() {
        var createCount = 0
        var scanCount = 0

        composeRule.setContent {
            STandTheme {
                BoyisoScreen(
                    state = BoyisoState(
                        configuration = BoyisoConfiguration(
                            role = BoyisoRole.VIEWER,
                            deviceName = "엄마",
                        ),
                    ),
                    invitationUri = null,
                    onUpdateConfiguration = {},
                    onCreateRoom = { createCount += 1 },
                    onScanInvitation = { scanCount += 1 },
                    onShareInvitation = {},
                    onStart = {},
                    onLeaveRoom = {},
                    onTokTok = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("볼 사람").assertIsDisplayed()
        composeRule.onNodeWithText("말할 사람").assertIsDisplayed()
        composeRule.onNodeWithText("무전기").assertIsDisplayed()
        composeRule.onNodeWithText("2. 내 이름").assertIsDisplayed()
        composeRule.onAllNodesWithText("연결 시작").assertCountEquals(0)
        composeRule.onNode(hasClickAction() and hasText("공간 만들기", substring = true)).performClick()
        composeRule.onNode(hasClickAction() and hasText("공간 입장", substring = true)).performClick()

        assertEquals(1, createCount)
        assertEquals(1, scanCount)
    }

    @Test
    fun connectedWalkieShowsCallButtonAndWalkieParticipantSection() {
        var walkieCount = 0
        composeRule.setContent {
            STandTheme {
                BoyisoScreen(
                    state = BoyisoState(
                        localDeviceId = "walkie",
                        configuration = BoyisoConfiguration(
                            role = BoyisoRole.WALKIE,
                            roomId = "room",
                            roomKey = "12345678901234567890123456789012",
                            deviceName = "현관",
                        ),
                        running = true,
                    ),
                    invitationUri = null,
                    onUpdateConfiguration = {},
                    onCreateRoom = {},
                    onScanInvitation = {},
                    onShareInvitation = {},
                    onStart = {},
                    onLeaveRoom = {},
                    onTokTok = {},
                    onWalkie = { walkieCount += 1 },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("무전기 호출", substring = true).performClick()
        composeRule.onNodeWithText("누르면 연결된 화면이 크게 반응합니다.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("무전기 1명").performScrollTo().assertIsDisplayed()
        assertEquals(1, walkieCount)
    }

    @Test
    fun createdRoomShowsConnectedWaitingLeaveAndQrPhotoShare() {
        var leaveCount = 0
        var shareCount = 0

        composeRule.setContent {
            STandTheme {
                BoyisoScreen(
                    state = BoyisoState(
                        configuration = BoyisoConfiguration(
                            role = BoyisoRole.VIEWER,
                            roomId = "room",
                            roomKey = "12345678901234567890123456789012",
                            canInvite = true,
                            deviceName = "엄마",
                        ),
                        running = true,
                    ),
                    invitationUri = "stand://boyiso?v=2&room=room&key=key",
                    onUpdateConfiguration = {},
                    onCreateRoom = {},
                    onScanInvitation = {},
                    onShareInvitation = { shareCount += 1 },
                    onStart = {},
                    onLeaveRoom = { leaveCount += 1 },
                    onTokTok = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("공간에 연결됨").assertIsDisplayed()
        composeRule.onNodeWithText("사람을 기다리고 있습니다.").assertIsDisplayed()
        composeRule.onAllNodesWithText("연결 자세히 보기").assertCountEquals(0)
        composeRule.onAllNodesWithText("연결 자세히 닫기").assertCountEquals(0)
        composeRule.onNodeWithText("톡톡 보내기", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("우리 공간 QR코드").assertIsDisplayed()
        composeRule.onNode(hasClickAction() and hasText("QR 사진 보내기", substring = true))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("공간에 있는 사람").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("나를 포함해 총 1명").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("볼 사람 1명").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("말할 사람 0명").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("무전기 0명").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("아직 없음").assertCountEquals(2)
        composeRule.onNodeWithText("공간에서 나오기").performScrollTo().performClick()
        composeRule.onNodeWithText("나오기").performClick()
        composeRule.waitForIdle()

        assertEquals(1, shareCount)
        assertEquals(1, leaveCount)
    }

    @Test
    fun connectedPeopleIncludeMeAndSeparateViewerAndSpeakerGroups() {
        composeRule.setContent {
            STandTheme {
                BoyisoScreen(
                    state = BoyisoState(
                        localDeviceId = "local",
                        configuration = BoyisoConfiguration(
                            role = BoyisoRole.VIEWER,
                            roomId = "room",
                            roomKey = "12345678901234567890123456789012",
                            deviceName = "엄마",
                        ),
                        running = true,
                        devices = listOf(
                            BoyisoDevice(
                                id = "viewer",
                                name = "아빠",
                                role = BoyisoRole.VIEWER,
                                batteryPercent = 82,
                                monitoring = false,
                                lastSeenMillis = 1L,
                                displayMode = EnvironmentDisplayMode.MATE,
                                sessionActive = true,
                                transportPaths = setOf("LAN"),
                            ),
                            BoyisoDevice(
                                id = "speaker",
                                name = "침실폰",
                                role = BoyisoRole.SPEAKER,
                                batteryPercent = 71,
                                monitoring = true,
                                lastSeenMillis = 1L,
                                displayMode = EnvironmentDisplayMode.MATE,
                                sessionActive = true,
                                transportPaths = setOf("LAN"),
                            ),
                            BoyisoDevice(
                                id = "speaker",
                                name = "침실폰",
                                role = BoyisoRole.SPEAKER,
                                batteryPercent = 71,
                                monitoring = true,
                                lastSeenMillis = 2L,
                                displayMode = EnvironmentDisplayMode.MATE,
                                sessionActive = true,
                                transportPaths = setOf("BLE"),
                            ),
                            BoyisoDevice(
                                id = "speaker",
                                name = "침실폰",
                                role = BoyisoRole.SPEAKER,
                                batteryPercent = 71,
                                monitoring = true,
                                lastSeenMillis = 3L,
                                displayMode = EnvironmentDisplayMode.MATE,
                                sessionActive = true,
                                transportPaths = setOf("INTERNET"),
                            ),
                            BoyisoDevice(
                                id = "speaker-2",
                                name = "침실폰",
                                role = BoyisoRole.SPEAKER,
                                batteryPercent = 64,
                                monitoring = false,
                                lastSeenMillis = 3L,
                                displayMode = EnvironmentDisplayMode.MATE,
                                sessionActive = true,
                                transportPaths = setOf("INTERNET"),
                            ),
                            BoyisoDevice(
                                id = "local",
                                name = "엄마",
                                role = BoyisoRole.VIEWER,
                                batteryPercent = 90,
                                monitoring = false,
                                lastSeenMillis = 2L,
                                displayMode = EnvironmentDisplayMode.MATE,
                                sessionActive = true,
                                transportPaths = setOf("BLE"),
                            ),
                        ),
                    ),
                    invitationUri = null,
                    onUpdateConfiguration = {},
                    onCreateRoom = {},
                    onScanInvitation = {},
                    onShareInvitation = {},
                    onStart = {},
                    onLeaveRoom = {},
                    onTokTok = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("공간에 있는 사람").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("나를 포함해 총 4명").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("볼 사람 2명").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("말할 사람 2명").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("무전기 0명").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("엄마").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("이 기기").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("침실폰").assertCountEquals(2)
        composeRule.onNodeWithText("감지 중").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Wi‑Fi").assertCountEquals(2)
        composeRule.onAllNodesWithText("Bluetooth").assertCountEquals(2)
        composeRule.onAllNodesWithText("인터넷").assertCountEquals(2)
        composeRule.onNodeWithContentDescription(
            "침실폰, 말할 사람, 감지 중, 배터리 71퍼센트, Wi‑Fi 및 Bluetooth 및 인터넷",
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "침실폰, 말할 사람, 대기 중, 배터리 64퍼센트, 인터넷",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun joinedParticipantCanRenameAndShareRoomQrBelowPeople() {
        var updatedName = ""
        var shareCount = 0
        composeRule.setContent {
            STandTheme {
                BoyisoScreen(
                    state = BoyisoState(
                        localDeviceId = "joined",
                        configuration = BoyisoConfiguration(
                            role = BoyisoRole.SPEAKER,
                            roomId = "room",
                            roomKey = "12345678901234567890123456789012",
                            canInvite = false,
                            deviceName = "침실폰",
                        ),
                        running = true,
                    ),
                    invitationUri = "stand://boyiso?v=2&room=room&key=12345678901234567890123456789012",
                    onUpdateConfiguration = { updatedName = it.deviceName },
                    onCreateRoom = {},
                    onScanInvitation = {},
                    onShareInvitation = { shareCount += 1 },
                    onStart = {},
                    onLeaveRoom = {},
                    onTokTok = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("내 이름 수정").performScrollTo().performClick()
        composeRule.onNodeWithText("내 이름").performTextClearance()
        composeRule.onNodeWithText("내 이름").performTextInput("아기방 폰")
        composeRule.onNodeWithText("저장").performClick()
        composeRule.onNodeWithText("공간에 있는 사람").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("우리 공간 QR코드").performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasClickAction() and hasText("QR 사진 보내기", substring = true))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals("아기방 폰", updatedName)
        assertEquals(1, shareCount)
    }

    @Test
    fun inactiveRoomDisplaysStartButtonAndTriggersStartAction() {
        var startCount = 0
        composeRule.setContent {
            STandTheme {
                BoyisoScreen(
                    state = BoyisoState(
                        configuration = BoyisoConfiguration(
                            role = BoyisoRole.VIEWER,
                            roomId = "room",
                            roomKey = "12345678901234567890123456789012",
                            deviceName = "엄마",
                        ),
                        running = false,
                    ),
                    invitationUri = null,
                    onUpdateConfiguration = {},
                    onCreateRoom = {},
                    onScanInvitation = {},
                    onShareInvitation = {},
                    onStart = { startCount += 1 },
                    onLeaveRoom = {},
                    onTokTok = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("감시를 시작하지 않았습니다").assertIsDisplayed()
        composeRule.onNodeWithText("돌봄 연결 시작").performClick()
        assertEquals(1, startCount)
    }

    @Test
    fun deniedPermissionDisplaysErrorMessageAndSettingsAction() {
        var settingsCount = 0
        composeRule.setContent {
            STandTheme {
                BoyisoScreen(
                    state = BoyisoState(
                        configuration = BoyisoConfiguration(
                            role = BoyisoRole.SPEAKER,
                            roomId = "room",
                            roomKey = "12345678901234567890123456789012",
                            deviceName = "침실폰",
                        ),
                        running = false,
                        issueMessage = "마이크 권한을 허용해 주세요. 설정에서 켤 수 있습니다.",
                    ),
                    invitationUri = null,
                    onUpdateConfiguration = {},
                    onCreateRoom = {},
                    onScanInvitation = {},
                    onShareInvitation = {},
                    onStart = {},
                    onLeaveRoom = {},
                    onTokTok = {},
                    onOpenAppSettings = { settingsCount += 1 },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("돌봄 연결 필요").assertIsDisplayed()
        composeRule.onNodeWithText("마이크 권한을 허용해 주세요. 설정에서 켤 수 있습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("돌봄 연결 시작").assertIsDisplayed()
        composeRule.onNodeWithText("앱 설정에서 권한 허용").performClick()
        assertEquals(1, settingsCount)
    }

    @Test
    fun cryingChildAlertNamesTheSpeakerDevice() {
        composeRule.setContent {
            STandTheme {
                CryingChildAlertOverlay(senderName = "침실폰")
            }
        }

        composeRule.onNodeWithContentDescription("침실폰, 말할 사람의 소리가 감지되었습니다")
            .assertExists()
        composeRule.onNodeWithText("침실폰").assertIsDisplayed()
        composeRule.onNodeWithText("말할 사람의 소리가 감지되었습니다.").assertIsDisplayed()
    }
}
