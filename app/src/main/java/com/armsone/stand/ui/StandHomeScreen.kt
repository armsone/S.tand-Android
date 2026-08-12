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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
import com.armsone.stand.model.InternetRadioConfiguration
import com.armsone.stand.model.LampPhase
import com.armsone.stand.model.OrientationPreference
import com.armsone.stand.model.PanelTransform
import com.armsone.stand.model.StandDisplayTheme
import com.armsone.stand.model.StandControlKind
import com.armsone.stand.model.StandScreenLayout
import com.armsone.stand.model.SimplifiedBrightnessModePolicy
import com.armsone.stand.model.WeatherPiece
import com.armsone.stand.platform.InternetRadioState
import com.armsone.stand.platform.RadioVolumePolicy
import com.armsone.stand.ui.components.ClockDateAndSeconds
import com.armsone.stand.ui.components.ClockSeconds
import com.armsone.stand.ui.components.FlipClock
import com.armsone.stand.ui.components.standPanelSurface
import com.armsone.stand.ui.components.rememberBurnInOffset
import com.armsone.stand.ui.theme.lampGradientColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun StandHomeScreen(
    state: StandUiState,
    onScreenTap: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenEditor: () -> Unit,
    onBrightnessAdjustmentStarted: () -> Unit,
    onBrightnessLevelChanged: (Float) -> Unit,
    onBrightnessAdjustmentFinished: () -> Unit,
    onInternetRadioVolumeChanged: (Float) -> Unit,
    onClockScaleChanged: (Float) -> Unit,
    onToggleTorch: () -> Unit,
    onCycleMode: () -> Unit,
    onToggleSession: () -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenAiShot: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleRadio: (String) -> Unit,
    onEditRadio: (String) -> Unit,
    modifier: Modifier = Modifier,
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
        val isPortrait = maxHeight > maxWidth
        val isExpanded = maxWidth >= 600.dp
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

        HomeGestureLayer(
            state = state,
            onScreenTap = onScreenTap,
            onOpenEditor = onOpenEditor,
            onToggleTheme = onToggleTheme,
            onBrightnessAdjustmentStarted = onBrightnessAdjustmentStarted,
            onBrightnessLevelChanged = { value ->
                onBrightnessLevelChanged(value)
                adjustmentFeedback = HomeAdjustmentFeedback(
                    title = "앱 밝기",
                    value = "${(value * 100f).roundToInt()}%",
                )
            },
            onBrightnessAdjustmentFinished = {
                onBrightnessAdjustmentFinished()
                adjustmentFeedback = null
            },
            onInternetRadioVolumeChanged = { value ->
                onInternetRadioVolumeChanged(value)
                adjustmentFeedback = HomeAdjustmentFeedback(
                    title = "라디오 볼륨",
                    value = "${(value * 100f).roundToInt()}%",
                )
            },
            onRadioVolumeAdjustmentFinished = { adjustmentFeedback = null },
            onClockScaleChanged = { value ->
                onClockScaleChanged(value)
                adjustmentFeedback = HomeAdjustmentFeedback(
                    title = "시계 크기",
                    value = "${(value * 100f).roundToInt()}%",
                )
            },
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
                    onToggleRadio = onToggleRadio,
                    onEditRadio = onEditRadio,
                    onClockTap = onScreenTap,
                    onClockDoubleTap = {},
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.safeDrawing.asPaddingValues())
                        .padding(horizontal = if (isPortrait) 16.dp else 28.dp),
                )
            } else {
                StandStartContent(
                    onStart = onToggleSession,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(
                        start = if (isPortrait) 16.dp else 28.dp,
                        top = 14.dp,
                        end = if (isPortrait) 16.dp else 28.dp,
                        bottom = if (isPortrait) 28.dp else 18.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Header(state = state, contentAlpha = contentAlpha)
                Spacer(Modifier.weight(1f))

                HomeControls(
                    state = state,
                    isPortrait = isPortrait,
                    isExpanded = isExpanded,
                    onToggleTorch = onToggleTorch,
                    onCycleMode = onCycleMode,
                    onToggleSession = onToggleSession,
                    onToggleOrientation = onToggleOrientation,
                    onOpenRecordings = onOpenRecordings,
                    onOpenAiShot = onOpenAiShot,
                    onOpenSettings = onOpenSettings,
                )
            }

            Text(
                text = "${BuildConfig.BUILD_NUMBER} · 밝기 " +
                    "${(state.lampIntensity.coerceIn(0f, 1f) * 100f).roundToInt()}%",
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
                            "빌드 번호 ${BuildConfig.BUILD_NUMBER}, 현재 밝기 " +
                            "${(state.lampIntensity.coerceIn(0f, 1f) * 100f).roundToInt()}퍼센트"
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
        } else if (state.isWritingClip) {
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

@Composable
private fun StandStartContent(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                .semantics {
                    contentDescription = "S.tand 시작"
                },
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
                imageVector = if (feedback.title == "라디오 볼륨") {
                    Icons.AutoMirrored.Filled.VolumeUp
                } else {
                    Icons.Default.LightMode
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
    onInternetRadioVolumeChanged: (Float) -> Unit,
    onRadioVolumeAdjustmentFinished: () -> Unit,
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
    val latestOnInternetRadioVolumeChanged = rememberUpdatedState(
        onInternetRadioVolumeChanged,
    )
    val latestOnRadioVolumeAdjustmentFinished = rememberUpdatedState(
        onRadioVolumeAdjustmentFinished,
    )
    val latestOnClockScaleChanged = rememberUpdatedState(onClockScaleChanged)
    var layerHeightPx by remember { mutableStateOf(0f) }
    val bottomGestureExclusionPx = with(LocalDensity.current) { 104.dp.toPx() }

    Box(
        modifier = modifier
            .onSizeChanged { layerHeightPx = it.height.toFloat() }
            .semantics {
                val currentLevel = state.lampIntensity.coerceIn(0f, 1f)
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
                        onClockScaleChanged((state.settings.clockScale + 0.1f).coerceAtMost(1.35f))
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
                            delay(viewConfiguration.longPressTimeoutMillis)
                            longPressTriggered = true
                            pendingSingleTap?.cancel()
                            previousTapUpTime = 0L
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            latestOnOpenEditor.value()
                        }
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                        longPressJob.cancel()

                        if (up != null && !longPressTriggered) {
                            val isDoubleTap = previousTapUpTime > 0L &&
                                up.uptimeMillis - previousTapUpTime <=
                                viewConfiguration.doubleTapTimeoutMillis &&
                                (up.position - previousTapPosition).getDistance() <=
                                viewConfiguration.touchSlop * 4f

                            if (isDoubleTap) {
                                pendingSingleTap?.cancel()
                                pendingSingleTap = null
                                previousTapUpTime = 0L
                            } else {
                                previousTapUpTime = up.uptimeMillis
                                previousTapPosition = up.position
                                pendingSingleTap?.cancel()
                                pendingSingleTap = launch {
                                    delay(viewConfiguration.doubleTapTimeoutMillis)
                                    previousTapUpTime = 0L
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    latestOnScreenTap.value()
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    var axis: HomeAdjustmentAxis? = null
                    var startingLevel = 0f
                    var startingSpan = 0f
                    var finished = false

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
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
                                startingLevel = current.lampIntensity
                                    .takeIf { current.isSessionActive }
                                    ?: current.settings.lampIntensity
                                latestOnBrightnessAdjustmentStarted.value()
                                HomeAdjustmentAxis.VERTICAL_BRIGHTNESS
                            } else {
                                startingLevel = latestState.value.internetRadioVolume
                                HomeAdjustmentAxis.HORIZONTAL_RADIO_VOLUME
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
                            HomeAdjustmentAxis.HORIZONTAL_RADIO_VOLUME -> {
                                change.consume()
                                latestOnInternetRadioVolumeChanged.value(
                                    RadioVolumePolicy.level(
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
                            } else if (axis == HomeAdjustmentAxis.HORIZONTAL_RADIO_VOLUME) {
                                latestOnRadioVolumeAdjustmentFinished.value()
                            }
                            finished = true
                        }
                    } while (!finished)

                    if (!finished && axis == HomeAdjustmentAxis.VERTICAL_BRIGHTNESS) {
                        latestOnBrightnessAdjustmentFinished.value()
                    } else if (!finished && axis == HomeAdjustmentAxis.HORIZONTAL_RADIO_VOLUME) {
                        latestOnRadioVolumeAdjustmentFinished.value()
                    }
                }
            },
    ) { content() }
}

private enum class HomeAdjustmentAxis {
    VERTICAL_BRIGHTNESS,
    HORIZONTAL_RADIO_VOLUME,
    CLOCK_SCALE,
}

@Composable
private fun Header(state: StandUiState, contentAlpha: Float) {
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = if (!state.isSessionActive) {
                    Icons.Default.StopCircle
                } else if (state.settings.modePreference == com.armsone.stand.model.StandModePreference.OBJECT) {
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
                    state.settings.modePreference == com.armsone.stand.model.StandModePreference.OBJECT ->
                        "오브제 모드 잠금"
                    else -> state.experienceMode.title
                },
                color = Color.White.copy(alpha = contentAlpha * 0.62f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center),
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
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (state.isCharging) {
                    Icons.Default.BatteryChargingFull
                } else {
                    Icons.Default.AutoAwesome
                },
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

@Composable
private fun DashboardCanvas(
    state: StandUiState,
    isPortrait: Boolean,
    contentAlpha: Float,
    burnInOffset: com.armsone.stand.ui.components.BurnInOffset,
    isExpanded: Boolean,
    onToggleRadio: (String) -> Unit,
    onEditRadio: (String) -> Unit,
    onClockTap: () -> Unit,
    onClockDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
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
                modifier = Modifier
                    .align(Alignment.Center)
                    .combinedClickable(
                        onClick = onClockTap,
                        onDoubleClick = onClockDoubleTap,
                    )
                    .panelTransform(layout.clock, canvasWidth.value, canvasHeight.value),
            )

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
                modifier = Modifier
                    .align(Alignment.Center)
                    .panelTransform(layout.seconds, canvasWidth.value, canvasHeight.value),
            )

            ClockDateAndSeconds(
                hourMode = state.settings.clockHourMode,
                contentAlpha = contentAlpha,
                modifier = Modifier
                    .align(Alignment.Center)
                    .panelTransform(layout.date, canvasWidth.value, canvasHeight.value),
            )

            if (state.isDisplayDark) {
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
                            imageVector = if (state.isCharging) {
                                Icons.Default.BatteryChargingFull
                            } else {
                                Icons.Default.AutoAwesome
                            },
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

            val radioConfigurations = state.settings.internetRadioChannels.take(2)
            if (radioConfigurations.size == 2 && layout.radiosGrouped) {
                GroupedRadioPanel(
                    state = state,
                    configurations = radioConfigurations,
                    contentAlpha = contentAlpha,
                    onClick = onToggleRadio,
                    onLongClick = onEditRadio,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .panelTransform(layout.radio, canvasWidth.value, canvasHeight.value),
                )
            } else {
                radioConfigurations.forEachIndexed { index, configuration ->
                    val transform = if (index == 0) layout.radio else layout.secondaryRadio
                    RadioPanel(
                        state = state,
                        configuration = configuration,
                        contentAlpha = contentAlpha,
                        onClick = { onToggleRadio(configuration.id) },
                        onLongClick = { onEditRadio(configuration.id) },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .panelTransform(transform, canvasWidth.value, canvasHeight.value),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RadioPanel(
    state: StandUiState,
    configuration: InternetRadioConfiguration?,
    contentAlpha: Float,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
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
        isActive && state.internetRadioState is InternetRadioState.Loading ->
            "인터넷 라디오 연결을 취소합니다."
        isActive && state.internetRadioState is InternetRadioState.Reconnecting ->
            "인터넷 라디오 연결을 취소합니다."
        isActive && state.internetRadioState is InternetRadioState.Playing ->
            "인터넷 라디오를 끄고 소리 감지와 녹음을 다시 시작합니다."
        else -> "등록한 인터넷 라디오를 재생합니다."
    }
    Surface(
        modifier = modifier
            .width(144.dp)
            .height(60.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $detail. $accessibilityHint"
                stateDescription = detail
                role = Role.Button
            }
            .then(
                if (drawsSurface) {
                    Modifier.standPanelSurface(
                        isDimmed = contentAlpha <= 0.2f,
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
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (isActive && state.internetRadioState is InternetRadioState.Playing) {
                    Icons.Default.StopCircle
                } else {
                    Icons.Default.GraphicEq
                },
                contentDescription = null,
                tint = Color.White.copy(alpha = contentAlpha),
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = contentAlpha),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                Text(
                    text = detail,
                    color = Color.White.copy(alpha = contentAlpha * 0.58f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun GroupedRadioPanel(
    state: StandUiState,
    configurations: List<InternetRadioConfiguration>,
    contentAlpha: Float,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .width(288.dp)
            .height(60.dp)
            .standPanelSurface(
                isDimmed = contentAlpha <= 0.2f,
                cornerRadius = 13.dp,
                splitGap = 2.dp,
            ),
    ) {
        configurations.take(2).forEach { configuration ->
            RadioPanel(
                state = state,
                configuration = configuration,
                contentAlpha = contentAlpha,
                onClick = { onClick(configuration.id) },
                onLongClick = { onLongClick(configuration.id) },
                drawsSurface = false,
            )
        }
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
    onToggleTorch: () -> Unit,
    onCycleMode: () -> Unit,
    onToggleSession: () -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenAiShot: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val controlOrder = if (isPortrait) {
        state.settings.portraitLayout.controlOrder
    } else {
        state.settings.landscapeLayout.controlOrder
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = if (isExpanded || !isPortrait) 7 else 4,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        controlOrder.forEach { kind ->
            if (kind == StandControlKind.STOP_DETECTION) {
                AutomaticRecordingControl(
                    state = state,
                    onToggleSession = onToggleSession,
                )
                return@forEach
            }
            val action = when (kind) {
                StandControlKind.FLASHLIGHT -> onToggleTorch
                StandControlKind.BRIGHTNESS -> onCycleMode
                StandControlKind.STOP_DETECTION -> onToggleSession
                StandControlKind.ORIENTATION -> onToggleOrientation
                StandControlKind.RECORDINGS -> onOpenRecordings
                StandControlKind.AI_SHOT -> onOpenAiShot
                StandControlKind.SETTINGS -> onOpenSettings
            }
            HomeControl(presentation = kind.presentation(state), onClick = action)
        }
    }
}

private const val MinimumSoundThresholdDb = -55f
private const val MaximumSoundThresholdDb = -18f

@Composable
private fun AutomaticRecordingControl(
    state: StandUiState,
    onToggleSession: () -> Unit,
) {
    val thresholdFraction = soundThresholdFraction(state.effectiveSoundThresholdDB)
    val currentLevel = state.audioLevel.coerceIn(0f, 1f)
    val currentToggleSession by rememberUpdatedState(onToggleSession)

    Box(
        modifier = Modifier
            .size(width = 203.dp, height = 66.dp)
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
        AutomaticRecordingControlContent(state = state, showReorderHandle = false)
    }
}

@Composable
internal fun AutomaticRecordingControlContent(
    state: StandUiState,
    showReorderHandle: Boolean,
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
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (state.isSessionActive) "자동 녹음" else "자동 녹음 시작",
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (state.audioRunning) "감지 중" else "대기",
                color = Color.White.copy(alpha = 0.48f),
                style = MaterialTheme.typography.labelSmall,
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
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 0.dp,
        modifier = Modifier
            .size(width = 98.dp, height = 66.dp)
            .standPanelSurface(
                isDimmed = false,
                cornerRadius = 14.dp,
                splitGap = 2.dp,
            ),
    ) {
        StandControlTileContent(
            presentation = presentation,
            showReorderHandle = false,
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
