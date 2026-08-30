@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.armsone.stand.ui

import androidx.annotation.RawRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.armsone.stand.BuildConfig
import com.armsone.stand.R
import com.armsone.stand.model.AppSettings
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockVisualPolicy
import com.armsone.stand.model.ExternalMusicService
import com.armsone.stand.model.HomeMusicChannelKind
import com.armsone.stand.model.HomeMusicChannelPolicy
import com.armsone.stand.model.HomeMusicChannelSelection
import com.armsone.stand.model.SettingsInformationArchitecture
import com.armsone.stand.model.SettingsSectionKind
import com.armsone.stand.model.StandDisplayTheme
import com.armsone.stand.model.StandModePreference
import com.armsone.stand.model.TvUiModePolicy
import com.armsone.stand.platform.AmbientCameraState
import com.armsone.stand.platform.InternetRadioState
import com.armsone.stand.ui.components.flipTextSplitMask
import com.armsone.stand.ui.components.settingsFocusable
import com.armsone.stand.ui.components.standFocusable
import com.armsone.stand.ui.components.standPanelSurface
import com.armsone.stand.ui.theme.fontFamily
import com.armsone.stand.ui.theme.fontWeight
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private fun musicSelectionTitle(
    selection: HomeMusicChannelSelection,
    settings: AppSettings,
): String = when (selection.kind) {
    HomeMusicChannelKind.SPOTIFY -> ExternalMusicService.SPOTIFY.displayName
    HomeMusicChannelKind.YOUTUBE_MUSIC -> ExternalMusicService.YOUTUBE_MUSIC.displayName
    HomeMusicChannelKind.INTERNET_RADIO -> settings.internetRadioChannels
        .firstOrNull { it.id == selection.radioID }
        ?.displayName
        ?: "인터넷 라디오"
}

@Composable
fun SettingsScreen(
    state: StandUiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onModePreferenceSelected: (StandModePreference) -> Unit,
    onRestoreRecommended: () -> Unit,
    onToggleInternetRadio: (String) -> Unit,
    onOpenExternalMusic: (ExternalMusicService) -> Unit = {},
    onEndExternalMusic: () -> Unit = {},
    onAssignHomeMusicChannel: (Int, HomeMusicChannelSelection) -> Unit = { _, _ -> },
    onMoveHomeMusicChannel: (Int, Int) -> Unit = { _, _ -> },
    onSaveInternetRadio: (String?, String, String) -> String?,
    onDeleteInternetRadio: (String) -> Unit,
    onManageInternetRadios: () -> Unit,
    onOpenInternetRadioBrowser: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenBoyiso: () -> Unit,
    onOpenClockFonts: () -> Unit,
    onOpenFontLicenses: () -> Unit,
    boyisoStatus: String,
    onRequestMicrophonePermission: () -> Unit,
    onRequestApproximateLocationPermission: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onCameraAmbientSensingChanged: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
    automaticUpdateDownloadEnabled: Boolean,
    onAutomaticUpdateDownloadChanged: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var showResetConfirmation by remember { mutableStateOf(false) }
    var pendingRadioDeletionID by remember { mutableStateOf<String?>(null) }
    var editingRadioID by remember { mutableStateOf<String?>(null) }
    var addingRadio by remember { mutableStateOf(false) }
    var radioDraftName by remember { mutableStateOf("") }
    var radioDraftURL by remember { mutableStateOf("") }
    var radioValidationMessage by remember { mutableStateOf<String?>(null) }
    val settings = state.settings
    val settingsBase = Color(red = 0.115f, green = 0.085f, blue = 0.078f)
    val settingsBackground = Brush.linearGradient(
        listOf(
            lerp(settingsBase, MaterialTheme.colorScheme.primary, 0.20f),
            lerp(settingsBase, MaterialTheme.colorScheme.primary, 0.24f),
            settingsBase,
        ),
    )
    val configuration = LocalConfiguration.current
    val isTelevision = TvUiModePolicy.isTelevision(configuration)
    val settingsGridState = rememberLazyStaggeredGridState()
    val settingsScope = rememberCoroutineScope()
    val missingPermissionCount = listOf(
        !isTelevision && settings.soundSensingEnabled && !state.hasMicrophonePermission,
        settings.weatherLocationEnabled && !state.hasApproximateLocationPermission,
        !isTelevision && settings.cameraAmbientSensingEnabled && !state.hasCameraPermission,
    ).count { it }
    val visibleSections = remember(isTelevision) {
        TvUiModePolicy.allowedSettingsSections(isTelevision)
    }
    val cameraAmbientDetail = when {
        isTelevision -> "TV에서는 지원하지 않음"
        state.ambientCameraState == AmbientCameraState.DISABLED -> "필요할 때 약 1초 동안 밝기만 계산"
        state.ambientCameraState == AmbientCameraState.PERMISSION_NEEDED -> "카메라 권한 필요"
        state.ambientCameraState == AmbientCameraState.DENIED -> "카메라 권한이 거부됨"
        state.ambientCameraState == AmbientCameraState.MEASURING -> "평균 밝기 확인 중"
        state.ambientCameraState == AmbientCameraState.READY -> state.ambientCameraBrightness?.let { value ->
            "최근 측정 ${(value * 100).roundToInt()}%"
        } ?: "측정 준비됨"
        else -> "카메라를 사용할 수 없음"
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(settingsBackground),
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets.safeDrawing,
                title = {
                    Text(
                        "설정",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                },
                actions = {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.settingsFocusable(
                        isTelevision = isTelevision,
                        shape = RoundedCornerShape(12.dp),
                    ),
                ) {
                    Text("완료", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
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
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columnCount),
                state = settingsGridState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (maxWidth >= 720.dp) 24.dp else 14.dp),
                contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalItemSpacing = 14.dp,
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    SettingsHero(
                        state = state,
                        onModePreferenceSelected = onModePreferenceSelected,
                        isTelevision = isTelevision,
                    )
                }

                item(span = StaggeredGridItemSpan.FullLine) {
                    InternetRadioSettingsShortcut(
                        onClick = {
                            settingsScope.launch {
                                val musicIndex = visibleSections
                                    .indexOf(SettingsSectionKind.MUSIC)
                                if (musicIndex >= 0) {
                                    settingsGridState.animateScrollToItem(2 + musicIndex)
                                }
                            }
                        },
                        isTelevision = isTelevision,
                    )
                }

                items(
                    count = visibleSections.size,
                    key = { index -> visibleSections[index].name },
                    span = { index ->
                        if (visibleSections[index] == SettingsSectionKind.MUSIC) {
                            StaggeredGridItemSpan.FullLine
                        } else {
                            StaggeredGridItemSpan.SingleLane
                        }
                    },
                ) { index ->
                    when (val section = visibleSections[index]) {
                    SettingsSectionKind.MUSIC -> SettingsCard(
                        title = "음악",
                        subtitle = state.externalMusicService?.let { "${it.displayName} 음악 듣기 모드" }
                            ?: "${settings.homeMusicChannels.size}개 채널 · 한곳에서 바로 전환",
                        icon = Icons.Default.MusicNote,
                    ) {
                        val hapticFeedback = LocalHapticFeedback.current
                        val density = LocalDensity.current
                        val spacingPx = with(density) { 14.dp.toPx() }
                        val channels = settings.homeMusicChannels
                        var draggedSlot by remember { mutableStateOf<Int?>(null) }
                        var dragOffsetY by remember { mutableFloatStateOf(0f) }
                        val itemHeights = remember { mutableStateMapOf<Int, Float>() }

                        val slotHeightsList = remember(channels.size, itemHeights.toMap()) {
                            (0 until channels.size).map { itemHeights[it] ?: with(density) { 58.dp.toPx() } }
                        }
                        val slotCenters = remember(slotHeightsList, spacingPx) {
                            HomeMusicChannelPolicy.calculateSlotCenters(slotHeightsList, spacingPx)
                        }
                        val targetSlot = remember(draggedSlot, dragOffsetY, slotCenters) {
                            draggedSlot?.let { from ->
                                HomeMusicChannelPolicy.calculateTargetIndex(from, dragOffsetY, slotCenters)
                            }
                        }
                        val currentTargetSlot by rememberUpdatedState(targetSlot)

                        var previousTargetSlot by remember { mutableStateOf<Int?>(null) }
                        LaunchedEffect(targetSlot) {
                            if (targetSlot != null && targetSlot != previousTargetSlot && previousTargetSlot != null) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            previousTargetSlot = targetSlot
                        }

                        val performMove: (Int, Int) -> Unit = { from, to ->
                            if (from in channels.indices && to in channels.indices && from != to) {
                                onMoveHomeMusicChannel(from, to)
                            }
                        }

                        Text(
                            "홈 음악 채널 순서",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        channels.forEachIndexed { slot, selection ->
                            key(selection.stableID) {
                                val isDragging = (draggedSlot == slot)
                                val channel = selection.radioID?.let { radioID ->
                                    settings.internetRadioChannels.firstOrNull { it.id == radioID }
                                }
                                val service = when (selection.kind) {
                                    HomeMusicChannelKind.SPOTIFY -> ExternalMusicService.SPOTIFY
                                    HomeMusicChannelKind.YOUTUBE_MUSIC -> ExternalMusicService.YOUTUBE_MUSIC
                                    HomeMusicChannelKind.INTERNET_RADIO -> null
                                }
                                val radioActive = channel != null && when (val radioState = state.internetRadioState) {
                                    is InternetRadioState.Loading -> radioState.channelID == channel.id
                                    is InternetRadioState.Playing -> radioState.channelID == channel.id
                                    is InternetRadioState.Reconnecting -> radioState.channelID == channel.id
                                    else -> false
                                }
                                val serviceActive = service != null && state.externalMusicService == service
                                val active = radioActive || serviceActive
                                val status = when {
                                    service != null && serviceActive -> "음악 듣기 모드 · 앱 다시 열기"
                                    service != null -> "로그인하고 음악 앱 열기"
                                    channel == null -> "등록을 기다림"
                                    state.internetRadioState is InternetRadioState.Loading && radioActive -> "연결 중"
                                    state.internetRadioState is InternetRadioState.Playing && radioActive -> "재생 중"
                                    state.internetRadioState is InternetRadioState.Reconnecting && radioActive ->
                                        "${state.internetRadioState.delaySeconds}초 뒤 재연결"
                                    state.internetRadioState is InternetRadioState.Failed -> state.internetRadioState.message
                                    else -> "대기 중"
                                }

                                val displacementY = HomeMusicChannelPolicy.calculateItemDisplacement(
                                    itemIndex = slot,
                                    draggedIndex = draggedSlot,
                                    targetIndex = targetSlot,
                                    draggedItemHeightPx = draggedSlot?.let { slotHeightsList.getOrElse(it) { with(density) { 58.dp.toPx() } } } ?: 0f,
                                    spacingPx = spacingPx,
                                )
                                val animatedDisplacementY by animateFloatAsState(
                                    targetValue = displacementY,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                    label = "musicCardDisplacement_$slot",
                                )
                                val cardElevation by animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 0.dp,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "musicCardElevation_$slot",
                                )
                                val cardScale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.025f else 1.0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "musicCardScale_$slot",
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coordinates ->
                                            itemHeights[slot] = coordinates.size.height.toFloat()
                                        }
                                        .zIndex(if (isDragging) 10f else 1f)
                                        .graphicsLayer {
                                            translationY = if (isDragging) dragOffsetY else animatedDisplacementY
                                            scaleX = cardScale
                                            scaleY = cardScale
                                            shadowElevation = cardElevation.toPx()
                                            shape = RoundedCornerShape(13.dp)
                                            clip = false
                                        },
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 58.dp)
                                            .then(
                                                if (isTelevision) {
                                                    Modifier
                                                } else {
                                                    Modifier.pointerInput(slot, channels.size) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                draggedSlot = slot
                                                                dragOffsetY = 0f
                                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                dragOffsetY += dragAmount.y
                                                            },
                                                            onDragEnd = {
                                                                val from = draggedSlot
                                                                val to = currentTargetSlot
                                                                if (from != null && to != null && from != to) {
                                                                    performMove(from, to)
                                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                }
                                                                draggedSlot = null
                                                                dragOffsetY = 0f
                                                            },
                                                            onDragCancel = {
                                                                draggedSlot = null
                                                                dragOffsetY = 0f
                                                            },
                                                        )
                                                    }
                                                },
                                            )
                                            .semantics {
                                                customActions = buildList {
                                                    if (slot > 0) {
                                                        add(
                                                            CustomAccessibilityAction("위로 이동") {
                                                                performMove(slot, slot - 1)
                                                                true
                                                            },
                                                        )
                                                    }
                                                    if (slot < channels.lastIndex) {
                                                        add(
                                                            CustomAccessibilityAction("아래로 이동") {
                                                                performMove(slot, slot + 1)
                                                                true
                                                            },
                                                        )
                                                    }
                                                }
                                            },
                                        color = when {
                                            isDragging -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                            active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else -> Color.White.copy(alpha = 0.05f)
                                        },
                                        shape = RoundedCornerShape(13.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            when {
                                                isDragging -> MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
                                                active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                                else -> Color.White.copy(alpha = 0.06f)
                                            },
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    when {
                                                        service != null -> onOpenExternalMusic(service)
                                                        channel != null -> onToggleInternetRadio(channel.id)
                                                        else -> {
                                                            editingRadioID = null
                                                            addingRadio = true
                                                            radioDraftName = ""
                                                            radioDraftURL = ""
                                                            radioValidationMessage = null
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .settingsFocusable(
                                                        isTelevision = isTelevision,
                                                        shape = CircleShape,
                                                    ),
                                            ) {
                                                Icon(
                                                    when {
                                                        radioActive -> Icons.Default.PauseCircle
                                                        service == ExternalMusicService.SPOTIFY -> Icons.Default.MusicNote
                                                        else -> Icons.Default.PlayArrow
                                                    },
                                                    contentDescription = when {
                                                        service != null -> "${service.displayName} 열기"
                                                        channel != null -> "${channel.displayName} 재생"
                                                        else -> "인터넷 라디오 등록"
                                                    },
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(musicSelectionTitle(selection, settings), fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    status,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.54f),
                                                    maxLines = 1,
                                                )
                                            }
                                            if (channel != null) {
                                                IconButton(
                                                    onClick = {
                                                        editingRadioID = channel.id
                                                        addingRadio = false
                                                        radioDraftName = channel.displayName
                                                        radioDraftURL = channel.streamUrl
                                                        radioValidationMessage = null
                                                    },
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .background(Color.White.copy(alpha = 0.07f), CircleShape)
                                                        .settingsFocusable(
                                                            isTelevision = isTelevision,
                                                            shape = CircleShape,
                                                        ),
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "${channel.displayName} 수정",
                                                        modifier = Modifier.size(13.dp),
                                                    )
                                                }
                                            }
                                            if (isTelevision) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    IconButton(
                                                        onClick = { performMove(slot, slot - 1) },
                                                        enabled = slot > 0,
                                                        modifier = Modifier.settingsFocusable(
                                                            isTelevision = isTelevision,
                                                            shape = CircleShape,
                                                        ),
                                                    ) {
                                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "위로 이동")
                                                    }
                                                    IconButton(
                                                        onClick = { performMove(slot, slot + 1) },
                                                        enabled = slot < channels.lastIndex,
                                                        modifier = Modifier.settingsFocusable(
                                                            isTelevision = isTelevision,
                                                            shape = CircleShape,
                                                        ),
                                                    ) {
                                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "아래로 이동")
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .pointerInput(slot, channels.size) {
                                                            detectVerticalDragGestures(
                                                                onDragStart = {
                                                                    draggedSlot = slot
                                                                    dragOffsetY = 0f
                                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                },
                                                                onVerticalDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    dragOffsetY += dragAmount
                                                                },
                                                                onDragEnd = {
                                                                    val from = draggedSlot
                                                                    val to = currentTargetSlot
                                                                    if (from != null && to != null && from != to) {
                                                                        performMove(from, to)
                                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    }
                                                                    draggedSlot = null
                                                                    dragOffsetY = 0f
                                                                },
                                                                onDragCancel = {
                                                                    draggedSlot = null
                                                                    dragOffsetY = 0f
                                                                },
                                                            )
                                                        }
                                                        .semantics {
                                                            contentDescription = "${musicSelectionTitle(selection, settings)} 순서 변경"
                                                            role = Role.Button
                                                            customActions = buildList {
                                                                if (slot > 0) {
                                                                    add(
                                                                        CustomAccessibilityAction("위로 이동") {
                                                                            performMove(slot, slot - 1)
                                                                            true
                                                                        },
                                                                    )
                                                                }
                                                                if (slot < channels.lastIndex) {
                                                                    add(
                                                                        CustomAccessibilityAction("아래로 이동") {
                                                                            performMove(slot, slot + 1)
                                                                            true
                                                                        },
                                                                    )
                                                                }
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        Icons.Default.DragHandle,
                                                        contentDescription = null,
                                                        tint = if (isDragging) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.38f),
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (channel != null && editingRadioID == channel.id) {
                                        InlineRadioEditor(
                                            name = radioDraftName,
                                            url = radioDraftURL,
                                            error = radioValidationMessage,
                                            isTelevision = isTelevision,
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
                                            onOpenBrowser = onOpenInternetRadioBrowser,
                                            onClose = { editingRadioID = null },
                                        )
                                    }
                                }
                            }
                        }
                        if (addingRadio) {
                            InlineRadioEditor(
                                name = radioDraftName,
                                url = radioDraftURL,
                                error = radioValidationMessage,
                                isTelevision = isTelevision,
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
                                onOpenBrowser = onOpenInternetRadioBrowser,
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .settingsFocusable(
                                        isTelevision = isTelevision,
                                        shape = RoundedCornerShape(12.dp),
                                    ),
                            ) { Text(if (settings.internetRadioChannels.isEmpty()) "첫 채널 추가" else "채널 추가") }
                        }
                        Text(
                            "길게 눌러 홈 순서를 바꾸고, 라디오의 연필을 누르면 같은 자리에서 바로 수정할 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onManageInternetRadios,
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingsFocusable(
                                    isTelevision = isTelevision,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Icon(Icons.Default.Radio, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("라디오 가져오기·내보내기")
                        }
                        Text(
                            state.externalMusicMessage
                                ?: if (isTelevision) {
                                    "Spotify와 YouTube Music에서 로그인·재생한 뒤 S.tand로 돌아오면 음악 듣기 모드를 유지합니다."
                                } else {
                                    "Spotify와 YouTube Music에서 로그인·재생한 뒤 S.tand로 돌아오면 음악 듣기 모드를 유지합니다. 재생 중에는 잠꼬대·코골이 감지와 녹음을 잠시 멈춥니다."
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SettingsSectionKind.SCREEN_AND_CLOCK -> SettingsCard(
                        title = "화면과 시계",
                        subtitle = "테마와 시계 글꼴을 바꿉니다",
                        icon = Icons.Default.TextFields,
                    ) {
                        Text(
                            "테마",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "시계를 더블 터치하면 테마가 바뀝니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ThemePalettePicker(
                            selectedTheme = settings.displayTheme,
                            onThemeSelected = { theme ->
                                onUpdate { it.copy(displayTheme = theme) }
                            },
                            isTelevision = isTelevision,
                        )
                        TextButton(
                            onClick = onOpenClockFonts,
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingsFocusable(
                                    isTelevision = isTelevision,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null)
                            Text(" 시계 글꼴", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            Text(
                                settings.clockFont.displayName,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                        Text(
                            if (isTelevision) {
                                "TV에서는 홈의 테마·밝기·시계 크기 버튼을 리모컨으로 조절할 수 있습니다. 패널 위치 편집은 터치 기기에서 지원합니다."
                            } else {
                                "홈 화면을 길게 누르면 시계와 날씨 같은 정보 패널을 편집할 수 있습니다."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SettingsSectionKind.PERMISSIONS -> SettingsCard(
                        title = "권한 설정",
                        subtitle = "필요한 기능만 선택해서 사용합니다",
                        icon = Icons.Default.Security,
                    ) {
                        if (!isTelevision) {
                            Text(
                                "백그라운드 모드",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                listOf(false to "꺼짐", true to "켜짐").forEachIndexed { index, option ->
                                    SegmentedButton(
                                        selected = settings.backgroundModeEnabled == option.first,
                                        onClick = {
                                            onUpdate { it.copy(backgroundModeEnabled = option.first) }
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                                        modifier = Modifier.settingsFocusable(
                                            isTelevision = isTelevision,
                                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                                        ),
                                    ) {
                                        Text(option.second)
                                    }
                                }
                            }
                            Text(
                                if (settings.backgroundModeEnabled) {
                                    "기본값입니다. 화면이 꺼지거나 다른 앱으로 전환해도 매이트 모드의 허용된 소리 감시를 유지합니다."
                                } else {
                                    "앱을 벗어나거나 화면이 꺼지면 마이크와 소리 감시를 멈춥니다."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            LabeledSwitch(
                                title = "플래시 사용",
                                detail = if (state.torchAvailable) {
                                    "화들짝 모드에서만 켜짐"
                                } else {
                                    "이 기기에서는 사용할 수 없음"
                                },
                                checked = settings.torchEnabled,
                                enabled = true,
                                isTelevision = isTelevision,
                            ) { enabled -> onUpdate { it.copy(torchEnabled = enabled) } }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            LabeledSwitch(
                                title = "카메라 사용",
                                detail = cameraAmbientDetail,
                                checked = settings.cameraAmbientSensingEnabled,
                                enabled = true,
                                isTelevision = isTelevision,
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
                                isTelevision = isTelevision,
                            ) { enabled ->
                                onUpdate { it.copy(soundSensingEnabled = enabled) }
                                if (enabled && !state.hasMicrophonePermission) {
                                    onRequestMicrophonePermission()
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        }
                        LabeledSwitch(
                            title = "위치 정보 사용",
                            detail = when {
                                !settings.weatherLocationEnabled -> "사용하지 않음"
                                state.hasApproximateLocationPermission -> "현재 위치의 날씨를 표시할 때만 사용"
                                else -> "위치 권한 필요"
                            },
                            checked = settings.weatherLocationEnabled &&
                                state.hasApproximateLocationPermission,
                            isTelevision = isTelevision,
                        ) { enabled ->
                            onUpdate { it.copy(weatherLocationEnabled = enabled) }
                            if (enabled && !state.hasApproximateLocationPermission) {
                                onRequestApproximateLocationPermission()
                            }
                        }
                        if (!isTelevision && !state.hasCameraPermission && settings.cameraAmbientSensingEnabled) {
                            TextButton(
                                onClick = onRequestCameraPermission,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .settingsFocusable(
                                        isTelevision = isTelevision,
                                        shape = RoundedCornerShape(12.dp),
                                    ),
                            ) {
                                Text("카메라 권한 다시 요청")
                            }
                        }
                        if (missingPermissionCount > 0) {
                            TextButton(
                                onClick = onOpenAppSettings,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .settingsFocusable(
                                        isTelevision = isTelevision,
                                        shape = RoundedCornerShape(12.dp),
                                    ),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingsFocusable(
                                    isTelevision = isTelevision,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Text("보이소 열기")
                        }
                    }

                    SettingsSectionKind.SLEEP_SOUNDS -> SettingsCard(
                        title = "잠꼬대와 코골이",
                        subtitle = "매이트 모드에서만 작동합니다",
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
                            isTelevision = isTelevision,
                        ) { enabled -> onUpdate { it.copy(multiStimulusWakeEnabled = enabled) } }
                        LabeledSwitch(
                            title = "코골이·잠꼬대 저장",
                            detail = "후보 소리가 날 때 필요한 구간만 기기에 저장",
                            checked = settings.recordingEnabled,
                            isTelevision = isTelevision,
                        ) { enabled -> onUpdate { it.copy(recordingEnabled = enabled) } }
                        TextButton(
                            onClick = onOpenRecordings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingsFocusable(
                                    isTelevision = isTelevision,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Text(
                                if (state.recordingCount == 0) {
                                    "잠소리 열기"
                                } else {
                                    "잠소리 ${state.recordingCount}개 보기"
                                },
                            )
                        }
                        Text(
                            "처음 1분 동안 방의 평소 소리를 익힙니다. 이후 평균보다 커진 순간에는 바로 화들짝 반응하고, 녹음은 이 기기 안에서만 처리합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SettingsSectionKind.INFORMATION -> SettingsCard(
                        title = "정보",
                        subtitle = "개인정보, 저작권과 앱 정보를 확인합니다",
                        icon = Icons.Default.Info,
                    ) {
                        if (!isTelevision) {
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
                        }
                        Text("S.tand ${BuildConfig.VERSION_NAME}")
                        Text(
                            "빌드 ${BuildConfig.BUILD_NUMBER} · versionCode ${BuildConfig.VERSION_CODE}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isTelevision) {
                                        Modifier.clickable {
                                            onAutomaticUpdateDownloadChanged(!automaticUpdateDownloadEnabled)
                                        }
                                    } else {
                                        Modifier
                                    },
                                )
                                .settingsFocusable(
                                    isTelevision = isTelevision,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .padding(
                                    horizontal = if (isTelevision) 8.dp else 0.dp,
                                    vertical = if (isTelevision) 6.dp else 0.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("업데이트 자동 다운로드")
                                Text(
                                    "데이터 요금이 없는 네트워크에서 받습니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = automaticUpdateDownloadEnabled,
                                onCheckedChange = if (isTelevision) null else onAutomaticUpdateDownloadChanged,
                            )
                        }
                        TextButton(
                            onClick = onCheckForUpdates,
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingsFocusable(
                                    isTelevision = isTelevision,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) { Text("업데이트 확인") }
                        TextButton(
                            onClick = { uriHandler.openUri("https://github.com/armsone") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingsFocusable(
                                    isTelevision = isTelevision,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null)
                            Text(" 만든 사람 GitHub · github.com/armsone")
                        }
                        TextButton(
                            onClick = { uriHandler.openUri("https://nasfinder.com") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingsFocusable(
                                    isTelevision = isTelevision,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null)
                            Text(" 공식 홈페이지 · nasfinder.com")
                        }
                        TextButton(
                            onClick = onOpenFontLicenses,
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingsFocusable(
                                    isTelevision = isTelevision,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null)
                            Text(" 내장 폰트 저작권", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            Text("원문 포함", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                        TextButton(
                            onClick = { uriHandler.openUri("https://open-meteo.com/") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingsFocusable(
                                    isTelevision = isTelevision,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Text("날씨 데이터 · Open-Meteo")
                        }
                        TextButton(
                            onClick = { showResetConfirmation = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingsFocusable(
                                    isTelevision = isTelevision,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Text(" 추천 설정 복원")
                        }
                    }
                    }
                }

                item(span = StaggeredGridItemSpan.FullLine) { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("추천 설정으로 되돌릴까요?") },
            text = { Text("저장한 라디오 채널을 포함해 앱 설정이 처음 모습으로 돌아갑니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        onRestoreRecommended()
                    },
                    modifier = Modifier.settingsFocusable(
                        isTelevision = isTelevision,
                        shape = RoundedCornerShape(12.dp),
                    ),
                ) { Text("추천 설정 복원") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmation = false },
                    modifier = Modifier.settingsFocusable(
                        isTelevision = isTelevision,
                        shape = RoundedCornerShape(12.dp),
                    ),
                ) { Text("취소") }
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
                    modifier = Modifier.settingsFocusable(
                        isTelevision = isTelevision,
                        shape = RoundedCornerShape(12.dp),
                    ),
                ) { Text("채널 삭제") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRadioDeletionID = null },
                    modifier = Modifier.settingsFocusable(
                        isTelevision = isTelevision,
                        shape = RoundedCornerShape(12.dp),
                    ),
                ) { Text("취소") }
            },
        )
    }

}

@Composable
private fun InternetRadioSettingsShortcut(
    onClick: () -> Unit,
    isTelevision: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .settingsFocusable(
                isTelevision = isTelevision,
                shape = RoundedCornerShape(15.dp),
            ),
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp),
            )
            Text(
                "인터넷 라디오",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun SettingsHero(
    state: StandUiState,
    onModePreferenceSelected: (StandModePreference) -> Unit,
    isTelevision: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    ),
                ),
                shape = RoundedCornerShape(22.dp),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.stand_brand_icon),
                contentDescription = null,
                modifier = Modifier.size(58.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("S.tand", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (isTelevision) "음악·시간·날씨" else "낮에는 오브제\n밤에는 매이트",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.64f),
                )
            }
            if (!isTelevision) {
                val statusTitle = if (state.isSessionActive) {
                    state.experienceMode.title
                } else {
                    "S.tand 멈춤"
                }
                Surface(
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "현재 상태, $statusTitle"
                    },
                    color = if (state.isSessionActive) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        Color.White.copy(alpha = 0.07f)
                    },
                    contentColor = if (state.isSessionActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.58f)
                    },
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = when {
                                !state.isSessionActive -> Icons.Default.PauseCircle
                                state.experienceMode == com.armsone.stand.model.StandExperienceMode.OBJECT ->
                                    Icons.Default.LightMode
                                else -> Icons.Default.DarkMode
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(statusTitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (!isTelevision) {
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
        }
    }
}

@Composable
private fun ThemePalettePicker(
    selectedTheme: StandDisplayTheme,
    onThemeSelected: (StandDisplayTheme) -> Unit,
    isTelevision: Boolean = false,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = 10.dp
        val columnCount = StandDisplayTheme.entries.size
        val tileWidth = ((maxWidth.value - spacing.value * (columnCount - 1)) / columnCount).dp
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            maxItemsInEachRow = columnCount,
        ) {
            StandDisplayTheme.entries.forEach { theme ->
                val selected = selectedTheme == theme
                Surface(
                    onClick = { onThemeSelected(theme) },
                    modifier = Modifier
                        .width(tileWidth)
                        .settingsFocusable(
                            isTelevision = isTelevision,
                            shape = RoundedCornerShape(14.dp),
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${theme.title} 테마"
                            this.selected = selected
                            role = Role.RadioButton
                        },
                    color = if (selected) {
                        themeAccent(theme).copy(alpha = 0.12f)
                    } else {
                        Color.White.copy(alpha = 0.04f)
                    },
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(themeAccent(theme), CircleShape)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = Color.White.copy(alpha = if (selected) 0.90f else 0.18f),
                                        shape = CircleShape,
                                    ),
                            )
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .offset(x = (-7).dp, y = (-7).dp)
                                    .background(
                                        Color.White.copy(
                                            alpha = if (theme == StandDisplayTheme.GRAYSCALE) 0.12f else 0.18f,
                                        ),
                                        CircleShape,
                                    ),
                            )
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (theme == StandDisplayTheme.GRAYSCALE) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Text(theme.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun themeAccent(theme: StandDisplayTheme): Color = when (theme) {
    StandDisplayTheme.COLOR -> Color(0xFFFF8A2A)
    StandDisplayTheme.GRAYSCALE -> Color(0xFFE4E4E4)
    StandDisplayTheme.MIDNIGHT -> Color(0xFF61ADFF)
    StandDisplayTheme.SAGE -> Color(0xFF8CC69E)
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
    isTelevision: Boolean = false,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onOpenBrowser: () -> Unit,
    onClose: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .standPanelSurface(isDimmed = false, cornerRadius = 16.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (onDelete == null) "채널 추가" else "채널 수정",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onClose,
                modifier = Modifier.settingsFocusable(
                    isTelevision = isTelevision,
                    shape = RoundedCornerShape(12.dp),
                ),
            ) { Text("닫기") }
        }
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .settingsFocusable(
                    isTelevision = isTelevision,
                    shape = RoundedCornerShape(12.dp),
                ),
            label = { Text("이름 (선택)") },
            singleLine = true,
        )
        val isHttp = url.trim().startsWith("http://", ignoreCase = true)
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier
                .fillMaxWidth()
                .settingsFocusable(
                    isTelevision = isTelevision,
                    shape = RoundedCornerShape(12.dp),
                ),
            label = { Text("주소") },
            placeholder = { Text("https://… 또는 http://…") },
            singleLine = true,
            isError = error != null,
            supportingText = {
                when {
                    error != null -> Text(error)
                    isHttp -> Text("암호화되지 않은 방송 주소", color = Color(0xFFFBBF24))
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    clipboardManager.getText()?.text?.let { pasted ->
                        onUrlChange(pasted.take(2_048))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .settingsFocusable(
                        isTelevision = isTelevision,
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) { Text("붙여넣기") }
            TextButton(
                onClick = onOpenBrowser,
                modifier = Modifier
                    .weight(1f)
                    .settingsFocusable(
                        isTelevision = isTelevision,
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) { Text("웹에서 찾기") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            onDelete?.let { delete ->
                TextButton(
                    onClick = delete,
                    modifier = Modifier.settingsFocusable(
                        isTelevision = isTelevision,
                        shape = RoundedCornerShape(12.dp),
                    ),
                ) { Text("삭제") }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSave,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .settingsFocusable(
                        isTelevision = isTelevision,
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) { Text("저장") }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.085f)),
                ),
                RoundedCornerShape(22.dp),
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp)),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(
                modifier = Modifier.padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.64f),
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.10f))
            Column(
                modifier = Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun LabeledSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    isTelevision: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isTelevision && enabled) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else {
                    Modifier
                },
            )
            .settingsFocusable(
                isTelevision = isTelevision,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(
                horizontal = if (isTelevision) 8.dp else 0.dp,
                vertical = if (isTelevision) 6.dp else 0.dp,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $detail"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.38f),
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.28f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (isTelevision) null else onCheckedChange,
            enabled = enabled,
        )
    }
}
