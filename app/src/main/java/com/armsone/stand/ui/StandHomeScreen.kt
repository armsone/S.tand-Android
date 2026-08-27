@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.armsone.stand.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import com.armsone.stand.R
import com.armsone.stand.BuildConfig
import com.armsone.stand.model.EnvironmentDisplayMode
import com.armsone.stand.model.HomeClockScalePolicy
import com.armsone.stand.model.HomeEditGesturePolicy
import com.armsone.stand.model.InternetRadioConfiguration
import com.armsone.stand.model.ExternalMusicService
import com.armsone.stand.model.HomeMusicChannelKind
import com.armsone.stand.model.HomeMusicChannelSelection
import com.armsone.stand.model.InternetRadioTitleTapPolicy
import com.armsone.stand.model.LampPhase
import com.armsone.stand.model.MusicChannelStripLayoutPolicy
import com.armsone.stand.model.PhoneLandscapeSideControlsPolicy
import com.armsone.stand.model.OrientationPreference
import com.armsone.stand.model.PanelTransform
import com.armsone.stand.model.StandDisplayTheme
import com.armsone.stand.model.StandControlKind
import com.armsone.stand.model.StandScreenLayout
import com.armsone.stand.model.StandModePreference
import com.armsone.stand.model.StandExperienceMode
import com.armsone.stand.model.SimplifiedBrightnessModePolicy
import com.armsone.stand.model.TvUiModePolicy
import com.armsone.stand.model.WeatherPiece
import com.armsone.stand.platform.InternetRadioState
import com.armsone.stand.platform.VolumeAdjustmentPolicy
import com.armsone.stand.ui.components.ClockDateAndSeconds
import com.armsone.stand.ui.components.ClockSeconds
import com.armsone.stand.ui.components.FlipClock
import com.armsone.stand.ui.components.standFocusable
import com.armsone.stand.ui.components.standPanelSurface
import com.armsone.stand.ui.components.rememberBurnInOffset
import com.armsone.stand.ui.theme.lampGradientColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun StandHomeScreen(
    state: StandUiState,
    showPermissionReview: Boolean = false,
    onScreenTap: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenEditor: () -> Unit,
    onBrightnessAdjustmentStarted: () -> Unit,
    onBrightnessLevelChanged: (Float) -> Unit,
    onBrightnessAdjustmentFinished: () -> Unit,
    readSystemVolume: () -> Float,
    onSystemVolumeChanged: (Float) -> Unit,
    onClockScaleChanged: (Float) -> Unit,
    onToggleTorch: () -> Unit,
    onCycleMode: () -> Unit,
    onToggleSession: () -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenAiShot: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBoyiso: () -> Unit,
    boyisoStatus: String,
    boyisoCanSendTokTok: Boolean,
    onSendBoyisoTokTok: () -> Unit,
    onToggleRadio: (String) -> Unit,
    onEditRadio: (String) -> Unit,
    onRegisterRadio: () -> Unit = {},
    onOpenExternalMusic: (ExternalMusicService) -> Unit = {},
    onEndExternalMusic: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    modifier: Modifier = Modifier,
    catalogNow: LocalDateTime? = null,
) {
    val burnInOffset = rememberBurnInOffset()
    var adjustmentFeedback by remember { mutableStateOf<HomeAdjustmentFeedback?>(null) }

    LaunchedEffect(adjustmentFeedback) {
        if (adjustmentFeedback != null) {
            delay(1_200L)
            adjustmentFeedback = null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val configuration = LocalConfiguration.current
        val isTelevision = TvUiModePolicy.isTelevision(configuration)
        val isPortrait = if (isTelevision) false else maxHeight > maxWidth
        val isExpanded = isTelevision || maxWidth >= 600.dp
        val tvSafePaddingHorizontal = if (isTelevision) TvUiModePolicy.SAFE_MARGIN_HORIZONTAL_DP.dp else 0.dp
        val tvSafePaddingVertical = if (isTelevision) TvUiModePolicy.SAFE_MARGIN_VERTICAL_DP.dp else 0.dp
        val viewportWidthDp = maxWidth.value
        val viewportHeightDp = maxHeight.value
        val visibleIntensity = if (state.isFaceDown || state.lampPhase == LampPhase.OFF) {
            0f
        } else {
            state.lampIntensity
        }
        val density = LocalDensity.current
        val backgroundRadiusPx = with(density) {
            // The iOS reference uses a 700 pt radius on an approximately 930 pt diagonal.
            // Keeping that 75% diagonal ratio makes the glow cover phones and tablets alike.
            hypot(maxWidth.toPx(), maxHeight.toPx()) * 0.75f
        }
        val contentAlpha = if (state.isFaceDown || state.isDisplayDark) {
            state.settings.silhouetteIntensity.coerceIn(0.005f, 0.2f)
        } else {
            (0.28f + visibleIntensity * 0.72f).coerceIn(0.28f, 1f)
        }
        val gradientColors = lampGradientColors(state.settings.displayTheme, visibleIntensity)
        val handleBrightnessLevelChanged: (Float) -> Unit = { value ->
            onBrightnessLevelChanged(value)
            adjustmentFeedback = HomeAdjustmentFeedback(
                title = "앱 밝기",
                value = if (isTelevision) {
                    "${TvUiModePolicy.brightnessStep(value)}/${TvUiModePolicy.BRIGHTNESS_STEP_COUNT} 단계 · ${(value * 100f).roundToInt()}%"
                } else {
                    "${(value * 100f).roundToInt()}%"
                },
            )
        }
        val handleBrightnessAdjustmentFinished: () -> Unit = {
            onBrightnessAdjustmentFinished()
            if (!isTelevision) adjustmentFeedback = null
        }
        val handleClockScaleChanged: (Float) -> Unit = { value ->
            onClockScaleChanged(value)
            adjustmentFeedback = HomeAdjustmentFeedback(
                title = "시계 크기",
                value = if (isTelevision) {
                    "${TvUiModePolicy.clockScaleStep(value)}/${TvUiModePolicy.CLOCK_SCALE_STEP_COUNT} 단계 · ${(value * 100f).roundToInt()}%"
                } else {
                    "${(value * 100f).roundToInt()}%"
                },
            )
        }

        if (showPermissionReview && !state.isSessionActive && !state.isFaceDown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = gradientColors,
                            radius = backgroundRadiusPx,
                        ),
                    ),
            )
            StandStartContent(
                state = state,
                showPermissionReview = true,
                onStart = onToggleSession,
                isTelevision = isTelevision,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            HomeGestureLayer(
            state = state,
            onScreenTap = onScreenTap,
            onOpenEditor = onOpenEditor,
            onToggleTheme = onToggleTheme,
            onBrightnessAdjustmentStarted = onBrightnessAdjustmentStarted,
            onBrightnessLevelChanged = handleBrightnessLevelChanged,
            onBrightnessAdjustmentFinished = handleBrightnessAdjustmentFinished,
            readSystemVolume = readSystemVolume,
            onSystemVolumeChanged = { value ->
                onSystemVolumeChanged(value)
                adjustmentFeedback = HomeAdjustmentFeedback(
                    title = "시스템 볼륨",
                    value = "${(value * 100f).roundToInt()}%",
                )
            },
            onSystemVolumeAdjustmentFinished = { adjustmentFeedback = null },
            onClockScaleChanged = handleClockScaleChanged,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = gradientColors,
                            radius = backgroundRadiusPx,
                        ),
                    ),
            )

        if (!state.isFaceDown) {
            if (state.isSessionActive) {
                DashboardCanvas(
                    state = state,
                    isPortrait = isPortrait,
                    contentAlpha = contentAlpha,
                    burnInOffset = burnInOffset,
                    isExpanded = isExpanded,
                    onClockTap = onScreenTap,
                    onClockDoubleTap = {},
                    catalogNow = catalogNow,
                    isTelevision = isTelevision,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.safeDrawing.asPaddingValues())
                        .padding(
                            horizontal = (if (isPortrait) 16.dp else 28.dp) + tvSafePaddingHorizontal,
                            vertical = tvSafePaddingVertical,
                        )
                        .then(
                            if (isTelevision) {
                                Modifier
                                    .offset(y = (-90).dp)
                                    .graphicsLayer {
                                        scaleX = 0.55f
                                        scaleY = 0.55f
                                    }
                            } else {
                                Modifier
                            },
                        ),
                )
            } else {
                StandStartContent(
                    state = state,
                    showPermissionReview = showPermissionReview,
                    onStart = onToggleSession,
                    isTelevision = isTelevision,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            val usesPhoneLandscapeSideControls = !isTelevision && PhoneLandscapeSideControlsPolicy.isEnabled(
                isPortrait = isPortrait,
                viewportWidth = viewportWidthDp,
                viewportHeight = viewportHeightDp,
            )

            val homeTopPadding = if (isTelevision) {
                TvUiModePolicy.TV_HOME_TOP_PADDING_DP.dp
            } else {
                14.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(
                        start = (if (isPortrait) 16.dp else 28.dp) + tvSafePaddingHorizontal,
                        top = homeTopPadding,
                        end = (if (isPortrait) 16.dp else 28.dp) + tvSafePaddingHorizontal,
                        bottom = (if (isPortrait) 28.dp else 18.dp) + tvSafePaddingVertical,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Header(
                    state = state,
                    contentAlpha = contentAlpha,
                    isTelevision = isTelevision,
                    onCheckUpdate = onCheckUpdate,
                )

                if (usesPhoneLandscapeSideControls) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MusicChannelStrip(
                            state = state,
                            contentAlpha = contentAlpha,
                            isPhoneLandscape = true,
                            isTelevision = false,
                            onToggleRadio = onToggleRadio,
                            onEditRadio = onEditRadio,
                            onRegisterRadio = onRegisterRadio,
                            onOpenExternalMusic = onOpenExternalMusic,
                            onEndExternalMusic = onEndExternalMusic,
                            modifier = Modifier.weight(1f),
                        )
                        PhoneLandscapeSideControls(
                            state = state,
                            onOpenRecordings = onOpenRecordings,
                            onOpenSettings = onOpenSettings,
                            onOpenBoyiso = onOpenBoyiso,
                            boyisoStatus = boyisoStatus,
                            boyisoCanSendTokTok = boyisoCanSendTokTok,
                            onSendBoyisoTokTok = onSendBoyisoTokTok,
                        )
                    }
                } else {
                    MusicChannelStrip(
                        state = state,
                        contentAlpha = contentAlpha,
                        isPhoneLandscape = false,
                        isTelevision = isTelevision,
                        onToggleRadio = onToggleRadio,
                        onEditRadio = onEditRadio,
                        onRegisterRadio = onRegisterRadio,
                        onOpenExternalMusic = onOpenExternalMusic,
                        onEndExternalMusic = onEndExternalMusic,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                if (!usesPhoneLandscapeSideControls) {
                    HomeControls(
                        state = state,
                        isPortrait = isPortrait,
                        isExpanded = isExpanded,
                        isTelevision = isTelevision,
                        onToggleTorch = onToggleTorch,
                        onCycleMode = onCycleMode,
                        onToggleSession = onToggleSession,
                        onToggleOrientation = onToggleOrientation,
                        onOpenRecordings = onOpenRecordings,
                        onOpenAiShot = onOpenAiShot,
                        onOpenSettings = onOpenSettings,
                        onOpenBoyiso = onOpenBoyiso,
                        onToggleTheme = onToggleTheme,
                        onBrightnessAdjustmentStarted = onBrightnessAdjustmentStarted,
                        onBrightnessLevelChanged = handleBrightnessLevelChanged,
                        onBrightnessAdjustmentFinished = handleBrightnessAdjustmentFinished,
                        onClockScaleChanged = handleClockScaleChanged,
                        boyisoStatus = boyisoStatus,
                        boyisoCanSendTokTok = boyisoCanSendTokTok,
                        onSendBoyisoTokTok = onSendBoyisoTokTok,
                    )
                }
            }

            Text(
                text = "${BuildConfig.VERSION_NAME} · 밝기 " +
                    "${(state.displayBrightness.coerceIn(0f, 1f) * 100f).roundToInt()}%",
                color = Color.White.copy(
                    alpha = if (state.isDisplayDark) 0f else 0.28f,
                ),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(bottom = 6.dp)
                    .semantics {
                        contentDescription =
                            "앱 버전 ${BuildConfig.VERSION_NAME}, 현재 밝기 " +
                            "${(state.displayBrightness.coerceIn(0f, 1f) * 100f).roundToInt()}퍼센트"
                    },
            )
        }

        if (state.batteryProtectionActive) {
            StatusBanner(
                text = "배터리가 20% 이하라 보호를 위해 감지와 불빛을 중지했습니다.",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 62.dp),
            )
        } else if (!isTelevision && state.isWritingClip) {
            StatusBanner(
                text = "수면 소리 후보 저장 중 · 기기 안에만 보관해요",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 62.dp),
                )
        }

            adjustmentFeedback?.let { feedback ->
                HomeAdjustmentFeedbackPanel(
                    feedback = feedback,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        }
    }
}

@Composable
private fun StandStartContent(
    state: StandUiState,
    showPermissionReview: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    isTelevision: Boolean = false,
) {
    if (!showPermissionReview) {
        RegularStartContent(
            onStart = onStart,
            modifier = modifier,
            isTelevision = isTelevision,
        )
        return
    }

    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isTelevision) {
        if (isTelevision) initialFocusRequester.requestFocus()
    }
    val allPermissionsGranted = if (isTelevision) {
        state.hasApproximateLocationPermission
    } else {
        state.hasCameraPermission &&
            state.hasMicrophonePermission &&
            state.hasApproximateLocationPermission
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(42.dp),
        )
        Text(
            text = "시작하기 전에",
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "S.tand가 필요한 이유를 먼저 알려드릴게요.",
            color = Color.White.copy(alpha = 0.60f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        Surface(
            color = Color.White.copy(alpha = 0.07f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!isTelevision) {
                    PermissionReasonRow(
                        icon = Icons.Default.CameraAlt,
                        title = "카메라와 플래시",
                        reason = "방 밝기를 확인하고, 어두울 때 화들짝 모드에서만 잠깐 밝힙니다. 사진·영상은 저장하거나 전송하지 않습니다.",
                    )
                }
                if (!isTelevision) {
                    PermissionReasonRow(
                        icon = Icons.Default.Mic,
                        title = "마이크",
                        reason = "잠꼬대·코골이를 감지하고 필요한 소리만 이 기기에 저장합니다.",
                    )
                }
                PermissionReasonRow(
                    icon = Icons.Default.LocationOn,
                    title = "위치 정보",
                    reason = "현재 날씨에만 사용하며 가능한 최소 정확도와 필요한 범위만 요청합니다.",
                )
            }
        }
        Text(
            text = "허용하지 않아도 앱은 시작됩니다. 허용한 기능만 작동합니다.",
            color = Color.White.copy(alpha = 0.58f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        Surface(
            onClick = onStart,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .height(52.dp)
                .widthIn(min = 220.dp)
                .focusRequester(initialFocusRequester)
                .standFocusable(shape = RoundedCornerShape(16.dp))
                .semantics {
                    contentDescription = if (allPermissionsGranted) {
                        "S.tand 시작"
                    } else {
                        "권한 확인하고 시작"
                    }
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.LightMode, contentDescription = null)
                Text(
                    text = if (allPermissionsGranted) {
                        "S.tand 시작"
                    } else {
                        "권한 확인하고 시작"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RegularStartContent(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    isTelevision: Boolean = false,
) {
    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isTelevision) {
        if (isTelevision) initialFocusRequester.requestFocus()
    }
    if (isTelevision) {
        Row(
            modifier = modifier
                .offset(y = (-52).dp)
                .widthIn(max = 760.dp)
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.stand_brand_icon),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "S.tand가 곁에 있을게요",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "시간·날씨와 잠자리를 돌봅니다.",
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Surface(
                onClick = onStart,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .height(56.dp)
                    .focusRequester(initialFocusRequester)
                    .standFocusable(shape = RoundedCornerShape(16.dp))
                    .semantics { contentDescription = "S.tand 시작" },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.LightMode, contentDescription = null)
                    Text("S.tand 시작", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.stand_brand_icon),
            contentDescription = null,
            modifier = Modifier.size(76.dp),
        )
        Text(
            text = "S.tand가 곁에 있을게요",
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "시작하면 오브제와 매이트 모드를 오가며 시간·날씨와 잠자리를 돌봅니다.",
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Surface(
            onClick = onStart,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .height(52.dp)
                .focusRequester(initialFocusRequester)
                .standFocusable(shape = RoundedCornerShape(16.dp))
                .semantics { contentDescription = "S.tand 시작" },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.LightMode, contentDescription = null)
                Text("S.tand 시작", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PermissionReasonRow(
    icon: ImageVector,
    title: String,
    reason: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = reason,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private data class HomeAdjustmentFeedback(
    val title: String,
    val value: String,
)

@Composable
private fun HomeAdjustmentFeedbackPanel(
    feedback: HomeAdjustmentFeedback,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF181A1F),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .height(46.dp)
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = when (feedback.title) {
                    "시스템 볼륨" -> Icons.AutoMirrored.Filled.VolumeUp
                    "시계 크기" -> Icons.Default.ZoomIn
                    else -> Icons.Default.LightMode
                },
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = feedback.title,
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = feedback.value,
                color = Color.White.copy(alpha = 0.92f),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HomeGestureLayer(
    state: StandUiState,
    onScreenTap: () -> Unit,
    onOpenEditor: () -> Unit,
    onToggleTheme: () -> Unit,
    onBrightnessAdjustmentStarted: () -> Unit,
    onBrightnessLevelChanged: (Float) -> Unit,
    onBrightnessAdjustmentFinished: () -> Unit,
    readSystemVolume: () -> Float,
    onSystemVolumeChanged: (Float) -> Unit,
    onSystemVolumeAdjustmentFinished: () -> Unit,
    onClockScaleChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    val latestState = rememberUpdatedState(state)
    val latestOnScreenTap = rememberUpdatedState(onScreenTap)
    val latestOnOpenEditor = rememberUpdatedState(onOpenEditor)
    val latestOnToggleTheme = rememberUpdatedState(onToggleTheme)
    val latestOnBrightnessAdjustmentStarted = rememberUpdatedState(onBrightnessAdjustmentStarted)
    val latestOnBrightnessLevelChanged = rememberUpdatedState(onBrightnessLevelChanged)
    val latestOnBrightnessAdjustmentFinished = rememberUpdatedState(
        onBrightnessAdjustmentFinished,
    )
    val latestReadSystemVolume = rememberUpdatedState(readSystemVolume)
    val latestOnSystemVolumeChanged = rememberUpdatedState(
        onSystemVolumeChanged,
    )
    val latestOnSystemVolumeAdjustmentFinished = rememberUpdatedState(
        onSystemVolumeAdjustmentFinished,
    )
    val latestOnClockScaleChanged = rememberUpdatedState(onClockScaleChanged)
    var layerHeightPx by remember { mutableStateOf(0f) }
    val bottomGestureExclusionPx = with(LocalDensity.current) { 104.dp.toPx() }

    Box(
        modifier = modifier
            .onSizeChanged { layerHeightPx = it.height.toFloat() }
            .semantics {
                val currentLevel = state.displayBrightness.coerceIn(0f, 1f)
                contentDescription = "홈 화면 제어"
                progressBarRangeInfo = ProgressBarRangeInfo(currentLevel, 0f..1f, 9)
                setProgress { requestedLevel ->
                    if (!state.isSessionActive) return@setProgress false
                    onBrightnessAdjustmentStarted()
                    onBrightnessLevelChanged(requestedLevel.coerceIn(0f, 1f))
                    onBrightnessAdjustmentFinished()
                    true
                }
                if (state.isSessionActive) customActions = listOf(
                    CustomAccessibilityAction("앱 밝기 10퍼센트 올리기") {
                        onBrightnessAdjustmentStarted()
                        onBrightnessLevelChanged((currentLevel + 0.1f).coerceAtMost(1f))
                        onBrightnessAdjustmentFinished()
                        true
                    },
                    CustomAccessibilityAction("앱 밝기 10퍼센트 내리기") {
                        onBrightnessAdjustmentStarted()
                        onBrightnessLevelChanged((currentLevel - 0.1f).coerceAtLeast(0f))
                        onBrightnessAdjustmentFinished()
                        true
                    },
                    CustomAccessibilityAction("오브제와 매이트 전환") {
                        onScreenTap()
                        true
                    },
                    CustomAccessibilityAction("테마 전환") {
                        onToggleTheme()
                        true
                    },
                    CustomAccessibilityAction("화면 편집 열기") {
                        onOpenEditor()
                        true
                    },
                    CustomAccessibilityAction("시계 크게") {
                        onClockScaleChanged(
                            (state.settings.clockScale + 0.1f)
                                .coerceAtMost(HomeClockScalePolicy.MAXIMUM_TOUCH_SCALE),
                        )
                        true
                    },
                    CustomAccessibilityAction("시계 작게") {
                        onClockScaleChanged((state.settings.clockScale - 0.1f).coerceAtLeast(0.7f))
                        true
                    },
                )
            }
            .pointerInput(Unit) {
                var previousTapUpTime = 0L
                var previousTapPosition = Offset.Unspecified

                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    var moved = false
                    var upPosition = down.position
                    var upTime = down.uptimeMillis
                    var pressed = true

                    while (pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        moved = moved ||
                            (change.position - down.position).getDistance() >=
                            viewConfiguration.touchSlop
                        upPosition = change.position
                        upTime = change.uptimeMillis
                        pressed = change.pressed
                    }

                    if (!moved &&
                        !pressed &&
                        down.position.y < layerHeightPx - bottomGestureExclusionPx
                    ) {
                        val isDoubleTap = previousTapUpTime > 0L &&
                            upTime - previousTapUpTime <= viewConfiguration.doubleTapTimeoutMillis &&
                            (upPosition - previousTapPosition).getDistance() <=
                            viewConfiguration.touchSlop * 4f

                        if (isDoubleTap) {
                            previousTapUpTime = 0L
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            latestOnToggleTheme.value()
                        } else {
                            previousTapUpTime = upTime
                            previousTapPosition = upPosition
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    var previousTapUpTime = 0L
                    var previousTapPosition = Offset.Unspecified
                    var pendingSingleTap: Job? = null

                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = true,
                            pass = PointerEventPass.Final,
                        )
                        if (down.position.y >= layerHeightPx - bottomGestureExclusionPx) {
                            waitForUpOrCancellation(pass = PointerEventPass.Final)
                            return@awaitEachGesture
                        }

                        var longPressTriggered = false
                        val longPressJob = launch {
                            delay(HomeEditGesturePolicy.HOLD_DURATION_MILLIS)
                            longPressTriggered = true
                            pendingSingleTap?.cancel()
                            previousTapUpTime = 0L
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            latestOnOpenEditor.value()
                        }

                        var moved = false
                        var upPosition = down.position
                        var upTime = down.uptimeMillis
                        var upConsumed = false
                        var pressed = true

                        while (pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                longPressJob.cancel()
                                break
                            }

                            val distance = (change.position - down.position).getDistance()
                            val pointerCount = event.changes.count { it.pressed }
                            val isCancelledOrConsumed = change.isConsumed

                            if (HomeEditGesturePolicy.shouldCancelHold(
                                    movementDistancePx = distance,
                                    touchSlopPx = viewConfiguration.touchSlop,
                                    pointerCount = pointerCount,
                                ) || isCancelledOrConsumed
                            ) {
                                if (distance >= viewConfiguration.touchSlop || pointerCount > 1 || isCancelledOrConsumed) {
                                    moved = true
                                }
                                longPressJob.cancel()
                            }

                            upPosition = change.position
                            upTime = change.uptimeMillis
                            upConsumed = change.isConsumed
                            pressed = change.pressed
                        }

                        longPressJob.cancel()

                        if (!moved && !pressed && !longPressTriggered && !upConsumed) {
                            val isDoubleTap = previousTapUpTime > 0L &&
                                upTime - previousTapUpTime <=
                                viewConfiguration.doubleTapTimeoutMillis &&
                                (upPosition - previousTapPosition).getDistance() <=
                                viewConfiguration.touchSlop * 4f

                            if (isDoubleTap) {
                                pendingSingleTap?.cancel()
                                pendingSingleTap = null
                                previousTapUpTime = 0L
                            } else {
                                previousTapUpTime = upTime
                                previousTapPosition = upPosition
                                pendingSingleTap?.cancel()
                                pendingSingleTap = launch {
                                    delay(viewConfiguration.doubleTapTimeoutMillis)
                                    previousTapUpTime = 0L
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    latestOnScreenTap.value()
                                }
                            }
                        } else if (moved || longPressTriggered) {
                            previousTapUpTime = 0L
                            previousTapPosition = Offset.Unspecified
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = true,
                        pass = PointerEventPass.Final,
                    )
                    var axis: HomeAdjustmentAxis? = null
                    var startingLevel = 0f
                    var startingSpan = 0f
                    var finished = false

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (axis == null && change.isConsumed) break
                        val translation = change.position - down.position
                        val pressed = event.changes.filter { it.pressed }

                        if (axis == null && pressed.size >= 2) {
                            startingLevel = latestState.value.settings.clockScale
                            startingSpan = (pressed[0].position - pressed[1].position).getDistance()
                                .coerceAtLeast(1f)
                            axis = HomeAdjustmentAxis.CLOCK_SCALE
                        }

                        if (axis == null && translation.getDistance() >= viewConfiguration.touchSlop) {
                            axis = if (abs(translation.y) > abs(translation.x)) {
                                val current = latestState.value
                                startingLevel = current.displayBrightness
                                    .takeIf { current.isSessionActive }
                                    ?: current.settings.lampIntensity
                                latestOnBrightnessAdjustmentStarted.value()
                                HomeAdjustmentAxis.VERTICAL_BRIGHTNESS
                            } else {
                                startingLevel = latestReadSystemVolume.value()
                                HomeAdjustmentAxis.HORIZONTAL_SYSTEM_VOLUME
                            }
                        }

                        when (axis) {
                            HomeAdjustmentAxis.VERTICAL_BRIGHTNESS -> {
                                change.consume()
                                latestOnBrightnessLevelChanged.value(
                                    SimplifiedBrightnessModePolicy.level(
                                        startingAt = startingLevel,
                                        verticalTranslationPx = translation.y,
                                        viewportHeightPx = layerHeightPx,
                                    ),
                                )
                            }
                            HomeAdjustmentAxis.HORIZONTAL_SYSTEM_VOLUME -> {
                                change.consume()
                                latestOnSystemVolumeChanged.value(
                                    VolumeAdjustmentPolicy.level(
                                        startingAt = startingLevel,
                                        horizontalTranslationPx = translation.x,
                                        viewportWidthPx = size.width.toFloat(),
                                    ),
                                )
                            }
                            HomeAdjustmentAxis.CLOCK_SCALE -> {
                                event.changes.forEach { it.consume() }
                                if (pressed.size >= 2) {
                                    val currentSpan =
                                        (pressed[0].position - pressed[1].position).getDistance()
                                    latestOnClockScaleChanged.value(
                                        HomeClockScalePolicy.scaled(
                                            startingAt = startingLevel,
                                            magnification = currentSpan / startingSpan,
                                        ),
                                    )
                                }
                            }
                            null -> Unit
                        }

                        if (!change.pressed) {
                            if (axis == HomeAdjustmentAxis.VERTICAL_BRIGHTNESS) {
                                latestOnBrightnessAdjustmentFinished.value()
                            } else if (axis == HomeAdjustmentAxis.HORIZONTAL_SYSTEM_VOLUME) {
                                latestOnSystemVolumeAdjustmentFinished.value()
                            }
                            finished = true
                        }
                    } while (!finished)

                    if (!finished && axis == HomeAdjustmentAxis.VERTICAL_BRIGHTNESS) {
                        latestOnBrightnessAdjustmentFinished.value()
                    } else if (!finished && axis == HomeAdjustmentAxis.HORIZONTAL_SYSTEM_VOLUME) {
                        latestOnSystemVolumeAdjustmentFinished.value()
                    }
                }
            },
    ) { content() }
}

private enum class HomeAdjustmentAxis {
    VERTICAL_BRIGHTNESS,
    HORIZONTAL_SYSTEM_VOLUME,
    CLOCK_SCALE,
}

@Composable
private fun Header(
    state: StandUiState,
    contentAlpha: Float,
    isTelevision: Boolean = false,
    onCheckUpdate: () -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!isTelevision) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = if (!state.isSessionActive) {
                        Icons.Default.StopCircle
                    } else if (state.experienceMode == StandExperienceMode.STARTLED) {
                        Icons.Default.Thunderstorm
                    } else if (state.settings.modePreference != StandModePreference.AUTOMATIC) {
                        Icons.Default.Lock
                    } else {
                        Icons.Default.Bedtime
                    },
                    contentDescription = null,
                    tint = Color.White.copy(alpha = contentAlpha * 0.62f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = when {
                        !state.isSessionActive -> "자동 기능 꺼짐"
                        state.experienceMode == StandExperienceMode.STARTLED ->
                            StandExperienceMode.STARTLED.title
                        state.settings.modePreference == com.armsone.stand.model.StandModePreference.OBJECT ->
                            "오브제 모드 잠금"
                        state.settings.modePreference == StandModePreference.MATE ->
                            "매이트 모드 잠금"
                        else -> state.experienceMode.title
                    },
                    color = Color.White.copy(alpha = contentAlpha * 0.62f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(8.dp))
                .standFocusable(
                    shape = RoundedCornerShape(8.dp),
                    scaleOnFocus = false,
                )
                .clickable(
                    onClick = onCheckUpdate,
                    onClickLabel = "최신 버전 확인",
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "최신 버전 확인"
                    role = Role.Button
                    customActions = listOf(
                        CustomAccessibilityAction("최신 버전 확인") {
                            onCheckUpdate()
                            true
                        },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.stand_brand_icon),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .graphicsLayer { alpha = contentAlpha },
            )
            Text(
                text = "S.tand",
                color = Color.White.copy(alpha = contentAlpha * 0.82f),
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.batteryLevel != null || state.isCharging) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = batteryIcon(state.batteryLevel, state.isCharging),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = contentAlpha * 0.62f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = state.batteryText,
                    color = Color.White.copy(alpha = contentAlpha * 0.62f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun DashboardCanvas(
    state: StandUiState,
    isPortrait: Boolean,
    contentAlpha: Float,
    burnInOffset: com.armsone.stand.ui.components.BurnInOffset,
    isExpanded: Boolean,
    onClockTap: () -> Unit,
    onClockDoubleTap: () -> Unit,
    catalogNow: LocalDateTime?,
    modifier: Modifier = Modifier,
    isTelevision: Boolean = false,
) {
    val layout = if (isPortrait) {
        state.settings.portraitLayout
    } else {
        state.settings.landscapeLayout
    }

    BoxWithConstraints(modifier = modifier) {
        val canvasWidth = maxWidth
        val canvasHeight = maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = burnInOffset.xDp.dp, y = burnInOffset.yDp.dp)
                .graphicsLayer {
                    scaleX = state.settings.clockScale
                    scaleY = state.settings.clockScale
                },
        ) {
            FlipClock(
                hourMode = state.settings.clockHourMode,
                clockFont = state.settings.clockFont,
                isPortrait = isPortrait,
                scale = 1f,
                contentAlpha = contentAlpha,
                fixedNow = catalogNow,
                modifier = Modifier
                    .align(Alignment.Center)
                    .combinedClickable(
                        onClick = onClockTap,
                        onDoubleClick = onClockDoubleTap,
                    )
                    .panelTransform(layout.clock, canvasWidth.value, canvasHeight.value),
            )

            if (
                !isTelevision &&
                state.settings.modePreference == StandModePreference.MATE &&
                state.experienceMode != StandExperienceMode.STARTLED
            ) {
                val lockSize = if (isPortrait) 92.dp else 116.dp
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.68f),
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .panelTransform(layout.clock, canvasWidth.value, canvasHeight.value)
                        .size(lockSize)
                        .semantics { contentDescription = "매이트 모드 잠금" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(lockSize * 0.66f),
                        )
                    }
                }
            }

            ClockSeconds(
                clockFont = state.settings.clockFont,
                isPortrait = isPortrait,
                contentAlpha = contentAlpha,
                showsBackground = !clockSecondsOverlapsClock(
                    layout = layout,
                    canvasWidthDp = canvasWidth.value,
                    canvasHeightDp = canvasHeight.value,
                    isPortrait = isPortrait,
                ),
                fixedNow = catalogNow,
                modifier = Modifier
                    .align(Alignment.Center)
                    .panelTransform(layout.seconds, canvasWidth.value, canvasHeight.value),
            )

            ClockDateAndSeconds(
                hourMode = state.settings.clockHourMode,
                contentAlpha = contentAlpha,
                fixedNow = catalogNow,
                modifier = Modifier
                    .align(Alignment.Center)
                    .panelTransform(layout.date, canvasWidth.value, canvasHeight.value),
            )

            if (state.isDisplayDark || state.experienceMode == StandExperienceMode.MATE) {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .panelTransform(layout.battery, canvasWidth.value, canvasHeight.value)
                        .standPanelSurface(
                            isDimmed = true,
                            cornerRadius = 18.dp,
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = batteryIcon(state.batteryLevel, state.isCharging),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = contentAlpha),
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "배터리 ${state.batteryText}",
                            color = Color.White.copy(alpha = contentAlpha),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            WeatherPanelCollection(
                weather = state.weather,
                message = state.weatherMessage,
                layout = layout,
                isPortrait = isPortrait,
                isExpanded = isExpanded,
                contentAlpha = contentAlpha,
                canvasWidthDp = canvasWidth.value,
                canvasHeightDp = canvasHeight.value,
            )
        }
    }
}

/** The iOS-style fixed horizontal music strip below the top brand/header. */
@Composable
internal fun MusicChannelStrip(
    state: StandUiState,
    contentAlpha: Float,
    isPhoneLandscape: Boolean,
    isTelevision: Boolean = false,
    onToggleRadio: (String) -> Unit,
    onEditRadio: (String) -> Unit,
    onRegisterRadio: () -> Unit,
    onOpenExternalMusic: (ExternalMusicService) -> Unit,
    onEndExternalMusic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channels = state.settings.homeMusicChannels
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier.height(
            if (isTelevision) 44.dp else MusicChannelStripLayoutPolicy.CARD_HEIGHT.dp,
        ),
    ) {
        val viewportWidth = maxWidth
        val viewportWidthDp = maxWidth.value
        val cardWidthDp = if (isTelevision) {
            112f
        } else {
            MusicChannelStripLayoutPolicy.cardWidth(viewportWidthDp, isPhoneLandscape)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("music_channel_strip")
                .clipToBounds()
                .musicChannelStripEdgeMask(
                    showsLeadingFade = scrollState.value > 0,
                    showsTrailingFade = scrollState.value < scrollState.maxValue,
                )
                .horizontalScroll(scrollState),
        ) {
            Row(
                modifier = if (isTelevision) {
                    Modifier.width(viewportWidth)
                } else {
                    Modifier.padding(horizontal = MusicChannelStripLayoutPolicy.SIDE_INSET.dp)
                },
                horizontalArrangement = Arrangement.spacedBy(
                    MusicChannelStripLayoutPolicy.SPACING.dp,
                    if (isTelevision) Alignment.CenterHorizontally else Alignment.Start,
                ),
            ) {
                channels.forEach { selection ->
                    MusicPanel(
                        state = state,
                        selection = selection,
                        contentAlpha = contentAlpha,
                        isTelevision = isTelevision,
                        width = cardWidthDp.dp,
                        onToggleRadio = onToggleRadio,
                        onEditRadio = onEditRadio,
                        onRegisterRadio = onRegisterRadio,
                        onOpenExternalMusic = onOpenExternalMusic,
                        onEndExternalMusic = onEndExternalMusic,
                    )
                }
            }
        }
    }
}

private fun Modifier.musicChannelStripEdgeMask(
    showsLeadingFade: Boolean,
    showsTrailingFade: Boolean,
): Modifier {
    if (!showsLeadingFade && !showsTrailingFade) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (showsLeadingFade) {
                val fadeWidth = 24.dp.toPx().coerceAtMost(size.width)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startX = 0f,
                        endX = fadeWidth,
                    ),
                    size = Size(fadeWidth, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (showsTrailingFade) {
                val fadeWidth = 28.dp.toPx().coerceAtMost(size.width)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startX = size.width - fadeWidth,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - fadeWidth, 0f),
                    size = Size(fadeWidth, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

/** Phone landscape controls fixed to the right of the independently sliding music strip. */
@Composable
internal fun PhoneLandscapeSideControls(
    state: StandUiState,
    onOpenRecordings: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBoyiso: () -> Unit,
    boyisoStatus: String,
    boyisoCanSendTokTok: Boolean,
    onSendBoyisoTokTok: () -> Unit,
) {
    val controlOrder = state.settings.landscapeLayout.controlOrder.filter {
        it in StandControlKind.DefaultOrder
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        controlOrder.forEach { kind ->
            val defaultAction = when (kind) {
                StandControlKind.RECORDINGS -> onOpenRecordings
                StandControlKind.SETTINGS -> onOpenSettings
                else -> onOpenBoyiso
            }
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(
                        width = PhoneLandscapeSideControlsPolicy.CONTROL_WIDTH.dp,
                        height = MusicChannelStripLayoutPolicy.CARD_HEIGHT.dp,
                    )
                    .standFocusable(shape = RoundedCornerShape(14.dp))
                    .standPanelSurface(isDimmed = false, cornerRadius = 14.dp, splitGap = 2.dp)
                    .combinedClickable(
                        onClick = if (kind == StandControlKind.BOYISO && boyisoCanSendTokTok) {
                            onSendBoyisoTokTok
                        } else {
                            defaultAction
                        },
                        onLongClick = onOpenBoyiso.takeIf { kind == StandControlKind.BOYISO },
                    ),
            ) {
                StandControlTileContent(
                    presentation = kind.presentation(state, boyisoStatus = boyisoStatus),
                    showReorderHandle = false,
                )
            }
        }
    }
}

@Composable
internal fun MusicPanel(
    state: StandUiState,
    selection: HomeMusicChannelSelection,
    contentAlpha: Float,
    isTelevision: Boolean = false,
    onToggleRadio: (String) -> Unit,
    onEditRadio: (String) -> Unit,
    onOpenExternalMusic: (ExternalMusicService) -> Unit,
    onEndExternalMusic: () -> Unit,
    onRegisterRadio: () -> Unit = {},
    width: androidx.compose.ui.unit.Dp = 144.dp,
    drawsSurface: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (selection.kind == HomeMusicChannelKind.INTERNET_RADIO) {
        val radio = state.settings.internetRadioChannels.firstOrNull { it.id == selection.radioID }
        val orderedChannelIDs = state.settings.internetRadioChannels.map { it.id }
        val activeChannelID = activeRadioChannelID(state.internetRadioState)
        RadioPanel(
            state = state,
            configuration = radio,
            contentAlpha = contentAlpha,
            isTelevision = isTelevision,
            width = width,
            onPrimaryClick = { radio?.id?.let(onToggleRadio) ?: onRegisterRadio() },
            onSecondaryClick = {
                if (radio == null) {
                    onRegisterRadio()
                } else {
                    InternetRadioTitleTapPolicy.targetChannelID(
                        tappedChannelID = radio.id,
                        activeChannelID = activeChannelID,
                        isPlaying = state.internetRadioState is InternetRadioState.Playing,
                        orderedChannelIDs = orderedChannelIDs,
                    )?.let(onToggleRadio)
                }
            },
            onLongClick = { radio?.id?.let(onEditRadio) },
            drawsSurface = drawsSurface,
            modifier = modifier,
        )
        return
    }

    val service = if (selection.kind == HomeMusicChannelKind.SPOTIFY) {
        ExternalMusicService.SPOTIFY
    } else {
        ExternalMusicService.YOUTUBE_MUSIC
    }
    val active = state.externalMusicService == service
    val detail = if (active) "음악 듣기 모드" else "대기 중"
    val visibleAlpha = contentAlpha * if (isTelevision) 0.48f else 1f
    Surface(
        modifier = modifier
            .width(width)
            .height(if (isTelevision) 44.dp else 60.dp)
            .standFocusable(shape = RoundedCornerShape(13.dp))
            .combinedClickable(
                onClick = { onOpenExternalMusic(service) },
                onLongClick = if (active) onEndExternalMusic else null,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${service.displayName}, $detail"
                stateDescription = detail
                role = Role.Button
            }
            .then(
                if (drawsSurface) Modifier.standPanelSurface(
                    isDimmed = isTelevision || contentAlpha <= 0.2f,
                    cornerRadius = 13.dp,
                    splitGap = 2.dp,
                ) else Modifier,
            ),
        color = Color.Transparent,
        shape = RoundedCornerShape(13.dp),
        shadowElevation = 0.dp,
    ) {
        MusicPanelTileContent(
            icon = if (service == ExternalMusicService.SPOTIFY) {
                Icons.Default.GraphicEq
            } else {
                Icons.Default.PlayArrow
            },
            title = service.displayName,
            detail = detail,
            visibleAlpha = visibleAlpha,
            detailAlpha = 0.52f,
            isTelevision = isTelevision,
        )
    }
}

@Composable
internal fun RadioPanel(
    state: StandUiState,
    configuration: InternetRadioConfiguration?,
    contentAlpha: Float,
    isTelevision: Boolean = false,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    width: androidx.compose.ui.unit.Dp = 144.dp,
    drawsSurface: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isActive = configuration?.id == activeRadioChannelID(state.internetRadioState)
    val (title, detail) = if (configuration == null) {
        "인터넷 라디오" to "HTTPS 주소 등록"
    } else when (val radioState = state.internetRadioState) {
        InternetRadioState.Idle -> configuration.displayName to "대기"
        is InternetRadioState.Loading -> if (isActive) {
            configuration.displayName to "연결 중"
        } else {
            configuration.displayName to "대기"
        }
        is InternetRadioState.Playing -> if (isActive) {
            configuration.displayName to "재생"
        } else {
            configuration.displayName to "대기"
        }
        is InternetRadioState.Reconnecting -> if (isActive) {
            configuration.displayName to "다시 연결 중"
        } else {
            configuration.displayName to "대기"
        }
        is InternetRadioState.Failed -> if (isActive) {
            configuration.displayName to "연결 실패"
        } else {
            configuration.displayName to "대기"
        }
    }
    val accessibilityHint = when {
        configuration == null -> "인터넷 라디오 주소를 등록합니다."
        isActive && state.internetRadioState is InternetRadioState.Loading ->
            "인터넷 라디오 연결을 취소합니다."
        isActive && state.internetRadioState is InternetRadioState.Reconnecting ->
            "인터넷 라디오 연결을 취소합니다."
        isActive && state.internetRadioState is InternetRadioState.Playing ->
            "인터넷 라디오를 끄고 소리 감지와 녹음을 다시 시작합니다."
        else -> "등록한 인터넷 라디오를 재생합니다."
    }
    val visibleAlpha = contentAlpha * if (isTelevision) 0.48f else 1f
    Surface(
        modifier = modifier
            .width(width)
            .height(if (isTelevision) 44.dp else 60.dp)
            .then(
                if (configuration == null) {
                    Modifier
                        .standFocusable(shape = RoundedCornerShape(13.dp))
                        .combinedClickable(onClick = onPrimaryClick)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$title, $detail. $accessibilityHint"
                            stateDescription = detail
                            role = Role.Button
                        }
                } else {
                    Modifier
                },
            )
            .then(
                if (drawsSurface) {
                    Modifier.standPanelSurface(
                        isDimmed = isTelevision || contentAlpha <= 0.2f,
                        cornerRadius = 13.dp,
                        splitGap = 2.dp,
                    )
                } else {
                    Modifier
                },
            ),
        color = Color.Transparent,
        shape = RoundedCornerShape(13.dp),
        shadowElevation = 0.dp,
    ) {
        Box {
            MusicPanelTileContent(
                icon = if (isActive && state.internetRadioState is InternetRadioState.Playing) {
                    Icons.Default.StopCircle
                } else {
                    Icons.Default.GraphicEq
                },
                title = title,
                detail = detail,
                visibleAlpha = visibleAlpha,
                detailAlpha = 0.58f,
                isTelevision = isTelevision,
            )
            if (configuration != null) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .combinedClickable(
                                onClick = onPrimaryClick,
                                onLongClick = onLongClick,
                            )
                            .semantics {
                                contentDescription = "$title, 재생 또는 일시 정지"
                                stateDescription = detail
                                role = Role.Button
                            },
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .combinedClickable(
                                onClick = onSecondaryClick,
                                onLongClick = onLongClick,
                            )
                            .semantics {
                                contentDescription = "$title, 다음 라디오"
                                stateDescription = detail
                                role = Role.Button
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicPanelTileContent(
    icon: ImageVector,
    title: String,
    detail: String,
    visibleAlpha: Float,
    detailAlpha: Float,
    isTelevision: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = if (isTelevision) 0.dp else (-2).dp)
            .padding(
                horizontal = if (isTelevision) 8.dp else 11.dp,
                vertical = if (isTelevision) 4.dp else 5.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isTelevision) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = visibleAlpha),
                modifier = Modifier.size(if (isTelevision) 14.dp else 24.dp),
            )
            Text(
                text = title,
                color = Color.White.copy(alpha = visibleAlpha),
                fontSize = if (isTelevision) 9.sp else 11.sp,
                lineHeight = if (isTelevision) 10.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(if (isTelevision) 3.dp else 11.dp))
        Text(
            text = detail,
            modifier = Modifier.offset(y = if (isTelevision) 0.dp else (-1).dp),
            color = Color.White.copy(alpha = visibleAlpha * detailAlpha),
            fontSize = if (isTelevision) 7.sp else 8.sp,
            lineHeight = if (isTelevision) 8.sp else 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun activeRadioChannelID(state: InternetRadioState): String? = when (state) {
    is InternetRadioState.Loading -> state.channelID
    is InternetRadioState.Playing -> state.channelID
    is InternetRadioState.Reconnecting -> state.channelID
    is InternetRadioState.Failed -> state.channelID
    InternetRadioState.Idle -> null
}

internal fun clockSecondsOverlapsClock(
    layout: StandScreenLayout,
    canvasWidthDp: Float,
    canvasHeightDp: Float,
    isPortrait: Boolean,
): Boolean {
    if (!canvasWidthDp.isFinite() || !canvasHeightDp.isFinite()) return false
    val clockWidth = if (isPortrait) 288f else 374f
    val clockHeight = if (isPortrait) 92f else 116f
    val relativeX = (layout.seconds.x - layout.clock.x) * canvasWidthDp
    val relativeY = (layout.seconds.y - layout.clock.y) * canvasHeightDp
    val halfWidth = clockWidth * layout.clock.scale / 2f + 8f
    val halfHeight = clockHeight * layout.clock.scale / 2f + 8f
    return abs(relativeX) <= halfWidth && abs(relativeY) <= halfHeight
}

private fun Modifier.panelTransform(
    transform: PanelTransform,
    canvasWidthDp: Float,
    canvasHeightDp: Float,
): Modifier = offset(
    x = (transform.x * canvasWidthDp).dp,
    y = (transform.y * canvasHeightDp).dp,
).graphicsLayer {
    scaleX = transform.scale
    scaleY = transform.scale
}

@Composable
private fun BoxScope.WeatherPanelCollection(
    weather: WeatherUiState?,
    message: String?,
    layout: StandScreenLayout,
    isPortrait: Boolean,
    isExpanded: Boolean,
    contentAlpha: Float,
    canvasWidthDp: Float,
    canvasHeightDp: Float,
) {
    layout.weatherGroupIds.distinct().sorted().forEach { groupId ->
        val pieces = WeatherPiece.entries.filter { layout.weatherGroupId(it) == groupId }
        val firstPiece = pieces.firstOrNull() ?: return@forEach
        WeatherGroupPanel(
            weather = weather,
            message = message,
            pieces = pieces,
            isPortrait = isPortrait,
            isExpanded = isExpanded,
            contentAlpha = contentAlpha,
            modifier = Modifier
                .align(Alignment.Center)
                .panelTransform(
                    transform = layout.weatherTransform(firstPiece),
                    canvasWidthDp = canvasWidthDp,
                    canvasHeightDp = canvasHeightDp,
                ),
        )
    }
}

@Composable
internal fun WeatherGroupPanel(
    weather: WeatherUiState?,
    message: String?,
    pieces: List<WeatherPiece>,
    isPortrait: Boolean,
    isExpanded: Boolean,
    contentAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val cellSize = if (isPortrait) 94.dp else 123.333.dp
    Surface(
        modifier = modifier.standPanelSurface(
            isDimmed = contentAlpha <= 0.2f,
            cornerRadius = if (isPortrait) 18.dp else 20.dp,
            splitGap = if (isPortrait) 4.dp else 3.dp,
        ),
        color = Color.Transparent,
        shape = RoundedCornerShape(if (isPortrait) 18.dp else 20.dp),
        shadowElevation = 0.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            pieces.forEach { piece ->
                WeatherPieceContent(
                    weather = weather,
                    message = message,
                    piece = piece,
                    contentAlpha = contentAlpha,
                    modifier = Modifier.size(cellSize),
                )
            }
        }
    }
}

@Composable
private fun WeatherPieceContent(
    weather: WeatherUiState?,
    message: String?,
    piece: WeatherPiece,
    contentAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val icon = when (weather?.weatherCode) {
        0 -> Icons.Default.WbSunny
        1, 2, 3, 45, 48 -> Icons.Default.Cloud
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Default.WaterDrop
        95, 96, 99 -> Icons.Default.Thunderstorm
        else -> Icons.Default.LocationOn
    }
    Box(
        modifier = modifier.padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (piece) {
            WeatherPiece.ICON -> Icon(
                imageVector = icon,
                contentDescription = weather?.let { weatherSummary(it.weatherCode) }
                    ?: "날씨 정보 확인 중",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                modifier = Modifier.size(34.dp),
            )

            WeatherPiece.TEMPERATURE -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = weather?.let { "${it.temperatureCelsius.roundToInt()}°" } ?: "--°",
                    color = Color.White.copy(alpha = contentAlpha),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = weather?.locationName ?: "현재 위치",
                    color = Color.White.copy(alpha = contentAlpha * 0.58f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }

            WeatherPiece.CONDITION -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = weather?.let { weatherSummary(it.weatherCode) }
                        ?: (message ?: "준비 중"),
                    color = Color.White.copy(alpha = contentAlpha * 0.78f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                weather?.let {
                    Text(
                        text = "체감 ${it.apparentTemperatureCelsius.roundToInt()}°",
                        color = Color.White.copy(alpha = contentAlpha * 0.56f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeControls(
    state: StandUiState,
    isPortrait: Boolean,
    isExpanded: Boolean,
    isTelevision: Boolean = false,
    onToggleTorch: () -> Unit,
    onCycleMode: () -> Unit,
    onToggleSession: () -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenAiShot: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBoyiso: () -> Unit,
    onToggleTheme: () -> Unit = {},
    onBrightnessAdjustmentStarted: () -> Unit = {},
    onBrightnessLevelChanged: (Float) -> Unit = {},
    onBrightnessAdjustmentFinished: () -> Unit = {},
    onClockScaleChanged: (Float) -> Unit = {},
    boyisoStatus: String,
    boyisoCanSendTokTok: Boolean,
    onSendBoyisoTokTok: () -> Unit,
) {
    val initialFocusRequester = remember { FocusRequester() }
    val rawControlOrder = if (isPortrait) {
        state.settings.portraitLayout.controlOrder
    } else {
        state.settings.landscapeLayout.controlOrder
    }
    val controlOrder = TvUiModePolicy.allowedControls(isTelevision, rawControlOrder)
    LaunchedEffect(isTelevision, state.isSessionActive) {
        if (isTelevision && state.isSessionActive) {
            initialFocusRequester.requestFocus()
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = if (isTelevision) 8 else if (isExpanded || !isPortrait) 7 else 4,
        horizontalArrangement = Arrangement.spacedBy(
            if (isTelevision) 5.dp else 7.dp,
            Alignment.CenterHorizontally,
        ),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        controlOrder.forEachIndexed { index, kind ->
            val initialFocusModifier = if (isTelevision && index == 0) {
                Modifier.focusRequester(initialFocusRequester)
            } else {
                Modifier
            }
            if (kind == StandControlKind.STOP_DETECTION) {
                if (!isTelevision) {
                    AutomaticRecordingControl(
                        state = state,
                        onToggleSession = onToggleSession,
                        modifier = initialFocusModifier,
                        isTelevision = false,
                    )
                }
                return@forEachIndexed
            }
            val defaultAction = when (kind) {
                StandControlKind.FLASHLIGHT -> onToggleTorch
                StandControlKind.BRIGHTNESS -> onCycleMode
                StandControlKind.STOP_DETECTION -> onToggleSession
                StandControlKind.ORIENTATION -> onToggleOrientation
                StandControlKind.RECORDINGS -> onOpenRecordings
                StandControlKind.AI_SHOT -> onOpenAiShot
                StandControlKind.SETTINGS -> onOpenSettings
                StandControlKind.BOYISO -> onOpenBoyiso
            }
            HomeControl(
                presentation = kind.presentation(state, boyisoStatus = boyisoStatus),
                onClick = if (kind == StandControlKind.BOYISO && boyisoCanSendTokTok) {
                    onSendBoyisoTokTok
                } else {
                    defaultAction
                },
                onLongClick = onOpenBoyiso.takeIf { kind == StandControlKind.BOYISO },
                onLongClickLabel = if (kind == StandControlKind.BOYISO) "보이소 설정 열기" else null,
                modifier = initialFocusModifier,
                isTelevision = isTelevision,
                isCompactTelevision = isTelevision,
                isSettingsControl = kind == StandControlKind.SETTINGS,
            )
        }
        if (isTelevision) {
            val themeFocusModifier = if (controlOrder.isEmpty()) {
                Modifier.focusRequester(initialFocusRequester)
            } else {
                Modifier
            }
            HomeControl(
                presentation = StandControlPresentation(
                    icon = Icons.Default.Palette,
                    title = "테마 전환",
                    status = state.settings.displayTheme.title,
                ),
                onClick = onToggleTheme,
                modifier = themeFocusModifier,
                isTelevision = true,
                isCompactTelevision = true,
            )
            HomeControl(
                presentation = StandControlPresentation(
                    icon = Icons.Default.LightMode,
                    title = "앱 밝기",
                    status = "${TvUiModePolicy.brightnessStep(state.displayBrightness)}/${TvUiModePolicy.BRIGHTNESS_STEP_COUNT} 단계",
                ),
                onClick = {
                    val next = TvUiModePolicy.stepBrightness(state.displayBrightness)
                    onBrightnessAdjustmentStarted()
                    onBrightnessLevelChanged(next)
                    onBrightnessAdjustmentFinished()
                },
                isTelevision = true,
                isCompactTelevision = true,
            )
            HomeControl(
                presentation = StandControlPresentation(
                    icon = Icons.Default.ZoomIn,
                    title = "시계 크기",
                    status = "${TvUiModePolicy.clockScaleStep(state.settings.clockScale)}/${TvUiModePolicy.CLOCK_SCALE_STEP_COUNT} 단계",
                ),
                onClick = {
                    val next = TvUiModePolicy.stepClockScale(state.settings.clockScale)
                    onClockScaleChanged(next)
                },
                isTelevision = true,
                isCompactTelevision = true,
            )
        }
    }
}

private const val MinimumSoundThresholdDb = -55f
private const val MaximumSoundThresholdDb = -18f

@Composable
private fun AutomaticRecordingControl(
    state: StandUiState,
    onToggleSession: () -> Unit,
    modifier: Modifier = Modifier,
    isTelevision: Boolean = false,
) {
    val thresholdFraction = soundThresholdFraction(state.effectiveSoundThresholdDB)
    val currentLevel = state.audioLevel.coerceIn(0f, 1f)
    val currentToggleSession by rememberUpdatedState(onToggleSession)

    Box(
        modifier = modifier
            .size(
                width = if (isTelevision) 138.dp else 203.dp,
                height = if (isTelevision) 52.dp else 66.dp,
            )
            .standFocusable(shape = RoundedCornerShape(14.dp))
            .standPanelSurface(
                isDimmed = false,
                cornerRadius = 14.dp,
                splitGap = 2.dp,
            )
            .clickable(onClick = currentToggleSession)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "자동 녹음, 현재 레벨 ${(currentLevel * 100).roundToInt()}퍼센트, " +
                    "자동 기준 ${(thresholdFraction * 100).roundToInt()}퍼센트"
            },
    ) {
        if (isTelevision) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.52f }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    imageVector = if (state.isSessionActive) Icons.Default.StopCircle else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "자동 녹음",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = if (state.audioRunning) "감지 중" else "대기",
                        color = Color.White.copy(alpha = 0.64f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        } else {
            AutomaticRecordingControlContent(
                state = state,
                showReorderHandle = false,
                isTelevision = false,
            )
        }
    }
}

@Composable
internal fun AutomaticRecordingControlContent(
    state: StandUiState,
    showReorderHandle: Boolean,
    isTelevision: Boolean = false,
) {
    val thresholdFraction = soundThresholdFraction(state.effectiveSoundThresholdDB)
    val currentLevel = state.audioLevel.coerceIn(0f, 1f)
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (state.isSessionActive) Icons.Default.StopCircle else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.78f),
                modifier = Modifier.size(if (isTelevision) 30.dp else 22.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (state.isSessionActive) "자동 녹음" else "자동 녹음 시작",
                color = Color.White.copy(alpha = 0.82f),
                style = if (isTelevision) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (state.audioRunning) "감지 중" else "대기",
                color = Color.White.copy(alpha = 0.48f),
                style = if (isTelevision) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
            )
        }

        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .height(20.dp),
        ) {
            val centerY = size.height / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(0f, centerY - 2f),
                size = androidx.compose.ui.geometry.Size(size.width, 4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.24f),
                topLeft = Offset(0f, centerY - 2f),
                size = androidx.compose.ui.geometry.Size(size.width * thresholdFraction, 4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            )
            val thresholdX = size.width * thresholdFraction
            drawLine(
                color = Color.White.copy(alpha = 0.52f),
                start = Offset(thresholdX, centerY - 7f),
                end = Offset(thresholdX, centerY + 7f),
                strokeWidth = 1f,
            )
            drawCircle(
                color = Color(0xFFFF8A2A).copy(alpha = 0.92f),
                radius = 4f,
                center = Offset(size.width * currentLevel, centerY),
            )
        }

        Text(
            text = if (state.audioRunning && state.noiseCalibrationProgress < 1f) {
                "현재 ${(currentLevel * 100).roundToInt()} · 주변 소리 학습 " +
                    "${(state.noiseCalibrationProgress.coerceIn(0f, 1f) * 100).roundToInt()}%"
            } else {
                "현재 ${(currentLevel * 100).roundToInt()} · 자동 기준 " +
                    "${(thresholdFraction * 100).roundToInt()}"
            },
            color = Color.White.copy(alpha = 0.52f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp),
        )
        if (showReorderHandle) {
            Text(
                text = "≡",
                color = Color(0xFFFF8A2A),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 7.dp),
            )
        }
    }
}

private fun soundThresholdFraction(soundThresholdDb: Float): Float =
    ((soundThresholdDb.coerceIn(MinimumSoundThresholdDb, MaximumSoundThresholdDb) + 70f) / 55f)
        .coerceIn(0f, 1f)

@Composable
private fun HomeControl(
    presentation: StandControlPresentation,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    modifier: Modifier = Modifier,
    isTelevision: Boolean = false,
    isCompactTelevision: Boolean = false,
    isSettingsControl: Boolean = false,
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 0.dp,
        modifier = modifier
            .size(
                width = when {
                    isSettingsControl && isCompactTelevision -> 52.dp
                    isCompactTelevision -> 112.dp
                    isTelevision -> 160.dp
                    else -> 98.dp
                },
                height = when {
                    isSettingsControl && isCompactTelevision -> 52.dp
                    isCompactTelevision -> 52.dp
                    isTelevision -> 100.dp
                    else -> 66.dp
                },
            )
            .standFocusable(shape = RoundedCornerShape(14.dp))
            .standPanelSurface(
                isDimmed = isCompactTelevision,
                cornerRadius = 14.dp,
                splitGap = 2.dp,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
            ),
    ) {
        StandControlTileContent(
            presentation = presentation,
            showReorderHandle = false,
            isTelevision = isTelevision,
            compactTelevision = isCompactTelevision,
            hideStatus = isSettingsControl && isCompactTelevision,
            dimmed = isCompactTelevision,
        )
    }
}

@Composable
private fun StatusBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 20.dp),
        color = Color(0xE629211B),
        shape = CircleShape,
        shadowElevation = 8.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun weatherSummary(code: Int): String = when (code) {
    0 -> "맑음"
    1 -> "대체로 맑음"
    2 -> "구름 조금"
    3 -> "흐림"
    45, 48 -> "안개"
    51, 53, 55, 56, 57 -> "이슬비"
    61, 63, 65, 66, 67 -> "비"
    71, 73, 75, 77 -> "눈"
    80, 81, 82 -> "소나기"
    85, 86 -> "눈 소나기"
    95, 96, 99 -> "뇌우"
    else -> "날씨 정보"
}
