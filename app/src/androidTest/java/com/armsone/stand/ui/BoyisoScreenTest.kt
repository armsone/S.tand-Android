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
import com.armsone.stand.boyiso.BoyisoConfiguration
import com.armsone.stand.boyiso.BoyisoRole
import com.armsone.stand.boyiso.BoyisoState
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

        composeRule.onNodeWithText("볼사람").assertIsDisplayed()
        composeRule.onNodeWithText("말할사람").assertIsDisplayed()
        composeRule.onNodeWithText("2. 내 이름").assertIsDisplayed()
        composeRule.onAllNodesWithText("연결 시작").assertCountEquals(0)
        composeRule.onNode(hasClickAction() and hasText("공간 만들기", substring = true)).performClick()
        composeRule.onNode(hasClickAction() and hasText("공간 입장", substring = true)).performClick()

        assertEquals(1, createCount)
        assertEquals(1, scanCount)
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
        composeRule.onNodeWithText("우리 공간 QR코드").assertIsDisplayed()
        composeRule.onNodeWithText("QR 사진 보내기", substring = true).performScrollTo().performClick()
        composeRule.onNodeWithText("같은 공간안에 있는 사람").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("공간에서 나오기").performScrollTo().performClick()

        assertEquals(1, shareCount)
        assertEquals(1, leaveCount)
    }

    @Test
    fun cryingChildAlertNamesTheSpeakerDevice() {
        composeRule.setContent {
            STandTheme {
                CryingChildAlertOverlay(senderName = "침실폰")
            }
        }

        composeRule.onNodeWithContentDescription("침실폰 기기에서 큰소리가 계속 들립니다")
            .assertIsDisplayed()
        composeRule.onNodeWithText("침실폰 기기에서 큰소리가 들려요").assertIsDisplayed()
    }
}
