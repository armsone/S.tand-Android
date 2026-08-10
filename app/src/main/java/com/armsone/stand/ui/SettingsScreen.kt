@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.armsone.stand.ui

import androidx.annotation.RawRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.armsone.stand.model.ClockHourMode
import com.armsone.stand.model.ClockVisualPolicy
import com.armsone.stand.model.OrientationPreference
import com.armsone.stand.model.StandDisplayTheme
import com.armsone.stand.model.StandModePreference
import com.armsone.stand.platform.AmbientCameraState
import com.armsone.stand.ui.components.flipTextSplitMask
import com.armsone.stand.ui.components.standPanelSurface
import com.armsone.stand.ui.theme.fontFamily
import com.armsone.stand.ui.theme.fontWeight
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: StandUiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onRefreshWeather: () -> Unit,
    onRestoreRecommended: () -> Unit,
    onOpenScreenEditor: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    onRequestApproximateLocationPermission: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onCameraAmbientSensingChanged: (Boolean) -> Unit,
    onMeasureAmbientCameraBrightness: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetConfirmation by remember { mutableStateOf(false) }
    var selectedLicense by remember { mutableStateOf<ClockFontChoice?>(null) }
    val settings = state.settings
    val missingPermissionCount = listOf(
        state.hasMicrophonePermission,
        state.hasApproximateLocationPermission,
        state.hasCameraPermission,
    ).count { granted -> !granted }
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
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("S.tand 설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsCard(
                    title = "모드",
                    subtitle = "자동 또는 원하는 상태를 즉시 유지합니다.",
                    icon = Icons.Default.DarkMode,
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StandModePreference.entries.forEach { preference ->
                            FilterChip(
                                selected = settings.modePreference == preference,
                                onClick = { onUpdate { it.copy(modePreference = preference) } },
                                label = { Text(preference.title) },
                            )
                        }
                    }
                    Text(
                        text = "현재 ${state.experienceMode.title}" +
                            (state.rawAmbientLux?.let { " · ${it.roundToInt()} lx" } ?: ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                SettingsCard(
                    title = "화면과 시계",
                    subtitle = "테마, 시간제, 크기와 글꼴을 정합니다.",
                    icon = Icons.Default.TextFields,
                ) {
                    LabeledSwitch(
                        title = "그레이스케일 테마",
                        detail = "홈 화면을 두 번 눌러서도 바꿀 수 있어요.",
                        checked = settings.displayTheme == StandDisplayTheme.GRAYSCALE,
                    ) { enabled ->
                        onUpdate {
                            it.copy(
                                displayTheme = if (enabled) {
                                    StandDisplayTheme.GRAYSCALE
                                } else {
                                    StandDisplayTheme.COLOR
                                },
                            )
                        }
                    }
                    SettingSlider(
                        title = "시계 크기",
                        valueText = "${(settings.clockScale * 100).roundToInt()}%",
                        value = settings.clockScale,
                        range = 0.7f..1.35f,
                        onValueChange = { value -> onUpdate { it.copy(clockScale = value) } },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClockHourMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.clockHourMode == mode,
                                onClick = { onUpdate { it.copy(clockHourMode = mode) } },
                                label = {
                                    Text(if (mode == ClockHourMode.TWELVE) "12시간" else "24시간")
                                },
                            )
                        }
                    }
                    Text(
                        text = "시계 글꼴",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "실제 숫자 모양을 눌러 선택하세요 · ${settings.clockFont.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ClockFontPreviewGrid(
                        selectedFont = settings.clockFont,
                        onFontSelected = { font ->
                            onUpdate { it.copy(clockFont = font) }
                        },
                    )
                    TextButton(
                        onClick = onOpenScreenEditor,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Text(" 현재 방향 화면 편집")
                    }
                }
            }

            item {
                SettingsCard(
                    title = "조명",
                    subtitle = "화면 조명과 어두운 실루엣의 세기를 조절합니다.",
                    icon = Icons.Default.Lightbulb,
                ) {
                    SettingSlider(
                        title = "조명 밝기",
                        valueText = "${(settings.lampIntensity * 100).roundToInt()}%",
                        value = settings.lampIntensity,
                        range = 0.15f..1f,
                        onValueChange = { value -> onUpdate { it.copy(lampIntensity = value) } },
                    )
                    SettingSlider(
                        title = "실루엣 밝기",
                        valueText = "${(settings.silhouetteIntensity * 100).roundToInt()}%",
                        value = settings.silhouetteIntensity,
                        range = 0.005f..0.2f,
                        onValueChange = { value -> onUpdate { it.copy(silhouetteIntensity = value) } },
                    )
                    SettingSlider(
                        title = "점등 유지",
                        valueText = durationText(settings.holdDurationSeconds),
                        value = settings.holdDurationSeconds,
                        range = 5f..300f,
                        steps = 58,
                        onValueChange = { value ->
                            onUpdate { it.copy(holdDurationSeconds = (value / 5f).roundToInt() * 5f) }
                        },
                    )
                    SettingSlider(
                        title = "감광 시간",
                        valueText = "${settings.fadeDurationSeconds.roundToInt()}초",
                        value = settings.fadeDurationSeconds,
                        range = 1f..120f,
                        onValueChange = { value -> onUpdate { it.copy(fadeDurationSeconds = value) } },
                    )
                    LabeledSwitch(
                        title = "매이트 자동 감광",
                        detail = "오브제 모드는 직접 어둡게 할 때까지 밝게 유지됩니다.",
                        checked = settings.automaticDimmingEnabled,
                    ) { enabled -> onUpdate { it.copy(automaticDimmingEnabled = enabled) } }
                    LabeledSwitch(
                        title = "플래시 연동",
                        detail = if (state.torchAvailable) {
                            "매이트 점등에 후면 플래시를 함께 사용합니다."
                        } else {
                            "이 기기에는 사용할 수 있는 후면 플래시가 없습니다."
                        },
                        checked = settings.torchEnabled,
                    ) { enabled -> onUpdate { it.copy(torchEnabled = enabled) } }
                }
            }

            item {
                SettingsCard(
                    title = "감지와 녹음",
                    subtitle = "매이트 모드에서만 마이크와 움직임을 살핍니다.",
                    icon = Icons.Default.GraphicEq,
                ) {
                    LabeledSwitch(
                        title = "코골이·잠꼬대 후보 저장",
                        detail = "의료 진단이 아닌 기기 내 휴리스틱 분류입니다.",
                        checked = settings.recordingEnabled,
                    ) { enabled -> onUpdate { it.copy(recordingEnabled = enabled) } }
                    LabeledSwitch(
                        title = "소리·움직임으로 점등",
                        detail = "박수, 뒤척임 또는 기기 움직임에 반응합니다.",
                        checked = settings.multiStimulusWakeEnabled,
                    ) { enabled -> onUpdate { it.copy(multiStimulusWakeEnabled = enabled) } }
                    SettingSlider(
                        title = "마이크 감지 기준",
                        valueText = "${settings.soundThresholdDB.roundToInt()} dB",
                        value = settings.soundThresholdDB,
                        range = -55f..-18f,
                        steps = 36,
                        onValueChange = { value -> onUpdate { it.copy(soundThresholdDB = value) } },
                    )
                }
            }

            item {
                SettingsCard(
                    title = "주변 밝기와 날씨",
                    subtitle = "조도 센서와 선택한 카메라 보조, 대략적 위치를 사용합니다.",
                    icon = Icons.Default.Cloud,
                ) {
                    LabeledSwitch(
                        title = "조도 센서 자동 전환",
                        detail = "사진이나 영상을 만들지 않고 센서 값만 기기 안에서 사용합니다.",
                        checked = settings.ambientSensingEnabled,
                    ) { enabled -> onUpdate { it.copy(ambientSensingEnabled = enabled) } }
                    LabeledSwitch(
                        title = "카메라 밝기 보조",
                        detail = cameraAmbientDetail,
                        checked = settings.cameraAmbientSensingEnabled,
                    ) { enabled -> onCameraAmbientSensingChanged(enabled) }
                    if (settings.cameraAmbientSensingEnabled) {
                        TextButton(
                            onClick = onMeasureAmbientCameraBrightness,
                            enabled = state.ambientCameraState != AmbientCameraState.MEASURING,
                        ) {
                            Icon(Icons.Default.Camera, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.ambientCameraState == AmbientCameraState.MEASURING) {
                                    "확인 중"
                                } else {
                                    "지금 확인"
                                },
                            )
                        }
                        Text(
                            "사진과 영상은 저장하거나 전송하지 않습니다. 조도 센서가 없는 기기의 자동 판단을 보조합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingSlider(
                        title = "밝기 기준",
                        valueText = "${(settings.brightnessModeThreshold * 100).roundToInt()}%",
                        value = settings.brightnessModeThreshold,
                        range = 0f..1f,
                        onValueChange = { value ->
                            onUpdate { it.copy(brightnessModeThreshold = value) }
                        },
                    )
                    TextButton(onClick = onRefreshWeather) {
                        Text("현재 위치 날씨 새로고침")
                    }
                    state.weatherMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SettingsCard(
                    title = "앱 권한",
                    subtitle = if (missingPermissionCount == 0) {
                        "마이크, 대략적 위치, 카메라 권한이 모두 허용되었습니다."
                    } else {
                        "필요한 권한 ${missingPermissionCount}개가 허용되지 않았습니다."
                    },
                    icon = Icons.Default.Security,
                ) {
                    PermissionStatusRow(
                        title = "마이크",
                        detail = "매이트 모드의 소리 감지와 기기 내 수면 소리 저장",
                        granted = state.hasMicrophonePermission,
                        onRequest = onRequestMicrophonePermission,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    PermissionStatusRow(
                        title = "대략적 위치",
                        detail = "현재 위치의 날씨를 불러올 때만 사용",
                        granted = state.hasApproximateLocationPermission,
                        onRequest = onRequestApproximateLocationPermission,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    PermissionStatusRow(
                        title = "카메라",
                        detail = "후면 플래시 연동과 선택한 밝기 보조 측정",
                        granted = state.hasCameraPermission,
                        onRequest = onRequestCameraPermission,
                    )
                    Text(
                        "권한 요청 창이 다시 나타나지 않으면 시스템 앱 설정에서 직접 허용해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onOpenAppSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("시스템 앱 권한 설정 열기")
                    }
                }
            }

            item {
                SettingsCard(
                    title = "화면 방향과 기기",
                    subtitle = "대화면에서는 Android 정책상 방향 고정이 제한될 수 있습니다.",
                    icon = Icons.Default.ScreenRotation,
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OrientationPreference.entries.forEach { preference ->
                            FilterChip(
                                selected = settings.orientationPreference == preference,
                                onClick = {
                                    onUpdate { it.copy(orientationPreference = preference) }
                                },
                                label = { Text(preference.title) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("배터리", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${state.batteryText}${if (state.isCharging) " · 충전 중" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.BatterySaver, contentDescription = null)
                    }
                }
            }

            item {
                SettingsCard(
                    title = "개인정보와 정보",
                    subtitle = "계정·광고·분석 SDK 없이 기기 중심으로 동작합니다.",
                    icon = Icons.Default.Security,
                ) {
                    Text(
                        "녹음은 앱 내부 저장소에만 두며 공유를 선택하기 전에는 밖으로 보내지 않습니다. " +
                            "날씨를 새로고침하면 대략적 좌표가 Open-Meteo에 전달됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Text("S.tand ${BuildConfig.VERSION_NAME}")
                    Text(
                        "빌드 ${BuildConfig.BUILD_NUMBER} · versionCode ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("시스템 글꼴 + 번들 글꼴 9종", style = MaterialTheme.typography.bodySmall)
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
                }
            }

            item {
                TextButton(
                    onClick = { showResetConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Text(" 권장 설정으로 초기화")
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("설정을 초기화할까요?") },
            text = { Text("녹음 파일은 지우지 않고 화면과 감지 설정만 권장값으로 되돌립니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmation = false
                    onRestoreRecommended()
                }) { Text("초기화") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text("취소") }
            },
        )
    }

    selectedLicense?.let { font ->
        FontLicenseDialog(font = font, onDismiss = { selectedLicense = null })
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
        shape = RoundedCornerShape(20.dp),
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
        modifier = Modifier.fillMaxWidth(),
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
private fun PermissionStatusRow(
    title: String,
    detail: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (granted) "허용됨" else "허용되지 않음",
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        TextButton(onClick = onRequest, enabled = !granted) {
            Text(if (granted) "완료" else "다시 요청")
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                valueText,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
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

private fun durationText(seconds: Float): String {
    val value = seconds.roundToInt()
    return if (value < 60) "${value}초" else "${value / 60}분 ${value % 60}초"
}
