@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.armsone.stand.ui

import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.armsone.stand.model.FloatOffset
import com.armsone.stand.model.FloatInsets
import com.armsone.stand.model.FloatRect
import com.armsone.stand.model.FloatSize
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockHourMode
import com.armsone.stand.model.ClockVisualPolicy
import com.armsone.stand.model.HomeEditorResetPolicy
import com.armsone.stand.model.OrientationPreference
import com.armsone.stand.model.PanelEditingPolicy
import com.armsone.stand.model.PanelTransform
import com.armsone.stand.model.RadioGroupPolicy
import com.armsone.stand.model.StandControlKind
import com.armsone.stand.model.StandDisplayTheme
import com.armsone.stand.model.StandScreenLayout
import com.armsone.stand.model.WeatherGroupPolicy
import com.armsone.stand.model.WeatherPiece
import com.armsone.stand.ui.components.ClockDateAndSeconds
import com.armsone.stand.ui.components.ClockSeconds
import com.armsone.stand.ui.components.FlipClock
import com.armsone.stand.ui.components.flipTextSplitMask
import com.armsone.stand.ui.components.standPanelSurface
import com.armsone.stand.ui.theme.fontFamily
import com.armsone.stand.ui.theme.fontWeight
import com.armsone.stand.ui.theme.lampGradientColors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign
import java.time.LocalDateTime

/**
 * Edits one orientation's draft layout. The caller owns the draft and decides when [onSave]
 * becomes persistent; [onCancel] therefore remains side-effect free.
 */
@Composable
fun ScreenEditorScreen(
    state: StandUiState,
    layout: StandScreenLayout,
    clockFont: ClockFontChoice,
    clockHourMode: ClockHourMode,
    isPortrait: Boolean,
    onLayoutChange: (StandScreenLayout) -> Unit,
    onClockFontChange: (ClockFontChoice) -> Unit,
    onClockHourModeChange: (ClockHourMode) -> Unit,
    onManageRadios: () -> Unit,
    onSave: (StandScreenLayout, ClockFontChoice, ClockHourMode) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    catalogNow: LocalDateTime? = null,
) {
    var selectedPanel by rememberSaveable(stateSaver = EditorPanelKeySaver) {
        mutableStateOf<EditorPanelKey>(EditorPanelKey.Clock)
    }
    var showFontPalette by rememberSaveable { mutableStateOf(false) }
    val measuredPanelSizes = remember { mutableStateMapOf<EditorPanelKey, FloatSize>() }
    val latestLayout = rememberUpdatedState(layout)
    val latestClockFont = rememberUpdatedState(clockFont)
    val latestClockHourMode = rememberUpdatedState(clockHourMode)
    val validPanels = panelKeys(layout, state.settings.internetRadioChannels.size)

    LaunchedEffect(validPanels, selectedPanel) {
        if (selectedPanel !in validPanels) selectedPanel = validPanels.first()
    }
    LaunchedEffect(selectedPanel) {
        if (selectedPanel != EditorPanelKey.Clock) {
            showFontPalette = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val expanded = maxWidth >= 600.dp
        val visibleIntensity = state.settings.lampIntensity.coerceIn(0.15f, 1f)
        val contentAlpha = (0.28f + visibleIntensity * 0.72f).coerceIn(0.28f, 1f)
        val density = LocalDensity.current
        val protectedInsets = FloatInsets(
            top = with(density) { (if (isPortrait) 82.dp else 70.dp).toPx() },
            bottom = with(density) {
                when {
                    showFontPalette && isPortrait -> 230.dp.toPx()
                    showFontPalette -> 160.dp.toPx()
                    else -> 100.dp.toPx()
                }
            },
        )
        val backgroundRadiusPx = with(density) {
            hypot(maxWidth.toPx(), maxHeight.toPx()) * 0.75f
        }
        val gradientColors = lampGradientColors(state.settings.displayTheme, visibleIntensity)

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

        PanelCanvas(
            state = state,
            clockFont = clockFont,
            clockHourMode = clockHourMode,
            layout = layout,
            isPortrait = isPortrait,
            isExpanded = expanded,
            contentAlpha = contentAlpha,
            protectedInsets = protectedInsets,
            selectedPanel = selectedPanel,
            onSelectedPanelChange = { key ->
                selectedPanel = key
                if (key != EditorPanelKey.Clock) showFontPalette = false
            },
            onClockClick = {
                selectedPanel = EditorPanelKey.Clock
                showFontPalette = !showFontPalette
            },
            onClockDoubleClick = {
                onClockHourModeChange(
                    if (clockHourMode == ClockHourMode.TWELVE) {
                        ClockHourMode.TWENTY_FOUR
                    } else {
                        ClockHourMode.TWELVE
                    },
                )
            },
            onManageRadios = onManageRadios,
            onLayoutChange = onLayoutChange,
            measuredPanelSizes = measuredPanelSizes,
            catalogNow = catalogNow,
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = if (isPortrait) 16.dp else 28.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .widthIn(max = 720.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EditorHeader(
                title = if (isPortrait) {
                    "세로 패널 편집"
                } else {
                    "가로 패널 편집"
                },
                resetDescription = "패널 배치 초기화",
                onCancel = onCancel,
                onReset = {
                    val currentLayout = latestLayout.value
                    onLayoutChange(HomeEditorResetPolicy.panels(currentLayout, isPortrait))
                },
                onSave = {
                    onSave(
                        latestLayout.value.copy(),
                        latestClockFont.value,
                        latestClockHourMode.value,
                    )
                },
            )
        }

        if (
            selectedPanel == EditorPanelKey.Clock &&
            showFontPalette
        ) {
            EditorClockFontPalette(
                selectedFont = clockFont,
                isPortrait = isPortrait,
                isExpanded = expanded,
                onFontSelected = onClockFontChange,
                modifier = Modifier
                    .align(if (isPortrait) Alignment.BottomCenter else Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .widthIn(max = if (isPortrait) 760.dp else 230.dp),
            )
        } else {
            Surface(
                modifier = Modifier
                    .align(if (isPortrait) Alignment.BottomCenter else Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
                color = Color(0xE64A2D24),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            ) {
                Text(
                    text = "패널을 끌어 이동 · 왼쪽 위 손잡이로 크기 조절",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun EditorHeader(
    title: String,
    resetDescription: String,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "화면 편집 취소")
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onReset) {
                Icon(Icons.Default.RestartAlt, contentDescription = resetDescription)
            }
            TextButton(onClick = onSave) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("저장")
            }
        }
    }
}

@Composable
private fun PanelCanvas(
    state: StandUiState,
    clockFont: ClockFontChoice,
    clockHourMode: ClockHourMode,
    layout: StandScreenLayout,
    isPortrait: Boolean,
    isExpanded: Boolean,
    contentAlpha: Float,
    protectedInsets: FloatInsets,
    selectedPanel: EditorPanelKey,
    catalogNow: LocalDateTime?,
    onSelectedPanelChange: (EditorPanelKey) -> Unit,
    onClockClick: () -> Unit,
    onClockDoubleClick: () -> Unit,
    onManageRadios: () -> Unit,
    onLayoutChange: (StandScreenLayout) -> Unit,
    measuredPanelSizes: MutableMap<EditorPanelKey, FloatSize>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize(),
    ) {
        Canvas(Modifier.fillMaxSize().padding(1.dp)) {
            drawLine(
                color = Color.White.copy(alpha = 0.14f),
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1f,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.14f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1f,
            )
        }

        val canvas = FloatSize(
            width = constraints.maxWidth.toFloat(),
            height = constraints.maxHeight.toFloat(),
        )
        val canvasWidthDp = maxWidth.value
        val canvasHeightDp = maxHeight.value
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = state.settings.clockScale
                    scaleY = state.settings.clockScale
                },
        ) {
            val radioConfigurations = state.settings.internetRadioChannels.take(2)
            val fixedPanels = buildList {
                add(EditorPanelKey.Clock)
                add(EditorPanelKey.Seconds)
                add(EditorPanelKey.Date)
                add(EditorPanelKey.Battery)
                add(EditorPanelKey.Radio)
                if (radioConfigurations.isNotEmpty() && !layout.radiosGrouped) {
                    add(EditorPanelKey.SecondaryRadio)
                }
            }
            fixedPanels.forEach { key ->
                EditablePanelNode(
                    key = key,
                    transform = layout.transformFor(key),
                    canvasSize = canvas,
                    screenScale = state.settings.clockScale,
                    protectedInsets = protectedInsets,
                    selected = selectedPanel == key,
                    onClick = when {
                        key == EditorPanelKey.Clock -> onClockClick
                        key == EditorPanelKey.Radio &&
                            radioConfigurations.size == 2 &&
                            layout.radiosGrouped -> {
                            {
                                onLayoutChange(RadioGroupPolicy.split(layout))
                                onSelectedPanelChange(EditorPanelKey.Radio)
                            }
                        }
                        key == EditorPanelKey.Radio && radioConfigurations.isEmpty() -> onManageRadios
                        key == EditorPanelKey.SecondaryRadio &&
                            radioConfigurations.getOrNull(1) == null -> onManageRadios
                        else -> ({ onSelectedPanelChange(key) })
                    },
                    onGestureSelect = { onSelectedPanelChange(key) },
                    onDoubleClick = when {
                        key == EditorPanelKey.Clock -> onClockDoubleClick
                        key == EditorPanelKey.Radio &&
                            radioConfigurations.size == 2 &&
                            layout.radiosGrouped -> {
                            {
                                onLayoutChange(RadioGroupPolicy.split(layout))
                                onSelectedPanelChange(EditorPanelKey.Radio)
                            }
                        }
                        else -> null
                    },
                    onTransformChange = { transform ->
                        onLayoutChange(layout.withPanelTransform(key, transform))
                    },
                    onTransformEnd = if (
                        radioConfigurations.size == 2 &&
                        !layout.radiosGrouped &&
                        (key == EditorPanelKey.Radio || key == EditorPanelKey.SecondaryRadio)
                    ) {
                        { finalTransform ->
                            val movedLayout = layout.withPanelTransform(key, finalTransform)
                            val firstSize = measuredPanelSizes[EditorPanelKey.Radio]
                            val secondSize = measuredPanelSizes[EditorPanelKey.SecondaryRadio]
                            val mergedLayout = if (firstSize != null && secondSize != null) {
                                RadioGroupPolicy.mergeIfOverlapping(
                                    layout = movedLayout,
                                    firstBounds = PanelEditingPolicy.transformedBounds(
                                        transform = movedLayout.radio,
                                        panelSize = firstSize,
                                        canvasSize = canvas,
                                        screenScale = state.settings.clockScale,
                                    ),
                                    secondBounds = PanelEditingPolicy.transformedBounds(
                                        transform = movedLayout.secondaryRadio,
                                        panelSize = secondSize,
                                        canvasSize = canvas,
                                        screenScale = state.settings.clockScale,
                                    ),
                                )
                            } else {
                                movedLayout
                            }
                            onLayoutChange(mergedLayout)
                            if (mergedLayout.radiosGrouped) {
                                onSelectedPanelChange(EditorPanelKey.Radio)
                            }
                        }
                    } else {
                        null
                    },
                    onMeasured = { measuredPanelSizes[key] = it },
                ) {
                    if (key == EditorPanelKey.Radio || key == EditorPanelKey.SecondaryRadio) {
                        LiveEditorSelection(selected = selectedPanel == key) {
                            if (key == EditorPanelKey.Radio &&
                                radioConfigurations.size == 2 &&
                                layout.radiosGrouped
                            ) {
                                GroupedRadioPanel(
                                    state = state,
                                    configurations = radioConfigurations,
                                    contentAlpha = contentAlpha,
                                    onClick = {
                                        onLayoutChange(RadioGroupPolicy.split(layout))
                                        onSelectedPanelChange(EditorPanelKey.Radio)
                                    },
                                )
                            } else {
                                val configuration = if (key == EditorPanelKey.SecondaryRadio) {
                                    radioConfigurations.getOrNull(1)
                                } else {
                                    radioConfigurations.firstOrNull()
                                }
                                RadioPanel(
                                    state = state,
                                    configuration = configuration,
                                    contentAlpha = contentAlpha,
                                    onClick = if (configuration == null) {
                                        onManageRadios
                                    } else {
                                        { onSelectedPanelChange(key) }
                                    },
                                )
                            }
                        }
                    } else {
                        LiveEditorPanelContent(
                            key = key,
                            state = state,
                            clockFont = clockFont,
                            clockHourMode = clockHourMode,
                            isPortrait = isPortrait,
                            contentAlpha = contentAlpha,
                            selected = selectedPanel == key,
                            secondsShowsBackground = !clockSecondsOverlapsClock(
                                layout = layout,
                                canvasWidthDp = canvasWidthDp,
                                canvasHeightDp = canvasHeightDp,
                                isPortrait = isPortrait,
                            ),
                            catalogNow = catalogNow,
                        )
                    }
                }
            }

            layout.weatherGroupIds.distinct().sorted().forEach { groupId ->
                val key = EditorPanelKey.Weather(groupId)
                val pieces = layout.weatherPieces(groupId)
                EditablePanelNode(
                    key = key,
                    transform = layout.transformFor(key),
                    canvasSize = canvas,
                    screenScale = state.settings.clockScale,
                    protectedInsets = protectedInsets,
                    selected = selectedPanel == key,
                    onClick = { onSelectedPanelChange(key) },
                    onGestureSelect = { onSelectedPanelChange(key) },
                    onDoubleClick = if (pieces.size > 1) {
                        {
                            val split = WeatherGroupPolicy.splitGroup(layout, groupId)
                            onLayoutChange(split)
                            onSelectedPanelChange(EditorPanelKey.Weather(pieces.first().index))
                        }
                    } else {
                        null
                    },
                    onTransformChange = { transform ->
                        onLayoutChange(layout.withPanelTransform(key, transform))
                    },
                    onTransformEnd = { finalTransform ->
                        val movedLayout = layout.withPanelTransform(key, finalTransform)
                        bestWeatherMergeCandidate(
                            layout = movedLayout,
                            sourceGroupId = groupId,
                            canvasSize = canvas,
                            screenScale = state.settings.clockScale,
                            measuredPanelSizes = measuredPanelSizes,
                        )?.let { merged ->
                            onLayoutChange(merged.layout)
                            onSelectedPanelChange(EditorPanelKey.Weather(merged.targetGroupId))
                        } ?: onLayoutChange(movedLayout)
                    },
                    onMeasured = { measuredPanelSizes[key] = it },
                ) {
                    LiveWeatherPanelContent(
                        state = state,
                        pieces = pieces,
                        isPortrait = isPortrait,
                        isExpanded = isExpanded,
                        contentAlpha = contentAlpha,
                        selected = selectedPanel == key,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.EditablePanelNode(
    key: EditorPanelKey,
    transform: PanelTransform,
    canvasSize: FloatSize,
    screenScale: Float,
    protectedInsets: FloatInsets,
    selected: Boolean,
    onClick: () -> Unit,
    onGestureSelect: () -> Unit = onClick,
    onTransformChange: (PanelTransform) -> Unit,
    onMeasured: (FloatSize) -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    onTransformEnd: ((PanelTransform) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var measuredSize by remember(key) { mutableStateOf(FloatSize(0f, 0f)) }
    var workingTransform by remember(key) { mutableStateOf(transform) }
    val latestOnGestureSelect by rememberUpdatedState(onGestureSelect)
    val latestOnTransformChange by rememberUpdatedState(onTransformChange)
    val latestOnTransformEnd = rememberUpdatedState(onTransformEnd)
    val transformGestureSession = remember(key) { PanelTransformGestureSession() }
    val safeScreenScale = screenScale.takeIf { it.isFinite() && it > 0f } ?: 1f

    fun clamp(updated: PanelTransform): PanelTransform = PanelEditingPolicy.clampedTransform(
        transform = updated.copy(scale = PanelEditingPolicy.clampScale(updated.scale)),
        panelSize = measuredSize,
        canvasSize = canvasSize,
        insets = protectedInsets,
        screenScale = screenScale,
    )

    LaunchedEffect(transform, measuredSize, canvasSize, protectedInsets, screenScale) {
        val clamped = clamp(transform)
        workingTransform = clamped
        if (clamped != transform && measuredSize.width > 0f && measuredSize.height > 0f) {
            latestOnTransformChange(clamped)
        }
    }

    fun applyAccessibleTransform(updated: PanelTransform) {
        val safe = clamp(updated)
        workingTransform = safe
        latestOnTransformEnd.value?.invoke(safe) ?: latestOnTransformChange(safe)
    }

    val panelAccessibilityActions = listOf(
        CustomAccessibilityAction("위로 5퍼센트 이동") {
            applyAccessibleTransform(workingTransform.copy(y = workingTransform.y - 0.05f))
            true
        },
        CustomAccessibilityAction("아래로 5퍼센트 이동") {
            applyAccessibleTransform(workingTransform.copy(y = workingTransform.y + 0.05f))
            true
        },
        CustomAccessibilityAction("왼쪽으로 5퍼센트 이동") {
            applyAccessibleTransform(workingTransform.copy(x = workingTransform.x - 0.05f))
            true
        },
        CustomAccessibilityAction("오른쪽으로 5퍼센트 이동") {
            applyAccessibleTransform(workingTransform.copy(x = workingTransform.x + 0.05f))
            true
        },
        CustomAccessibilityAction("10퍼센트 확대") {
            applyAccessibleTransform(workingTransform.copy(scale = workingTransform.scale + 0.1f))
            true
        },
        CustomAccessibilityAction("10퍼센트 축소") {
            applyAccessibleTransform(workingTransform.copy(scale = workingTransform.scale - 0.1f))
            true
        },
        CustomAccessibilityAction("열기") {
            onClick()
            true
        },
    )

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                translationX = workingTransform.x * canvasSize.width
                translationY = workingTransform.y * canvasSize.height
                scaleX = workingTransform.scale
                scaleY = workingTransform.scale
            }
            .zIndex(if (selected) 2f else 1f)
            .testTag(key.testTag)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "${key.title} 패널. 끌어서 이동하고 왼쪽 위 손잡이로 크기를 조절합니다."
                stateDescription = "크기 ${(workingTransform.scale * 100).roundToInt()}퍼센트"
                role = Role.Button
                customActions = panelAccessibilityActions
            }
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = onDoubleClick,
            )
            .pointerInput(key, canvasSize) {
                detectTransformGestures { _, pan, zoom, _ ->
                    latestOnGestureSelect()
                    val current = workingTransform
                    val proposed = current.copy(
                        x = current.x + pan.x / canvasSize.width.coerceAtLeast(1f),
                        y = current.y + pan.y / canvasSize.height.coerceAtLeast(1f),
                        scale = PanelEditingPolicy.clampScale(current.scale * zoom),
                    )
                    val snapped = PanelEditingPolicy.snappedTransform(
                        transform = proposed,
                        panelSize = measuredSize,
                        canvasSize = canvasSize,
                    )
                    workingTransform = clamp(snapped)
                    transformGestureSession.markChanged()
                }
            }
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pressed: Boolean
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        pressed = event.changes.any { it.pressed }
                    } while (pressed)
                    if (transformGestureSession.consumeChanged()) {
                        val finalTransform = workingTransform
                        latestOnTransformEnd.value?.invoke(finalTransform)
                            ?: latestOnTransformChange(finalTransform)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.onSizeChanged {
                measuredSize = FloatSize(it.width.toFloat(), it.height.toFloat())
                onMeasured(measuredSize)
            },
        ) {
            content()
        }
    }

    if (selected && measuredSize.width > 0f && measuredSize.height > 0f) {
        // Keep the 48dp touch target outside the panel's own scale layer. This keeps
        // a small panel resizable and anchors the handle centre to its visible corner.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .graphicsLayer {
                    translationX = workingTransform.x * canvasSize.width -
                        measuredSize.width * workingTransform.scale / 2f
                    translationY = workingTransform.y * canvasSize.height -
                        measuredSize.height * workingTransform.scale / 2f
                    scaleX = 1f / safeScreenScale
                    scaleY = 1f / safeScreenScale
                }
                .zIndex(5f)
                .semantics {
                    contentDescription = "${key.title} 패널 크기 조절"
                    role = Role.Button
                }
                .pointerInput(key, measuredSize, canvasSize, safeScreenScale) {
                    var startTransform = workingTransform
                    var cumulativeDrag = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            startTransform = workingTransform
                            cumulativeDrag = Offset.Zero
                        },
                        onDragEnd = {
                            val finalTransform = workingTransform
                            latestOnTransformEnd.value?.invoke(finalTransform)
                                ?: latestOnTransformChange(finalTransform)
                        },
                        onDragCancel = {
                            cumulativeDrag = Offset.Zero
                            workingTransform = startTransform
                        },
                    ) { change, amount ->
                        change.consume()
                        cumulativeDrag += amount
                        val proposed = startTransform.copy(
                            scale = PanelEditingPolicy.scaleFromTopLeadingDrag(
                                startScale = startTransform.scale,
                                panelSize = measuredSize,
                                // The handle cancels the dashboard's visual scale so its
                                // pointer delta is in screen pixels. Convert it back to the
                                // canvas coordinate space expected by the pure policy.
                                translation = FloatOffset(
                                    x = cumulativeDrag.x / safeScreenScale,
                                    y = cumulativeDrag.y / safeScreenScale,
                                ),
                            ),
                        )
                        workingTransform = clamp(proposed)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = androidx.compose.foundation.shape.CircleShape,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("↖", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LiveEditorPanelContent(
    key: EditorPanelKey,
    state: StandUiState,
    clockFont: ClockFontChoice,
    clockHourMode: ClockHourMode,
    isPortrait: Boolean,
    contentAlpha: Float,
    selected: Boolean,
    secondsShowsBackground: Boolean,
    catalogNow: LocalDateTime?,
) {
    LiveEditorSelection(selected = selected) {
        when (key) {
            EditorPanelKey.Clock -> FlipClock(
                hourMode = clockHourMode,
                clockFont = clockFont,
                isPortrait = isPortrait,
                scale = 1f,
                contentAlpha = contentAlpha,
                fixedNow = catalogNow,
            )

            EditorPanelKey.Seconds -> ClockSeconds(
                clockFont = clockFont,
                isPortrait = isPortrait,
                contentAlpha = contentAlpha,
                showsBackground = secondsShowsBackground,
                fixedNow = catalogNow,
            )

            EditorPanelKey.Date -> ClockDateAndSeconds(
                hourMode = state.settings.clockHourMode,
                contentAlpha = contentAlpha,
                fixedNow = catalogNow,
            )

            EditorPanelKey.Status -> Text(
                text = "현재 상태 · ${state.experienceMode.title}",
                color = Color.White.copy(alpha = contentAlpha * 0.74f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )

            EditorPanelKey.Battery -> Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 0.dp,
                modifier = Modifier.standPanelSurface(
                    isDimmed = false,
                    cornerRadius = 18.dp,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        batteryIcon(state.batteryLevel, state.isCharging),
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

            EditorPanelKey.Radio,
            EditorPanelKey.SecondaryRadio,
            -> Unit

            is EditorPanelKey.Weather -> Unit
        }
    }
}

@Composable
private fun LiveWeatherPanelContent(
    state: StandUiState,
    pieces: List<WeatherPiece>,
    isPortrait: Boolean,
    isExpanded: Boolean,
    contentAlpha: Float,
    selected: Boolean,
) {
    LiveEditorSelection(selected = selected) {
        WeatherGroupPanel(
            weather = state.weather,
            message = state.weatherMessage,
            pieces = pieces,
            isPortrait = isPortrait,
            isExpanded = isExpanded,
            contentAlpha = contentAlpha,
        )
    }
}

@Composable
private fun LiveEditorSelection(
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    Box {
        content()
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(18.dp),
                    ),
            )
        }
    }
}

@Composable
private fun PanelInspector(
    layout: StandScreenLayout,
    selectedPanel: EditorPanelKey,
    onSelectedPanelChange: (EditorPanelKey) -> Unit,
    onLayoutChange: (StandScreenLayout) -> Unit,
    canvasSize: FloatSize,
    measuredPanelSizes: Map<EditorPanelKey, FloatSize>,
    modifier: Modifier = Modifier,
) {
    val panels = panelKeys(layout)
    val selectedTransform = layout.transformFor(selectedPanel)
    val mergeCandidate = (selectedPanel as? EditorPanelKey.Weather)?.let { weather ->
        bestWeatherMergeCandidate(
            layout = layout,
            sourceGroupId = weather.groupId,
            canvasSize = canvasSize,
            screenScale = 1f,
            measuredPanelSizes = measuredPanelSizes,
        )
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "패널을 끌어 이동 · 두 손가락으로 크기 조절",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                panels.forEach { key ->
                    FilterChip(
                        selected = key == selectedPanel,
                        onClick = { onSelectedPanelChange(key) },
                        label = { Text(key.titleFor(layout)) },
                        leadingIcon = {
                            Icon(
                                key.icon,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                    )
                }
            }

            Text(
                text = "${selectedPanel.titleFor(layout)} 크기 · " +
                    "${(selectedTransform.scale * 100).roundToInt()}%",
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = selectedTransform.scale,
                onValueChange = { scale ->
                    val proposed = selectedTransform.copy(
                        scale = PanelEditingPolicy.clampScale(scale),
                    )
                    onLayoutChange(layout.withPanelTransform(selectedPanel, proposed))
                },
                valueRange = PanelEditingPolicy.MinimumPanelScale..PanelEditingPolicy.MaximumPanelScale,
                modifier = Modifier.semantics {
                    contentDescription = "${selectedPanel.titleFor(layout)} 패널 크기"
                },
            )

            if (selectedPanel is EditorPanelKey.Weather) {
                val pieces = layout.weatherPieces(selectedPanel.groupId)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        enabled = pieces.size > 1,
                        onClick = {
                            val split = WeatherGroupPolicy.splitGroup(
                                layout,
                                selectedPanel.groupId,
                            )
                            onLayoutChange(split)
                            onSelectedPanelChange(
                                EditorPanelKey.Weather(pieces.firstOrNull()?.index ?: 0),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("날씨 분리")
                    }
                    Button(
                        enabled = mergeCandidate != null,
                        onClick = {
                            mergeCandidate?.let {
                                onLayoutChange(it.layout)
                                onSelectedPanelChange(EditorPanelKey.Weather(it.targetGroupId))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("겹친 날씨 결합")
                    }
                }
                Text(
                    text = "날씨 패널을 40% 이상 겹친 뒤 결합할 수 있습니다. " +
                        "결합된 패널은 두 번 눌러도 분리됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EditorClockFontPalette(
    selectedFont: ClockFontChoice,
    isPortrait: Boolean,
    isExpanded: Boolean,
    onFontSelected: (ClockFontChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("editor_clock_font_palette"),
        // This is a chooser, not a live panel: keep previews readable over every
        // dashboard panel without sharp text bleeding through the palette.
        color = Color(0xFF4A2D24),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "시계 숫자 모양을 직접 눌러 선택하세요",
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = when {
                    !isPortrait -> 2
                    isExpanded -> 5
                    else -> 3
                }
                val spacing = if (isPortrait) 7.dp else 5.dp
                // Leave a small rounding margin so FlowRow does not wrap the last item
                // on fractional-density tablets such as the SM-T500.
                val tileWidth = (maxWidth - spacing * (columns - 1)) / columns - 1.dp
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = columns,
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    ClockFontChoice.entries.forEach { font ->
                        val selected = font == selectedFont
                        Surface(
                            onClick = { onFontSelected(font) },
                            modifier = Modifier
                                .width(tileWidth)
                                .height(if (isPortrait) 58.dp else 50.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription =
                                        "${font.displayName} 글꼴, 12시 34분 미리보기"
                                    stateDescription = if (selected) "선택됨" else "선택 안 됨"
                                    role = Role.RadioButton
                                },
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                            } else {
                                Color.White.copy(alpha = 0.07f)
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White.copy(alpha = 0.13f)
                                },
                            ),
                        ) {
                            MiniEditorFontClock(font = font, compact = !isPortrait)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniEditorFontClock(font: ClockFontChoice, compact: Boolean) {
    val fontSize = if (compact) 15.sp else 18.sp
    val density = LocalDensity.current
    val visualFontSize = with(density) { fontSize.toPx() / this.density }
    val verticalOffset = ClockVisualPolicy.verticalOffset(
        font = font,
        fontSize = visualFontSize,
    ).dp
    Row(
        modifier = Modifier.fillMaxSize().padding(
            horizontal = if (compact) 3.dp else 5.dp,
            vertical = if (compact) 5.dp else 7.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniEditorFontCard(
            value = "12",
            font = font,
            fontSize = fontSize,
            verticalOffset = verticalOffset,
            compact = compact,
        )
        Text(
            text = ":",
            color = Color.White.copy(alpha = 0.84f),
            fontFamily = font.fontFamily(),
            fontSize = fontSize,
            fontWeight = font.fontWeight(),
            modifier = Modifier.offset(y = verticalOffset),
        )
        MiniEditorFontCard(
            value = "34",
            font = font,
            fontSize = fontSize,
            verticalOffset = verticalOffset,
            compact = compact,
        )
    }
}

@Composable
private fun MiniEditorFontCard(
    value: String,
    font: ClockFontChoice,
    fontSize: androidx.compose.ui.unit.TextUnit,
    verticalOffset: Dp,
    compact: Boolean,
) {
    val splitGap = 2.dp
    Box(
        modifier = Modifier
            .size(
                width = if (compact) 34.dp else 40.dp,
                height = if (compact) 34.dp else 42.dp,
            )
            .standPanelSurface(
                isDimmed = false,
                cornerRadius = 8.dp,
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
                text = value,
                color = Color.White,
                fontFamily = font.fontFamily(),
                fontSize = fontSize,
                fontWeight = font.fontWeight(),
                maxLines = 1,
                modifier = Modifier.offset(y = verticalOffset),
            )
        }
    }
}

@Composable
private fun ControlOrderEditor(
    state: StandUiState,
    order: List<StandControlKind>,
    isPortrait: Boolean,
    isExpanded: Boolean,
    onOrderChange: (List<StandControlKind>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingKind by remember { mutableStateOf<StandControlKind?>(null) }
    val tileCenters = remember { mutableStateMapOf<StandControlKind, Offset>() }
    val scrollState = rememberScrollState()
    val contentModifier = if (isExpanded) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxSize()
            .verticalScroll(
                state = scrollState,
                enabled = draggingKind == null,
            )
    }

    Column(
        modifier = modifier.then(contentModifier).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
            if (isExpanded) {
                Spacer(Modifier.weight(0.8f))
            } else {
                Spacer(Modifier.height(if (isPortrait) 108.dp else 96.dp))
            }
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = Color(0xFFFF8A2A),
                modifier = Modifier.size(28.dp),
            )
            Text(
                "버튼을 길게 눌러 원하는 자리로 옮기세요",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "이 순서는 홈 화면의 하단 기능 버튼에 적용됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.64f),
                textAlign = TextAlign.Center,
            )
            if (isExpanded) {
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.height(24.dp))
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val spacing = 7.dp
                val tileWidth = 98.dp
                val columns = (
                    (maxWidth.value + spacing.value) /
                        (tileWidth.value + spacing.value)
                ).toInt().coerceIn(1, 7)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "하단 버튼 순서 편집"
                    },
                    horizontalArrangement = Arrangement.spacedBy(
                        spacing,
                        Alignment.CenterHorizontally,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    order.forEachIndexed { index, kind ->
                        val controlWidth = if (kind == StandControlKind.STOP_DETECTION) {
                            tileWidth * 2 + spacing
                        } else {
                            tileWidth
                        }
                        ReorderableControlTile(
                            state = state,
                            kind = kind,
                            presentation = kind.presentation(state),
                            index = index,
                            itemCount = order.size,
                            columns = columns,
                            width = controlWidth,
                            dragImmediately = isExpanded,
                            onPositioned = { center -> tileCenters[kind] = center },
                            resolveTarget = { dragOffset ->
                                nearestControlIndex(
                                    order = order,
                                    centers = tileCenters,
                                    movingKind = kind,
                                    dragOffset = dragOffset,
                                )
                            },
                            onMove = { from, to ->
                                onOrderChange(order.moved(from, to))
                            },
                            onDragStateChange = { isDragging ->
                                draggingKind = when {
                                    isDragging -> kind
                                    draggingKind == kind -> null
                                    else -> draggingKind
                                }
                            },
                        )
                    }
                }
            }
    }
}

@Composable
private fun ReorderableControlTile(
    state: StandUiState,
    kind: StandControlKind,
    presentation: StandControlPresentation,
    index: Int,
    itemCount: Int,
    columns: Int,
    width: Dp,
    dragImmediately: Boolean,
    onPositioned: (Offset) -> Unit,
    resolveTarget: (Offset) -> Int?,
    onMove: (from: Int, to: Int) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
) {
    var dragging by remember(kind) { mutableStateOf(false) }
    var dragOffset by remember(kind) { mutableStateOf(Offset.Zero) }
    val currentIndex by rememberUpdatedState(index)
    val currentItemCount by rememberUpdatedState(itemCount)
    val currentResolveTarget by rememberUpdatedState(resolveTarget)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragStateChange by rememberUpdatedState(onDragStateChange)
    val rawDragSession = remember(kind) { RawControlDragSession() }
    val density = LocalDensity.current
    val horizontalStep = with(density) { (width + 7.dp).toPx() }
    val verticalStep = with(density) { 73.dp.toPx() }
    val horizontalThreshold = horizontalStep * 0.45f
    val verticalThreshold = verticalStep * 0.55f
    val accessibilityActions = buildList {
        if (index > 0) {
            add(
                CustomAccessibilityAction("앞으로 이동") {
                    onMove(index, index - 1)
                    true
                },
            )
        }
        if (index < itemCount - 1) {
            add(
                CustomAccessibilityAction("뒤로 이동") {
                    onMove(index, index + 1)
                    true
                },
            )
        }
    }
    val beginDrag: () -> Unit = {
        dragging = true
        currentOnDragStateChange(true)
    }
    val finishDrag: () -> Unit = {
        dragging = false
        dragOffset = Offset.Zero
        currentOnDragStateChange(false)
    }
    val commitDrag: (RawControlDragSnapshot) -> Unit = { completedDrag ->
        val horizontalDelta = dragIndexDelta(
            distance = completedDrag.offset.x,
            threshold = horizontalThreshold,
            step = horizontalStep,
        )
        val verticalDelta = dragIndexDelta(
            distance = completedDrag.offset.y,
            threshold = verticalThreshold,
            step = verticalStep,
        ) * columns
        val indexDelta = horizontalDelta + verticalDelta
        val target = currentResolveTarget(completedDrag.offset)
            ?: (completedDrag.startIndex + indexDelta)
                .coerceIn(0, currentItemCount - 1)
        if (target != completedDrag.startIndex) {
            currentOnMove(completedDrag.startIndex, target)
        }
    }
    val reorderGestureModifier = if (dragImmediately) {
        Modifier.pointerInteropFilter { event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    rawDragSession.begin(event.rawX, event.rawY, currentIndex)
                    beginDrag()
                }

                MotionEvent.ACTION_MOVE -> {
                    rawDragSession.snapshot(event.rawX, event.rawY)?.let { snapshot ->
                        dragOffset = snapshot.offset
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val snapshot = rawDragSession.snapshot(event.rawX, event.rawY)
                    rawDragSession.reset()
                    finishDrag()
                    snapshot?.let(commitDrag)
                }

                MotionEvent.ACTION_CANCEL -> {
                    rawDragSession.reset()
                    finishDrag()
                }
            }
            true
        }
    } else {
        Modifier.pointerInput(kind, columns, width) {
            var startIndex = 0
            var cumulativeOffset = Offset.Zero
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    startIndex = currentIndex
                    cumulativeOffset = Offset.Zero
                    beginDrag()
                },
                onDragCancel = {
                    cumulativeOffset = Offset.Zero
                    finishDrag()
                },
                onDragEnd = {
                    val completedDrag = RawControlDragSnapshot(
                        startIndex = startIndex,
                        offset = cumulativeOffset,
                    )
                    cumulativeOffset = Offset.Zero
                    finishDrag()
                    commitDrag(completedDrag)
                },
            ) { change, amount ->
                change.consume()
                cumulativeOffset += amount
                dragOffset = cumulativeOffset
            }
        }
    }

    Surface(
        modifier = Modifier
            .width(width)
            .height(66.dp)
            .zIndex(if (dragging) 4f else 1f)
            .onGloballyPositioned { coordinates ->
                if (!dragging) {
                    val position = coordinates.positionInRoot()
                    onPositioned(
                        Offset(
                            x = position.x + coordinates.size.width / 2f,
                            y = position.y + coordinates.size.height / 2f,
                        ),
                    )
                }
            }
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                alpha = if (dragging) 0.84f else 1f
            }
            .standPanelSurface(
                isDimmed = false,
                cornerRadius = 14.dp,
                splitGap = 2.dp,
            )
            .semantics {
                contentDescription = "${presentation.title}, 순서 변경"
                stateDescription = "${index + 1}번째"
                role = Role.Button
                customActions = accessibilityActions
            }
            .then(reorderGestureModifier),
        color = Color.Transparent,
        contentColor = Color.White.copy(alpha = 0.82f),
        shape = RoundedCornerShape(14.dp),
        border = if (dragging) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (dragging) 7.dp else 0.dp,
    ) {
        if (kind == StandControlKind.STOP_DETECTION) {
            AutomaticRecordingControlContent(
                state = state,
                showReorderHandle = true,
            )
        } else {
            StandControlTileContent(
                presentation = presentation,
                showReorderHandle = true,
            )
        }
    }
}

private class RawControlDragSession {
    private var active = false
    private var startRawX = 0f
    private var startRawY = 0f
    private var startIndex = 0

    fun begin(rawX: Float, rawY: Float, index: Int) {
        active = true
        startRawX = rawX
        startRawY = rawY
        startIndex = index
    }

    fun snapshot(rawX: Float, rawY: Float): RawControlDragSnapshot? {
        if (!active) return null
        return RawControlDragSnapshot(
            startIndex = startIndex,
            offset = Offset(rawX - startRawX, rawY - startRawY),
        )
    }

    fun reset() {
        active = false
    }
}

private data class RawControlDragSnapshot(
    val startIndex: Int,
    val offset: Offset,
)

private class PanelTransformGestureSession {
    private var changed = false

    fun markChanged() {
        changed = true
    }

    fun consumeChanged(): Boolean {
        val result = changed
        changed = false
        return result
    }

}

private fun dragIndexDelta(distance: Float, threshold: Float, step: Float): Int {
    if (abs(distance) < threshold || step <= 0f) return 0
    return (distance / step).roundToInt().takeIf { it != 0 } ?: distance.sign.toInt()
}

private fun nearestControlIndex(
    order: List<StandControlKind>,
    centers: Map<StandControlKind, Offset>,
    movingKind: StandControlKind,
    dragOffset: Offset,
): Int? {
    val startCenter = centers[movingKind] ?: return null
    val releasePoint = startCenter + dragOffset
    return order.indices
        .filter { centers[order[it]] != null }
        .minByOrNull { index ->
            val center = checkNotNull(centers[order[index]])
            val deltaX = center.x - releasePoint.x
            val deltaY = center.y - releasePoint.y
            deltaX * deltaX + deltaY * deltaY
        }
}

private sealed interface EditorPanelKey {
    data object Clock : EditorPanelKey
    data object Seconds : EditorPanelKey
    data class Weather(val groupId: Int) : EditorPanelKey
    data object Date : EditorPanelKey
    data object Status : EditorPanelKey
    data object Battery : EditorPanelKey
    data object Radio : EditorPanelKey
    data object SecondaryRadio : EditorPanelKey
}

private val EditorPanelKeySaver = Saver<EditorPanelKey, String>(
    save = { key ->
        when (key) {
            EditorPanelKey.Clock -> "clock"
            EditorPanelKey.Seconds -> "seconds"
            is EditorPanelKey.Weather -> "weather:${key.groupId}"
            EditorPanelKey.Date -> "date"
            EditorPanelKey.Status -> "status"
            EditorPanelKey.Battery -> "battery"
            EditorPanelKey.Radio -> "radio"
            EditorPanelKey.SecondaryRadio -> "secondaryRadio"
        }
    },
    restore = { saved ->
        when {
            saved == "clock" -> EditorPanelKey.Clock
            saved == "seconds" -> EditorPanelKey.Seconds
            saved == "date" -> EditorPanelKey.Date
            saved == "status" -> EditorPanelKey.Status
            saved == "battery" -> EditorPanelKey.Battery
            saved == "radio" -> EditorPanelKey.Radio
            saved == "secondaryRadio" -> EditorPanelKey.SecondaryRadio
            saved.startsWith("weather:") -> saved.substringAfter(':').toIntOrNull()
                ?.let { groupId -> EditorPanelKey.Weather(groupId) }
            else -> null
        }
    },
)

private data class WeatherMergeCandidate(
    val targetGroupId: Int,
    val layout: StandScreenLayout,
)

private val EditorPanelKey.title: String
    get() = when (this) {
        EditorPanelKey.Clock -> "시계"
        EditorPanelKey.Seconds -> "초"
        is EditorPanelKey.Weather -> "날씨"
        EditorPanelKey.Date -> "날짜"
        EditorPanelKey.Status -> "상태"
        EditorPanelKey.Battery -> "배터리"
        EditorPanelKey.Radio -> "라디오"
        EditorPanelKey.SecondaryRadio -> "두 번째 라디오"
    }

private val EditorPanelKey.testTag: String
    get() = when (this) {
        EditorPanelKey.Clock -> "editor_panel_clock"
        EditorPanelKey.Seconds -> "editor_panel_seconds"
        is EditorPanelKey.Weather -> "editor_panel_weather_$groupId"
        EditorPanelKey.Date -> "editor_panel_date"
        EditorPanelKey.Status -> "editor_panel_status"
        EditorPanelKey.Battery -> "editor_panel_battery"
        EditorPanelKey.Radio -> "editor_panel_radio"
        EditorPanelKey.SecondaryRadio -> "editor_panel_secondary_radio"
    }

private val EditorPanelKey.icon: ImageVector
    get() = when (this) {
        EditorPanelKey.Clock -> Icons.Default.Schedule
        EditorPanelKey.Seconds -> Icons.Default.Schedule
        is EditorPanelKey.Weather -> Icons.Default.Cloud
        EditorPanelKey.Date -> Icons.Default.CalendarMonth
        EditorPanelKey.Status -> Icons.Default.Info
        EditorPanelKey.Battery -> Icons.Default.BatteryChargingFull
        EditorPanelKey.Radio -> Icons.Default.GraphicEq
        EditorPanelKey.SecondaryRadio -> Icons.Default.GraphicEq
    }

private fun EditorPanelKey.titleFor(layout: StandScreenLayout): String = when (this) {
    is EditorPanelKey.Weather -> {
        val pieces = layout.weatherPieces(groupId)
        if (pieces.size == WeatherPiece.entries.size) {
            "날씨 전체"
        } else {
            pieces.joinToString("·") { piece ->
                when (piece) {
                    WeatherPiece.ICON -> "아이콘"
                    WeatherPiece.TEMPERATURE -> "온도"
                    WeatherPiece.CONDITION -> "상태"
                }
            }
        }
    }
    else -> title
}

private fun panelKeys(
    layout: StandScreenLayout,
    radioCount: Int = 1,
): List<EditorPanelKey> = buildList {
    add(EditorPanelKey.Clock)
    add(EditorPanelKey.Seconds)
    layout.weatherGroupIds.distinct().sorted().forEach { add(EditorPanelKey.Weather(it)) }
    add(EditorPanelKey.Date)
    add(EditorPanelKey.Battery)
    add(EditorPanelKey.Radio)
    if (radioCount >= 1 && !layout.radiosGrouped) add(EditorPanelKey.SecondaryRadio)
}

private fun StandScreenLayout.weatherPieces(groupId: Int): List<WeatherPiece> =
    WeatherPiece.entries.filter { weatherGroupId(it) == groupId }

private fun StandScreenLayout.transformFor(key: EditorPanelKey): PanelTransform = when (key) {
    EditorPanelKey.Clock -> clock
    EditorPanelKey.Seconds -> seconds
    is EditorPanelKey.Weather -> {
        val first = weatherPieces(key.groupId).firstOrNull() ?: WeatherPiece.ICON
        weatherTransform(first)
    }
    EditorPanelKey.Date -> date
    EditorPanelKey.Status -> status
    EditorPanelKey.Battery -> battery
    EditorPanelKey.Radio -> radio
    EditorPanelKey.SecondaryRadio -> secondaryRadio
}

private fun StandScreenLayout.withPanelTransform(
    key: EditorPanelKey,
    transform: PanelTransform,
): StandScreenLayout = when (key) {
    EditorPanelKey.Clock -> copy(clock = transform)
    EditorPanelKey.Seconds -> copy(seconds = transform)
    is EditorPanelKey.Weather -> {
        var result = this
        weatherPieces(key.groupId).forEach { piece ->
            result = result.withWeatherTransform(piece, transform)
        }
        result
    }
    EditorPanelKey.Date -> copy(date = transform)
    EditorPanelKey.Status -> copy(status = transform)
    EditorPanelKey.Battery -> copy(battery = transform)
    EditorPanelKey.Radio -> copy(radio = transform)
    EditorPanelKey.SecondaryRadio -> copy(secondaryRadio = transform)
}

private fun bestWeatherMergeCandidate(
    layout: StandScreenLayout,
    sourceGroupId: Int,
    canvasSize: FloatSize,
    screenScale: Float,
    measuredPanelSizes: Map<EditorPanelKey, FloatSize>,
): WeatherMergeCandidate? {
    val sourceBounds = weatherBounds(
        layout,
        sourceGroupId,
        canvasSize,
        screenScale,
        measuredPanelSizes,
    ) ?: return null
    val candidates = layout.weatherGroupIds.distinct().filter { it != sourceGroupId }
    val best = candidates.mapNotNull { targetGroupId ->
        val targetBounds = weatherBounds(
            layout,
            targetGroupId,
            canvasSize,
            screenScale,
            measuredPanelSizes,
        ) ?: return@mapNotNull null
        Triple(
            targetGroupId,
            targetBounds,
            PanelEditingPolicy.overlapFraction(sourceBounds, targetBounds),
        )
    }.maxByOrNull { it.third } ?: return null
    if (best.third < PanelEditingPolicy.WeatherMergeOverlapThreshold) return null

    val merged = WeatherGroupPolicy.mergeIfOverlapping(
        layout = layout,
        sourceGroupId = sourceGroupId,
        targetGroupId = best.first,
        sourceBounds = sourceBounds,
        targetBounds = best.second,
    )
    return WeatherMergeCandidate(targetGroupId = best.first, layout = merged)
}

private fun weatherBounds(
    layout: StandScreenLayout,
    groupId: Int,
    canvasSize: FloatSize,
    screenScale: Float,
    measuredPanelSizes: Map<EditorPanelKey, FloatSize>,
): FloatRect? {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) return null
    val key = EditorPanelKey.Weather(groupId)
    val measured = measuredPanelSizes[key] ?: return null
    return PanelEditingPolicy.transformedBounds(
        transform = layout.transformFor(key),
        panelSize = measured,
        canvasSize = canvasSize,
        screenScale = screenScale,
    )
}

private fun List<StandControlKind>.moved(from: Int, to: Int): List<StandControlKind> {
    if (from !in indices || to !in indices || from == to) return this
    val result = toMutableList()
    val item = result.removeAt(from)
    result.add(to, item)
    return StandControlKind.normalizedOrder(result)
}
