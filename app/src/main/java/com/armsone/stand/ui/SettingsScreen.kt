@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.armsone.stand.ui

import androidx.annotation.RawRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsone.stand.BuildConfig
import com.armsone.stand.R
import com.armsone.stand.model.AppSettings
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockVisualPolicy
import com.armsone.stand.model.SettingsInformationArchitecture
import com.armsone.stand.model.SettingsSectionKind
import com.armsone.stand.model.StandDisplayTheme
import com.armsone.stand.model.StandModePreference
import com.armsone.stand.platform.AmbientCameraState
import com.armsone.stand.platform.InternetRadioState
import com.armsone.stand.ui.components.flipTextSplitMask
import com.armsone.stand.ui.components.standPanelSurface
import com.armsone.stand.ui.theme.fontFamily
import com.armsone.stand.ui.theme.fontWeight
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: StandUiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onModePreferenceSelected: (StandModePreference) -> Unit,
    onRestoreRecommended: () -> Unit,
    onToggleInternetRadio: (String) -> Unit,
    onSaveInternetRadio: (String?, String, String) -> String?,
    onDeleteInternetRadio: (String) -> Unit,
    onManageInternetRadios: () -> Unit,
    onOpenInternetRadioBrowser: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenBoyiso: () -> Unit,
    boyisoStatus: String,
    onRequestMicrophonePermission: () -> Unit,
    onRequestApproximateLocationPermission: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onCameraAmbientSensingChanged: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var showResetConfirmation by remember { mutableStateOf(false) }
    var pendingRadioDeletionID by remember { mutableStateOf<String?>(null) }
    var selectedLicense by remember { mutableStateOf<ClockFontChoice?>(null) }
    var editingRadioID by remember { mutableStateOf<String?>(null) }
    var addingRadio by remember { mutableStateOf(false) }
    var radioDraftName by remember { mutableStateOf("") }
    var radioDraftURL by remember { mutableStateOf("") }
    var radioValidationMessage by remember { mutableStateOf<String?>(null) }
    val settings = state.settings
    val settingsBackground = Brush.linearGradient(
        listOf(
            lerp(Color(0xFF1D1614), MaterialTheme.colorScheme.primary, 0.20f),
            Color(0xFF291D1A),
            Color(0xFF161313),
        ),
    )
    val missingPermissionCount = listOf(
        settings.soundSensingEnabled && !state.hasMicrophonePermission,
        settings.weatherLocationEnabled && !state.hasApproximateLocationPermission,
        settings.cameraAmbientSensingEnabled && !state.hasCameraPermission,
    ).count { it }
    val cameraAmbientDetail = when (state.ambientCameraState) {
        AmbientCameraState.DISABLED -> "필요할 때 약 1초 동안 밝기만 계산"
        AmbientCameraState.PERMISSION_NEEDED -> "카메라 권한 필요"
        AmbientCameraState.DENIED -> "카메라 권한이 거부됨"
        AmbientCameraState.MEASURING -> "평균 밝기 확인 중"
        AmbientCameraState.READY -> state.ambientCameraBrightness?.let { value ->
            "최근 측정 ${(value * 100).roundToInt()}%"
        } ?: "측정 준비됨"
        AmbientCameraState.UNAVAILABLE -> "카메라를 사용할 수 없음"
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(settingsBackground),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("에스텐드 설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ),
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val columnCount = if (maxWidth >= 720.dp) 2 else 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (maxWidth >= 720.dp) 24.dp else 14.dp),
                contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SettingsHero(
                        state = state,
                        onModePreferenceSelected = onModePreferenceSelected,
                    )
                }

                items(
                    count = SettingsInformationArchitecture.CardOrder.size,
                    key = { index -> SettingsInformationArchitecture.CardOrder[index].name },
                    span = { index ->
                        if (SettingsInformationArchitecture.CardOrder[index] == SettingsSectionKind.INTERNET_RADIO) {
                            GridItemSpan(maxLineSpan)
                        } else {
                            GridItemSpan(1)
                        }
                    },
                ) { index ->
                    when (val section = SettingsInformationArchitecture.CardOrder[index]) {
                    SettingsSectionKind.INTERNET_RADIO -> SettingsCard(
                        title = "인터넷 라디오",
                        subtitle = if (settings.internetRadioChannels.isEmpty()) {
                            "이 화면에서 채널을 추가하고 바로 재생합니다."
                        } else {
                            "${settings.internetRadioChannels.size}개 채널 · 최대 2개"
                        },
                        icon = Icons.Default.Radio,
                    ) {
                        settings.internetRadioChannels.forEach { channel ->
                            val active = when (val radioState = state.internetRadioState) {
                                is InternetRadioState.Loading -> radioState.channelID == channel.id
                                is InternetRadioState.Playing -> radioState.channelID == channel.id
                                is InternetRadioState.Reconnecting -> radioState.channelID == channel.id
                                else -> false
                            }
                            val status = when (val radioState = state.internetRadioState) {
                                is InternetRadioState.Loading -> if (active) "연결 중" else "대기 중"
                                is InternetRadioState.Playing -> if (active) "재생 중" else "대기 중"
                                is InternetRadioState.Reconnecting -> if (active) {
                                    "${radioState.delaySeconds}초 뒤 재연결"
                                } else {
                                    "대기 중"
                                }
                                is InternetRadioState.Failed -> radioState.message
                                InternetRadioState.Idle -> "대기 중"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(
                                    onClick = { onToggleInternetRadio(channel.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(channel.displayName, fontWeight = FontWeight.SemiBold)
                                        Text(status, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                TextButton(onClick = {
                                    editingRadioID = channel.id
                                    addingRadio = false
                                    radioDraftName = channel.displayName
                                    radioDraftURL = channel.streamUrl
                                    radioValidationMessage = null
                                }) { Text("수정") }
                            }
                            if (editingRadioID == channel.id) {
                                InlineRadioEditor(
                                    name = radioDraftName,
                                    url = radioDraftURL,
                                    error = radioValidationMessage,
                                    onNameChange = {
                                        radioDraftName = it.take(30)
                                        radioValidationMessage = null
                                    },
                                    onUrlChange = {
                                        radioDraftURL = it.take(2_048)
                                        radioValidationMessage = null
                                    },
                                    onSave = {
                                        radioValidationMessage = onSaveInternetRadio(
                                            channel.id,
                                            radioDraftName,
                                            radioDraftURL,
                                        )
                                        if (radioValidationMessage == null) editingRadioID = null
                                    },
                                    onDelete = {
                                        pendingRadioDeletionID = channel.id
                                    },
                                    onClose = { editingRadioID = null },
                                )
                            }
                        }
                        if (addingRadio) {
                            InlineRadioEditor(
                                name = radioDraftName,
                                url = radioDraftURL,
                                error = radioValidationMessage,
                                onNameChange = {
                                    radioDraftName = it.take(30)
                                    radioValidationMessage = null
                                },
                                onUrlChange = {
                                    radioDraftURL = it.take(2_048)
                                    radioValidationMessage = null
                                },
                                onSave = {
                                    radioValidationMessage = onSaveInternetRadio(
                                        null,
                                        radioDraftName,
                                        radioDraftURL,
                                    )
                                    if (radioValidationMessage == null) addingRadio = false
                                },
                                onDelete = null,
                                onClose = { addingRadio = false },
                            )
                        } else if (
                            editingRadioID == null &&
                            settings.internetRadioChannels.size <
                            AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT
                        ) {
                            TextButton(
                                onClick = {
                                    addingRadio = true
                                    radioDraftName = ""
                                    radioDraftURL = ""
                                    radioValidationMessage = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (settings.internetRadioChannels.isEmpty()) "첫 채널 추가" else "채널 추가") }
                        }
                        TextButton(
                            onClick = onManageInternetRadios,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Radio, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("채널 관리")
                        }
                        TextButton(
                            onClick = onOpenInternetRadioBrowser,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("웹에서 주소 찾기")
                        }
                        Text(
                            "브라우저는 스트리밍 주소를 자동으로 감지하거나 채널에 입력하지 않습니다. 이용 권한이 있는 주소를 직접 복사한 뒤 채널 추가 화면에서 붙여넣어 주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "최대 두 채널을 저장합니다. 현재 선택 채널은 홈에서 재생할 수 있고, 연결이 끊기면 자동 재연결합니다. 재생 중에는 소리 감지와 녹음을 잠시 멈춥니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SettingsSectionKind.SCREEN_AND_CLOCK -> SettingsCard(
                        title = "화면과 시계",
                        subtitle = "테마와 시계 글꼴을 바꿉니다.",
                        icon = Icons.Default.TextFields,
                    ) {
                        Text(
                            "테마",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "홈 화면을 더블 터치하면 테마가 바뀝니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StandDisplayTheme.entries.forEach { theme ->
                                FilterChip(
                                    selected = settings.displayTheme == theme,
                                    onClick = { onUpdate { it.copy(displayTheme = theme) } },
                                    label = {
                                        Text(theme.title)
                                    },
                                )
                            }
                        }
                        Text(
                            "시계 글꼴",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "실제 플립시계 모양을 눌러 선택하세요 · ${settings.clockFont.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ClockFontPreviewGrid(
                            selectedFont = settings.clockFont,
                            onFontSelected = { font -> onUpdate { it.copy(clockFont = font) } },
                        )
                        Text(
                            "홈 화면을 길게 누르면 시계와 날씨 같은 정보 패널을 편집할 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SettingsSectionKind.PERMISSIONS -> SettingsCard(
                        title = "권한 설정",
                        subtitle = "필요한 기능만 선택해서 사용합니다.",
                        icon = Icons.Default.Security,
                    ) {
                        LabeledSwitch(
                            title = "플래시 사용",
                            detail = if (state.torchAvailable) {
                                "화들짝 모드에서만 켜짐"
                            } else {
                                "이 기기에서는 사용할 수 없음"
                            },
                            checked = settings.torchEnabled,
                        ) { enabled -> onUpdate { it.copy(torchEnabled = enabled) } }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        LabeledSwitch(
                            title = "카메라 사용",
                            detail = cameraAmbientDetail,
                            checked = settings.cameraAmbientSensingEnabled,
                        ) { enabled -> onCameraAmbientSensingChanged(enabled) }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        LabeledSwitch(
                            title = "마이크 사용",
                            detail = when {
                                !settings.soundSensingEnabled -> "사용하지 않음"
                                state.hasMicrophonePermission -> "매이트 모드의 잠꼬대·코골이 감지와 기기 내 저장"
                                else -> "마이크 권한 필요"
                            },
                            checked = settings.soundSensingEnabled && state.hasMicrophonePermission,
                        ) { enabled ->
                            onUpdate { it.copy(soundSensingEnabled = enabled) }
                            if (enabled && !state.hasMicrophonePermission) {
                                onRequestMicrophonePermission()
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        LabeledSwitch(
                            title = "위치 정보 사용",
                            detail = when {
                                !settings.weatherLocationEnabled -> "사용하지 않음"
                                state.hasApproximateLocationPermission -> "현재 위치의 날씨를 표시할 때만 사용"
                                else -> "위치 권한 필요"
                            },
                            checked = settings.weatherLocationEnabled &&
                                state.hasApproximateLocationPermission,
                        ) { enabled ->
                            onUpdate { it.copy(weatherLocationEnabled = enabled) }
                            if (enabled && !state.hasApproximateLocationPermission) {
                                onRequestApproximateLocationPermission()
                            }
                        }
                        if (!state.hasCameraPermission && settings.cameraAmbientSensingEnabled) {
                            TextButton(onClick = onRequestCameraPermission) {
                                Text("카메라 권한 다시 요청")
                            }
                        }
                        if (missingPermissionCount > 0) {
                            TextButton(
                                onClick = onOpenAppSettings,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("시스템 앱 권한 설정 열기")
                            }
                        }
                    }

                    SettingsSectionKind.BOYISO -> SettingsCard(
                        title = "보이소",
                        subtitle = "BOISO · 보이는 소리",
                        icon = Icons.Default.ChildCare,
                    ) {
                        Text(
                            boyisoStatus,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "볼 사람과 말할 사람을 QR로 연결하고 톡톡을 보낼 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onOpenBoyiso,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("보이소 열기")
                        }
                    }

                    SettingsSectionKind.SLEEP_SOUNDS -> SettingsCard(
                        title = "잠꼬대와 코골이",
                        subtitle = "매이트 모드에서만 작동합니다.",
                        icon = Icons.Default.GraphicEq,
                    ) {
                        val audioStatus = when {
                            !state.isSessionActive -> "감지 멈춤"
                            state.experienceMode ==
                                com.armsone.stand.model.StandExperienceMode.OBJECT -> "오브제 모드"
                            state.isWritingClip -> "소리 저장 중"
                            state.audioRunning -> "소리 감지 중"
                            else -> "마이크 대기"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SettingsAudioLevelMeter(state.audioLevel)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(audioStatus, fontWeight = FontWeight.SemiBold)
                                Text(
                                    state.audioMessage ?: "현재 레벨 ${(state.audioLevel * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        LabeledSwitch(
                            title = "다시 밝혀주기",
                            detail = "박수, 핑거스냅, 뒤척임과 기기 움직임에 반응",
                            checked = settings.multiStimulusWakeEnabled,
                        ) { enabled -> onUpdate { it.copy(multiStimulusWakeEnabled = enabled) } }
                        LabeledSwitch(
                            title = "코골이·잠꼬대 저장",
                            detail = "후보 소리가 날 때 필요한 구간만 기기에 저장",
                            checked = settings.recordingEnabled,
                        ) { enabled -> onUpdate { it.copy(recordingEnabled = enabled) } }
                        TextButton(
                            onClick = onOpenRecordings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (state.recordingCount == 0) {
                                    "수면 소리 열기"
                                } else {
                                    "녹음 ${state.recordingCount}개 보기"
                                },
                            )
                        }
                        Text(
                            "처음에는 방의 평소 소리를 익히고, 후보 녹음은 이 기기 안에서만 처리합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SettingsSectionKind.INFORMATION -> SettingsCard(
                        title = "정보",
                        subtitle = "개인정보, 저작권과 앱 정보를 확인합니다.",
                        icon = Icons.Default.Info,
                    ) {
                        Text(
                            "오디오는 이 기기에서 처리하고 로컬에만 저장합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "함께 있는 사람에게 녹음 사실을 먼저 알려 주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "충전 중인 기기와 플래시를 침구로 덮지 마세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Text("에스텐드 · S.tand ${BuildConfig.VERSION_NAME}")
                        Text(
                            "빌드 ${BuildConfig.BUILD_NUMBER} · versionCode ${BuildConfig.VERSION_CODE}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("시스템 글꼴 + 번들 글꼴 9종", style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = { uriHandler.openUri("https://open-meteo.com/") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("날씨 데이터 · Open-Meteo")
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ClockFontChoice.entries.filterNot {
                                it == ClockFontChoice.SYSTEM_ROUNDED
                            }.forEach { font ->
                                AssistChip(
                                    onClick = { selectedLicense = font },
                                    label = { Text(font.displayName) },
                                )
                            }
                        }
                        TextButton(
                            onClick = { showResetConfirmation = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Text(" 추천 설정 복원")
                        }
                    }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("추천 설정으로 되돌릴까요?") },
            text = { Text("저장한 라디오 채널을 포함해 앱 설정이 처음 모습으로 돌아갑니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmation = false
                    onRestoreRecommended()
                }) { Text("추천 설정 복원") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text("취소") }
            },
        )
    }

    pendingRadioDeletionID?.let { channelID ->
        val channelName = settings.internetRadioChannels
            .firstOrNull { it.id == channelID }
            ?.displayName
            .orEmpty()
        AlertDialog(
            onDismissRequest = { pendingRadioDeletionID = null },
            title = { Text("${channelName}을 삭제할까요?") },
            text = { Text("삭제한 채널 주소는 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteInternetRadio(channelID)
                        editingRadioID = null
                        pendingRadioDeletionID = null
                    },
                ) { Text("채널 삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRadioDeletionID = null }) { Text("취소") }
            },
        )
    }

    selectedLicense?.let { font ->
        FontLicenseDialog(font = font, onDismiss = { selectedLicense = null })
    }
}

@Composable
private fun SettingsHero(
    state: StandUiState,
    onModePreferenceSelected: (StandModePreference) -> Unit,
) {
    SettingsCard(
        title = "에스텐드",
        subtitle = "낮에는 오브제 · 밤에는 매이트",
        icon = Icons.Default.DarkMode,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (state.isSessionActive) {
                        "현재 상태, ${state.experienceMode.title}"
                    } else {
                        "현재 상태, 에스텐드 멈춤"
                    }
                },
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (state.isSessionActive) state.experienceMode.title else "에스텐드 멈춤")
            }
        }
        Text(
            "화면 모드 유지",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            StandModePreference.entries.forEachIndexed { index, preference ->
                SegmentedButton(
                    selected = state.settings.modePreference == preference,
                    onClick = { onModePreferenceSelected(preference) },
                    enabled = state.isSessionActive,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = StandModePreference.entries.size,
                    ),
                ) {
                    Text(preference.title)
                }
            }
        }
        Text(
            "자동 전환 또는 오브제와 매이트 모드 유지를 선택합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsAudioLevelMeter(level: Float) {
    val safeLevel = level.coerceIn(0f, 1f)
    val fillHeight by animateDpAsState(
        targetValue = maxOf(4f, 58f * safeLevel).dp,
        animationSpec = tween(120),
        label = "audio-level",
    )
    Box(
        modifier = Modifier
            .width(12.dp)
            .height(58.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
            .semantics {
                contentDescription = "감지 레벨 ${(safeLevel * 100).roundToInt()}퍼센트"
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fillHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                        ),
                    ),
                    RoundedCornerShape(50),
                ),
        )
    }
}

@Composable
private fun InlineRadioEditor(
    name: String,
    url: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .standPanelSurface(isDimmed = false, cornerRadius = 14.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("표시 이름") },
            singleLine = true,
        )
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("HTTPS 스트림 주소") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { message -> { Text(message) } },
        )
        TextButton(
            onClick = {
                clipboardManager.getText()?.text?.let { pasted ->
                    onUrlChange(pasted.take(2_048))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("복사한 주소 붙여넣기")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            onDelete?.let { delete ->
                TextButton(onClick = delete) { Text("삭제") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("닫기") }
            TextButton(onClick = onSave) { Text("저장") }
        }
    }
}

@Composable
private fun ClockFontPreviewGrid(
    selectedFont: ClockFontChoice,
    onFontSelected: (ClockFontChoice) -> Unit,
) {
    val isLargeScreen = LocalConfiguration.current.screenWidthDp >= 600
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnCount = if (isLargeScreen) 5 else 3
        val spacing = 8.dp
        val tileWidth = (
            (maxWidth.value - spacing.value * (columnCount - 1)) / columnCount
        ).dp - 1.dp
        val previewFontSize = when {
            isLargeScreen -> 28.sp
            tileWidth < 88.dp -> 21.sp
            else -> 24.sp
        }
        val density = LocalDensity.current
        val previewVisualFontSize = with(density) {
            previewFontSize.toPx() / this.density
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            maxItemsInEachRow = columnCount,
        ) {
            ClockFontChoice.entries.forEach { font ->
                val isSelected = selectedFont == font
                val previewFontFamily = font.fontFamily()
                val previewVerticalOffset = ClockVisualPolicy.verticalOffset(
                    font = font,
                    fontSize = previewVisualFontSize,
                ).dp
                Card(
                    onClick = { onFontSelected(font) },
                    modifier = Modifier
                        .width(tileWidth)
                        .heightIn(min = 72.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription =
                                "${font.displayName} 글꼴, 12시 34분 미리보기"
                            selected = isSelected
                            role = Role.RadioButton
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                        },
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            listOf("12", "34").forEachIndexed { index, digits ->
                                if (index > 0) {
                                    Text(
                                        text = ":",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = previewFontFamily,
                                        fontSize = previewFontSize,
                                        fontWeight = font.fontWeight(),
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.offset(y = previewVerticalOffset),
                                    )
                                }
                                SettingsMiniFlipCard(
                                    digits = digits,
                                    font = font,
                                    fontSize = previewFontSize,
                                    verticalOffset = previewVerticalOffset,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMiniFlipCard(
    digits: String,
    font: ClockFontChoice,
    fontSize: TextUnit,
    verticalOffset: Dp,
    modifier: Modifier = Modifier,
) {
    val splitGap = 2.dp
    Box(
        modifier = modifier.standPanelSurface(
            isDimmed = false,
            cornerRadius = 9.dp,
            splitGap = splitGap,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .flipTextSplitMask(splitGap),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = digits,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = font.fontFamily(),
                fontSize = fontSize,
                fontWeight = font.fontWeight(),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset(y = verticalOffset),
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun LabeledSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $detail"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FontLicenseDialog(font: ClockFontChoice, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val license = remember(font) {
        font.licenseResource()?.let { resource ->
            runCatching {
                context.resources.openRawResource(resource).bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: "라이선스 원문을 불러올 수 없습니다."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${font.displayName} 라이선스") },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp)) {
                item {
                    Text(
                        license,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@RawRes
private fun ClockFontChoice.licenseResource(): Int? = when (this) {
    ClockFontChoice.SYSTEM_ROUNDED -> null
    ClockFontChoice.PRETENDARD -> R.raw.pretendard_license
    ClockFontChoice.KAKAO_BIG_SANS -> R.raw.kakao_big_sans_ofl
    ClockFontChoice.NANUM_GOTHIC -> R.raw.nanum_gothic_ofl
    ClockFontChoice.TENADA -> R.raw.tenada_license
    ClockFontChoice.BLACK_HAN_SANS -> R.raw.black_han_sans_ofl
    ClockFontChoice.DO_HYEON -> R.raw.do_hyeon_ofl
    ClockFontChoice.PAPERLOGY_BOLD -> R.raw.paperlogy_ofl
    ClockFontChoice.NEXON_LV1_GOTHIC -> R.raw.nexon_lv1_gothic_license
    ClockFontChoice.POPPINS -> R.raw.poppins_ofl
}
