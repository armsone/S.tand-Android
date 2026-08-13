package com.armsone.stand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.armsone.stand.model.OrientationPreference
import com.armsone.stand.model.StandControlKind
import kotlin.math.roundToInt

internal data class StandControlPresentation(
    val title: String,
    val status: String,
    val icon: ImageVector,
)

internal fun StandControlKind.presentation(
    state: StandUiState,
    boyisoStatus: String = "설정 필요",
): StandControlPresentation =
    when (this) {
        StandControlKind.FLASHLIGHT -> StandControlPresentation(
            title = "플래시",
            status = when {
                !state.torchAvailable -> "플래시 없음"
                !state.hasCameraPermission -> "카메라 권한 필요"
                state.settings.torchEnabled -> "연동"
                else -> "보조 10%"
            },
            icon = Icons.Default.FlashlightOn,
        )

        StandControlKind.BRIGHTNESS -> StandControlPresentation(
            title = state.settings.modePreference.title,
            status = state.rawAmbientLux?.let { "${it.roundToInt()} lx" }
                ?: "밝기 ${(state.normalizedAmbientLight?.times(100))?.roundToInt() ?: "--"}%",
            icon = Icons.Default.WbSunny,
        )

        StandControlKind.STOP_DETECTION -> StandControlPresentation(
            title = if (state.isSessionActive) "자동 녹음 끄기" else "자동 녹음 시작",
            status = if (state.isSessionActive) "감지 종료" else "대기 중",
            icon = if (state.isSessionActive) Icons.Default.StopCircle else Icons.Default.PlayArrow,
        )

        StandControlKind.ORIENTATION -> StandControlPresentation(
            title = if (
                state.settings.orientationPreference == OrientationPreference.AUTOMATIC
            ) {
                "현재 방향 고정"
            } else {
                "기기 회전 따르기"
            },
            status = state.settings.orientationPreference.title,
            icon = if (
                state.settings.orientationPreference == OrientationPreference.AUTOMATIC
            ) {
                Icons.Default.Lock
            } else {
                Icons.AutoMirrored.Filled.RotateRight
            },
        )

        StandControlKind.RECORDINGS -> StandControlPresentation(
            title = "녹음 목록",
            status = "${state.recordingCount}개 녹음",
            icon = Icons.Default.Mic,
        )

        StandControlKind.AI_SHOT -> StandControlPresentation(
            title = "AiShot 실행",
            status = "HanClip",
            icon = Icons.Default.CameraAlt,
        )

        StandControlKind.SETTINGS -> StandControlPresentation(
            title = "설정 열기",
            status = "화면·감지",
            icon = Icons.Default.Settings,
        )

        StandControlKind.BOYISO -> StandControlPresentation(
            title = "보이소",
            status = boyisoStatus,
            icon = Icons.Default.Pets,
        )
    }

/** The exact live tile content shared by the home and its direct reorder editor. */
@Composable
internal fun StandControlTileContent(
    presentation: StandControlPresentation,
    showReorderHandle: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 7.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = presentation.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = presentation.title,
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = presentation.status,
                color = Color.White.copy(alpha = 0.42f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }

        if (showReorderHandle) {
            Text(
                text = "≡",
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
