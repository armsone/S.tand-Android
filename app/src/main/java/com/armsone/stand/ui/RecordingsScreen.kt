@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.armsone.stand.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.armsone.stand.recording.RecordingClip
import com.armsone.stand.recording.RecordingSessionGroup
import com.armsone.stand.recording.RecordingSessionPolicy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToLong

@Composable
fun RecordingsScreen(
    recordings: List<RecordingClip>,
    sessionGroups: List<RecordingSessionGroup>,
    isBusy: Boolean,
    message: String?,
    onMessageDismiss: () -> Unit,
    onBack: () -> Unit,
    onDelete: (RecordingClip) -> Unit,
    onShare: (RecordingClip) -> Unit,
    onMergeSelected: (List<RecordingClip>, Boolean) -> Unit,
    onMergeToday: (Boolean) -> Unit,
    onDeleteSelected: (List<RecordingClip>) -> Unit,
    onDeleteAll: () -> Unit,
    onPlaybackStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val currentPlaybackCallback = rememberUpdatedState(onPlaybackStateChanged)
    val player = remember {
        RecordingPlaybackController { playing -> currentPlaybackCallback.value(playing) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingDelete by remember { mutableStateOf<RecordingClip?>(null) }
    var pendingDeleteSelected by remember { mutableStateOf(false) }
    var pendingDeleteAll by remember { mutableStateOf(false) }
    var pendingMerge by remember { mutableStateOf<PendingMerge?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedPaths by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var expandedSessionIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var mergedExpanded by rememberSaveable { mutableStateOf(false) }
    var listActionsExpanded by remember { mutableStateOf(false) }

    val sortedRecordings = remember(recordings) {
        recordings.sortedWith(
            compareByDescending<RecordingClip> { it.createdAt }
                .thenByDescending { it.file.name },
        )
    }
    val originals = remember(sortedRecordings) { sortedRecordings.filterNot(RecordingClip::isMerged) }
    val mergedClips = remember(sortedRecordings) { sortedRecordings.filter(RecordingClip::isMerged) }
    val originalPaths = remember(originals) { originals.mapTo(hashSetOf()) { it.file.absolutePath } }
    val selectedClips = remember(originals, selectedPaths) {
        val selected = selectedPaths.toHashSet()
        originals.filter { it.file.absolutePath in selected }
    }
    val zoneId = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zoneId)
    val todayOriginals = remember(originals, today, zoneId) {
        originals.filter { it.createdAt.atZone(zoneId).toLocalDate() == today }
    }
    val groupedPaths = remember(sessionGroups) {
        sessionGroups.flatMapTo(hashSetOf()) { group ->
            group.clips.filterNot(RecordingClip::isMerged).map { it.file.absolutePath }
        }
    }
    val ungroupedOriginals = remember(originals, groupedPaths) {
        originals.filterNot { it.file.absolutePath in groupedPaths }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    LaunchedEffect(originalPaths) {
        val retained = selectedPaths.filter { it in originalPaths }
        if (retained != selectedPaths) selectedPaths = retained
    }

    LaunchedEffect(sortedRecordings.map { it.file.absolutePath }) {
        val activeFile = player.activeClip?.file ?: return@LaunchedEffect
        if (sortedRecordings.none { it.file.absolutePath == activeFile.absolutePath }) player.stop()
    }

    LaunchedEffect(isBusy) {
        if (isBusy) player.stop()
    }

    LaunchedEffect(player.activeClip?.file, player.isPlaying) {
        while (player.activeClip != null) {
            player.refreshProgress()
            delay(PROGRESS_UPDATE_MILLIS)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(recordingsBackground()),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("수면 소리") },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                player.stop()
                                onBack()
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기")
                        }
                    },
                    actions = {
                        if (sortedRecordings.isNotEmpty()) {
                            IconButton(
                                onClick = { listActionsExpanded = true },
                                enabled = !isBusy,
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "목록 작업")
                            }
                            DropdownMenu(
                                expanded = listActionsExpanded,
                                onDismissRequest = { listActionsExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("전체 선택") },
                                    onClick = {
                                        listActionsExpanded = false
                                        selectionMode = true
                                        selectedPaths = originals.map { it.file.absolutePath }
                                    },
                                    enabled = originals.isNotEmpty(),
                                )
                                DropdownMenuItem(
                                    text = { Text("오늘 선택") },
                                    onClick = {
                                        listActionsExpanded = false
                                        selectionMode = true
                                        selectedPaths = todayOriginals.map { it.file.absolutePath }
                                    },
                                    enabled = todayOriginals.isNotEmpty(),
                                )
                                DropdownMenuItem(
                                    text = { Text("선택 모두 해제") },
                                    onClick = {
                                        listActionsExpanded = false
                                        selectedPaths = emptyList()
                                    },
                                    enabled = selectedPaths.isNotEmpty(),
                                )
                                DropdownMenuItem(
                                    text = { Text("전체 삭제", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        listActionsExpanded = false
                                        pendingDeleteAll = true
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                Column {
                    if (selectedClips.isNotEmpty()) {
                        RecordingSelectionDock(
                            count = selectedClips.size,
                            canMerge = selectedClips.size >= 2 && !isBusy,
                            isBusy = isBusy,
                            onClear = { selectedPaths = emptyList() },
                            onMerge = { pendingMerge = PendingMerge.Selected },
                            onDelete = { pendingDeleteSelected = true },
                        )
                    }
                    player.activeClip?.let { clip ->
                        PlaybackPanel(
                            clip = clip,
                            isPlaying = player.isPlaying,
                            isPreparing = player.isPreparing,
                            positionMillis = player.positionMillis,
                            durationMillis = player.durationMillis,
                            boostEnabled = player.boostEnabled,
                            onToggle = { player.toggle(clip) },
                            onToggleBoost = player::toggleBoost,
                            onSeek = player::seekTo,
                            onClose = player::stop,
                        )
                    }
                }
            },
        ) { innerPadding ->
            if (sortedRecordings.isEmpty() && sessionGroups.isEmpty()) {
                EmptyRecordings(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                )
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    val columnCount = if (maxWidth >= 600.dp) 2 else 1
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 12.dp,
                            end = 16.dp,
                            bottom = 28.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
                            RecordingSummaryCard(
                                sessionCount = sessionGroups.size,
                                originals = originals,
                            )
                        }

                        item(key = "today", span = { GridItemSpan(maxLineSpan) }) {
                            TodayMergeCard(
                                clips = todayOriginals,
                                enabled = todayOriginals.size >= 2 && !isBusy,
                                isBusy = isBusy,
                                onMerge = { pendingMerge = PendingMerge.Today },
                            )
                        }

                        if (selectionMode && originals.isNotEmpty()) {
                            item(key = "selection", span = { GridItemSpan(maxLineSpan) }) {
                                SelectionToolsCard(
                                    selectedCount = selectedClips.size,
                                    enabled = !isBusy,
                                    hasToday = todayOriginals.isNotEmpty(),
                                    onSelectAll = { selectedPaths = originals.map { it.file.absolutePath } },
                                    onSelectToday = {
                                        selectedPaths = todayOriginals.map { it.file.absolutePath }
                                    },
                                    onClear = { selectedPaths = emptyList() },
                                )
                            }
                        }

                        if (isBusy || !message.isNullOrBlank()) {
                            item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                                RecordingStatusCard(
                                    isBusy = isBusy,
                                    message = message,
                                    onDismiss = onMessageDismiss,
                                )
                            }
                        }

                        player.errorMessage?.let { error ->
                            item(key = "playback-error", span = { GridItemSpan(maxLineSpan) }) {
                                PlaybackErrorCard(message = error)
                            }
                        }

                        items(
                            items = sessionGroups,
                            key = { group -> "session-${group.id}" },
                        ) { group ->
                            val expanded = group.id in expandedSessionIds
                            RecordingSessionCard(
                                session = group,
                                isExpanded = expanded,
                                selectedPaths = selectedPaths.toSet(),
                                selectionMode = selectionMode,
                                isBusy = isBusy,
                                player = player,
                                onToggleExpanded = {
                                    expandedSessionIds = if (expanded) {
                                        expandedSessionIds - group.id
                                    } else {
                                        expandedSessionIds + group.id
                                    }
                                },
                                onToggleSelection = { clip ->
                                    selectedPaths = selectedPaths.toggled(clip.file.absolutePath)
                                },
                                onShare = onShare,
                                onDelete = { pendingDelete = it },
                            )
                        }

                        if (ungroupedOriginals.isNotEmpty()) {
                            item(key = "ungrouped") {
                                RecordingListCard(
                                    title = "기타 수면 소리",
                                    subtitle = "세션 시간이 없는 원본 ${ungroupedOriginals.size}개",
                                    clips = ungroupedOriginals,
                                    selectionMode = selectionMode,
                                    selectedPaths = selectedPaths.toSet(),
                                    isBusy = isBusy,
                                    player = player,
                                    onToggleSelection = { clip ->
                                        selectedPaths = selectedPaths.toggled(clip.file.absolutePath)
                                    },
                                    onShare = onShare,
                                    onDelete = { pendingDelete = it },
                                )
                            }
                        }

                        if (mergedClips.isNotEmpty()) {
                            item(key = "merged", span = { GridItemSpan(maxLineSpan) }) {
                                MergedRecordingsCard(
                                    clips = mergedClips,
                                    expanded = mergedExpanded,
                                    isBusy = isBusy,
                                    player = player,
                                    onToggleExpanded = { mergedExpanded = !mergedExpanded },
                                    onShare = onShare,
                                    onDelete = { pendingDelete = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { clip ->
        DeleteRecordingDialog(
            clip = clip,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                if (!isBusy) {
                    if (player.activeClip?.file?.absolutePath == clip.file.absolutePath) player.stop()
                    selectedPaths = selectedPaths - clip.file.absolutePath
                    onDelete(clip)
                }
            },
        )
    }

    if (pendingDeleteSelected) {
        ConfirmActionDialog(
            title = "선택한 녹음 ${selectedClips.size}개를 삭제할까요?",
            message = "삭제한 원본 녹음은 복구할 수 없습니다.",
            confirmLabel = "선택 항목 삭제",
            onDismiss = { pendingDeleteSelected = false },
            onConfirm = {
                pendingDeleteSelected = false
                if (!isBusy && selectedClips.isNotEmpty()) {
                    player.stop()
                    val clips = selectedClips
                    selectedPaths = emptyList()
                    onDeleteSelected(clips)
                }
            },
        )
    }

    if (pendingDeleteAll) {
        ConfirmActionDialog(
            title = "저장된 수면 소리를 모두 삭제할까요?",
            message = "삭제한 녹음은 복구할 수 없습니다.",
            confirmLabel = "모두 삭제",
            onDismiss = { pendingDeleteAll = false },
            onConfirm = {
                pendingDeleteAll = false
                if (!isBusy) {
                    player.stop()
                    selectedPaths = emptyList()
                    expandedSessionIds = emptyList()
                    onDeleteAll()
                }
            },
        )
    }

    pendingMerge?.let { target ->
        val count = if (target == PendingMerge.Today) todayOriginals.size else selectedClips.size
        MergeSourcesDialog(
            count = count,
            onDismiss = { pendingMerge = null },
            onMerge = { deleteSources ->
                pendingMerge = null
                if (!isBusy && count >= 2) {
                    player.stop()
                    if (target == PendingMerge.Today) {
                        onMergeToday(deleteSources)
                    } else {
                        val clips = selectedClips
                        selectedPaths = emptyList()
                        onMergeSelected(clips, deleteSources)
                    }
                }
            },
        )
    }
}

@Composable
private fun RecordingSummaryCard(
    sessionCount: Int,
    originals: List<RecordingClip>,
) {
    val totalDuration = originals.sumOf(RecordingClip::durationSeconds)
    RecordingSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(27.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("기록 요약", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "잠자리 ${sessionCount}회 · 원본 ${originals.size}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatDuration(totalDuration),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "원본 소리",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TodayMergeCard(
    clips: List<RecordingClip>,
    enabled: Boolean,
    isBusy: Boolean,
    onMerge: () -> Unit,
) {
    RecordingSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text("오늘", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${clips.size}개 · ${formatDuration(clips.sumOf(RecordingClip::durationSeconds))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onMerge, enabled = enabled) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("오늘 소리 합치기")
                }
            }
        }
    }
}

@Composable
private fun SelectionToolsCard(
    selectedCount: Int,
    enabled: Boolean,
    hasToday: Boolean,
    onSelectAll: () -> Unit,
    onSelectToday: () -> Unit,
    onClear: () -> Unit,
) {
    RecordingSurface {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (selectedCount == 0) "합치거나 지울 원본 소리를 선택합니다" else "${selectedCount}개 선택됨",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = onSelectAll, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("모두 고르기")
                }
                TextButton(
                    onClick = onSelectToday,
                    enabled = enabled && hasToday,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("오늘만")
                }
                TextButton(
                    onClick = onClear,
                    enabled = enabled && selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("선택 풀기")
                }
            }
            Text(
                "합본은 선택할 수 없으며, 선택한 원본은 아래 도구에서 합치거나 삭제할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordingSessionCard(
    session: RecordingSessionGroup,
    isExpanded: Boolean,
    selectedPaths: Set<String>,
    selectionMode: Boolean,
    isBusy: Boolean,
    player: RecordingPlaybackController,
    onToggleExpanded: () -> Unit,
    onToggleSelection: (RecordingClip) -> Unit,
    onShare: (RecordingClip) -> Unit,
    onDelete: (RecordingClip) -> Unit,
) {
    val selectedCount = session.clips.count { !it.isMerged && it.file.absolutePath in selectedPaths }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        shadowElevation = 3.dp,
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .semantics(mergeDescendants = true) {
                        contentDescription = sessionAccessibilityLabel(session)
                        stateDescription = if (isExpanded) "녹음 목록 펼쳐짐" else "녹음 목록 접힘"
                    }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                sessionTitle(session),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (session.isInferred) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        "시간 추정",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                        Text(
                            buildString {
                                append(sessionTimeRange(session))
                                if (session.clips.isNotEmpty()) {
                                    append(" · 소리 ${session.clips.size}개 · ")
                                    append(formatDuration(session.totalDurationSeconds))
                                }
                                if (session.startleEvents.isNotEmpty()) {
                                    append(" · 화들짝 ${session.startleEvents.size}회")
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selectedCount > 0) {
                        Text(
                            "${selectedCount} 선택",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "녹음 목록 접기" else "녹음 목록 펼치기",
                        modifier = Modifier.rotate(if (isExpanded) 180f else 0f),
                    )
                }
                SessionTimeline(session)
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    session.clips.forEach { clip ->
                        RecordingRow(
                            clip = clip,
                            isActive = player.activeClip?.file?.absolutePath == clip.file.absolutePath,
                            isPlaying = player.activeClip?.file?.absolutePath == clip.file.absolutePath &&
                                player.isPlaying,
                            isPreparing = player.activeClip?.file?.absolutePath == clip.file.absolutePath &&
                                player.isPreparing,
                            selectionMode = selectionMode,
                            isSelected = clip.file.absolutePath in selectedPaths,
                            enabled = !isBusy,
                            onToggleSelection = { onToggleSelection(clip) },
                            onPlay = { player.toggle(clip) },
                            onShare = { onShare(clip) },
                            onDelete = { onDelete(clip) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionTimeline(session: RecordingSessionGroup) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        ) {
            drawRoundRect(trackColor, cornerRadius = CornerRadius(size.height / 2f))
            val sessionMillis = max(1L, Duration.between(session.startedAt, session.endedAt).toMillis())
            session.clips.forEach { clip ->
                val fraction = RecordingSessionPolicy.markerFraction(
                    clip = clip,
                    sessionStart = session.startedAt,
                    sessionEnd = session.endedAt,
                ).toFloat()
                val proportionalWidth = size.width *
                    (clip.durationSeconds * 1_000.0 / sessionMillis.toDouble()).toFloat()
                val markerWidth = proportionalWidth.coerceIn(7.dp.toPx(), 22.dp.toPx())
                val left = ((size.width - markerWidth) * fraction).coerceIn(0f, size.width - markerWidth)
                drawRoundRect(
                    color = markerColor,
                    topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(markerWidth, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
        ) {
            drawRoundRect(
                color = trackColor.copy(alpha = 0.42f),
                cornerRadius = CornerRadius(size.height / 2f),
            )
            session.startleEvents.forEach { event ->
                val start = RecordingSessionPolicy.markerFraction(
                    instant = event.startedAt,
                    sessionStart = session.startedAt,
                    sessionEnd = session.endedAt,
                ).toFloat()
                val end = RecordingSessionPolicy.markerFraction(
                    instant = event.endedAt ?: session.endedAt,
                    sessionStart = session.startedAt,
                    sessionEnd = session.endedAt,
                ).toFloat()
                val left = size.width * start
                val markerWidth = (size.width * (end - start).coerceAtLeast(0f))
                    .coerceAtLeast(2.dp.toPx())
                    .coerceAtMost(size.width - left)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.88f),
                    topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(markerWidth, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(formatClockTime(session.startedAt), style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "수면 소리 · 얇은 선은 화들짝",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(formatClockTime(session.endedAt), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RecordingListCard(
    title: String,
    subtitle: String,
    clips: List<RecordingClip>,
    selectionMode: Boolean,
    selectedPaths: Set<String>,
    isBusy: Boolean,
    player: RecordingPlaybackController,
    onToggleSelection: (RecordingClip) -> Unit,
    onShare: (RecordingClip) -> Unit,
    onDelete: (RecordingClip) -> Unit,
) {
    RecordingSurface {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            clips.forEach { clip ->
                RecordingRow(
                    clip = clip,
                    isActive = player.activeClip?.file?.absolutePath == clip.file.absolutePath,
                    isPlaying = player.activeClip?.file?.absolutePath == clip.file.absolutePath && player.isPlaying,
                    isPreparing = player.activeClip?.file?.absolutePath == clip.file.absolutePath &&
                        player.isPreparing,
                    selectionMode = selectionMode,
                    isSelected = clip.file.absolutePath in selectedPaths,
                    enabled = !isBusy,
                    onToggleSelection = { onToggleSelection(clip) },
                    onPlay = { player.toggle(clip) },
                    onShare = { onShare(clip) },
                    onDelete = { onDelete(clip) },
                )
            }
        }
    }
}

@Composable
private fun MergedRecordingsCard(
    clips: List<RecordingClip>,
    expanded: Boolean,
    isBusy: Boolean,
    player: RecordingPlaybackController,
    onToggleExpanded: () -> Unit,
    onShare: (RecordingClip) -> Unit,
    onDelete: (RecordingClip) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        shadowElevation = 3.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("한데 묶은 소리", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${clips.size}개 · 원본과 별도로 보관",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "합본 목록 접기" else "합본 목록 펼치기",
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    clips.forEach { clip ->
                        RecordingRow(
                            clip = clip,
                            isActive = player.activeClip?.file?.absolutePath == clip.file.absolutePath,
                            isPlaying = player.activeClip?.file?.absolutePath == clip.file.absolutePath &&
                                player.isPlaying,
                            isPreparing = player.activeClip?.file?.absolutePath == clip.file.absolutePath &&
                                player.isPreparing,
                            selectionMode = false,
                            isSelected = false,
                            enabled = !isBusy,
                            onToggleSelection = {},
                            onPlay = { player.toggle(clip) },
                            onShare = { onShare(clip) },
                            onDelete = { onDelete(clip) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(
    clip: RecordingClip,
    isActive: Boolean,
    isPlaying: Boolean,
    isPreparing: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    onToggleSelection: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.52f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            },
        ),
        shadowElevation = if (isActive) 4.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                clip.isMerged -> Icon(
                    Icons.Default.Layers,
                    contentDescription = "합본",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp).padding(9.dp),
                )
                selectionMode -> IconButton(
                    onClick = onToggleSelection,
                    enabled = enabled,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = if (isSelected) "녹음 선택 해제" else "녹음 선택",
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            PlaybackIconButton(
                isPlaying = isPlaying,
                isPreparing = isPreparing,
                enabled = enabled,
                onClick = onPlay,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = enabled,
                        onClick = if (selectionMode && !clip.isMerged) onToggleSelection else onPlay,
                    )
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = if (clip.isMerged) "${formatRecordingTime(clip)} 합본" else formatRecordingTime(clip),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatDuration(clip.durationSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            RecordingActionButton(
                icon = Icons.Default.Share,
                description = "녹음 공유",
                enabled = enabled,
                onClick = onShare,
            )
            RecordingActionButton(
                icon = Icons.Default.Delete,
                description = "녹음 삭제",
                enabled = enabled,
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun PlaybackIconButton(
    isPlaying: Boolean,
    isPreparing: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled && !isPreparing,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
    ) {
        if (isPreparing) {
            CircularProgressIndicator(
                modifier = Modifier.size(19.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "재생 일시 정지" else "녹음 재생",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RecordingActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RecordingSelectionDock(
    count: Int,
    canMerge: Boolean,
    isBusy: Boolean,
    onClear: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
) {
    val useLargeTextLayout = LocalDensity.current.fontScale >= 1.3f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        shadowElevation = 6.dp,
    ) {
        if (useLargeTextLayout) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClear, enabled = !isBusy) {
                        Icon(Icons.Default.Close, contentDescription = "선택 해제")
                    }
                    Text(
                        "${count}개 선택",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDelete, enabled = !isBusy) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "선택 삭제",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(
                    onClick = onMerge,
                    enabled = canMerge,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("한데 묶기")
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onClear, enabled = !isBusy) {
                    Icon(Icons.Default.Close, contentDescription = "선택 해제")
                }
                Text("${count}개 선택", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onMerge, enabled = canMerge) { Text("한데 묶기") }
                IconButton(onClick = onDelete, enabled = !isBusy) {
                    Icon(Icons.Default.Delete, contentDescription = "선택 삭제", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PlaybackPanel(
    clip: RecordingClip,
    isPlaying: Boolean,
    isPreparing: Boolean,
    positionMillis: Int,
    durationMillis: Int,
    boostEnabled: Boolean,
    onToggle: () -> Unit,
    onToggleBoost: () -> Unit,
    onSeek: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val useLargeTextLayout = LocalDensity.current.fontScale >= 1.3f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
        shadowElevation = 6.dp,
    ) {
        if (useLargeTextLayout) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlaybackIconButton(isPlaying = isPlaying, isPreparing = isPreparing, onClick = onToggle)
                    PlaybackBoostButton(boostEnabled = boostEnabled, onClick = onToggleBoost)
                    Text(
                        formatRecordingTime(clip),
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    PlaybackCloseButton(onClose)
                }
                PlaybackProgress(
                    positionMillis = positionMillis,
                    durationMillis = durationMillis,
                    isPreparing = isPreparing,
                    onSeek = onSeek,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlaybackIconButton(isPlaying = isPlaying, isPreparing = isPreparing, onClick = onToggle)
                PlaybackBoostButton(boostEnabled = boostEnabled, onClick = onToggleBoost)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        formatRecordingTime(clip),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    PlaybackProgress(
                        positionMillis = positionMillis,
                        durationMillis = durationMillis,
                        isPreparing = isPreparing,
                        onSeek = onSeek,
                    )
                }
                PlaybackCloseButton(onClose)
            }
        }
    }
}

@Composable
private fun PlaybackBoostButton(
    boostEnabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = if (boostEnabled) {
                    "작은 소리 두 배 증폭 끄기"
                } else {
                    "작은 소리 두 배 증폭 켜기"
                }
                stateDescription = if (boostEnabled) "켜짐" else "꺼짐"
            },
    ) {
        Text(
            "2×",
            color = if (boostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PlaybackProgress(
    positionMillis: Int,
    durationMillis: Int,
    isPreparing: Boolean,
    onSeek: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Slider(
            value = positionMillis.coerceIn(0, max(durationMillis, 1)).toFloat(),
            onValueChange = { onSeek(it.toInt()) },
            valueRange = 0f..max(durationMillis, 1).toFloat(),
            enabled = !isPreparing && durationMillis > 0,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                formatDuration(positionMillis / MILLIS_PER_SECOND),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                formatDuration(durationMillis / MILLIS_PER_SECOND),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackCloseButton(onClose: () -> Unit) {
    IconButton(
        onClick = onClose,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Icon(Icons.Default.Close, contentDescription = "재생 닫기")
    }
}

@Composable
private fun EmptyRecordings(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
        }
        Text(
            "저장된 수면 소리가 없습니다",
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "매이트 모드에서 코골이와 잠꼬대 후보가 감지되면\n필요한 구간만 저장합니다.",
            modifier = Modifier.padding(top = 7.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecordingStatusCard(
    isBusy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
) {
    RecordingSurface {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                message?.takeIf { it.isNotBlank() } ?: "녹음을 처리하고 있습니다…",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!isBusy && !message.isNullOrBlank()) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "작업 메시지 닫기")
                }
            }
        }
    }
}

@Composable
private fun PlaybackErrorCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.36f)),
        shadowElevation = 2.dp,
    ) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DeleteRecordingDialog(
    clip: RecordingClip,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmActionDialog(
        title = "이 녹음을 삭제할까요?",
        message = "${formatRecordingTime(clip)} · ${formatDuration(clip.durationSeconds)}\n" +
            "삭제한 녹음은 복구할 수 없습니다.",
        confirmLabel = "삭제",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        RecordingDialogSurface {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("취소") }
                TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun MergeSourcesDialog(
    count: Int,
    onDismiss: () -> Unit,
    onMerge: (Boolean) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        RecordingDialogSurface {
            Text("녹음 ${count}개를 한데 묶을까요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "합본을 만든 뒤 원본을 보관할지 삭제할지 선택하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onMerge(false) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("원본 보관하며 합치기")
            }
            TextButton(
                onClick = { onMerge(true) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("합치고 원본 삭제", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("취소") }
        }
    }
}

@Composable
private fun RecordingDialogSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun RecordingSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        shadowElevation = 4.dp,
        content = content,
    )
}

private class RecordingPlaybackController(
    private val onPlaybackStateChanged: (Boolean) -> Unit,
) {
    var activeClip by mutableStateOf<RecordingClip?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isPreparing by mutableStateOf(false)
        private set
    var positionMillis by mutableIntStateOf(0)
        private set
    var durationMillis by mutableIntStateOf(0)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var boostEnabled by mutableStateOf(true)
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    fun toggleBoost() {
        boostEnabled = !boostEnabled
        applyBoost()
    }

    fun toggle(clip: RecordingClip) {
        errorMessage = null
        if (activeClip?.file == clip.file) {
            if (isPreparing) return
            if (isPlaying) pause() else resume()
            return
        }
        start(clip)
    }

    fun seekTo(positionMillis: Int) {
        val player = mediaPlayer ?: return
        if (isPreparing || durationMillis <= 0) return
        val clamped = positionMillis.coerceIn(0, durationMillis)
        try {
            player.seekTo(clamped.toLong(), MediaPlayer.SEEK_CLOSEST)
            this.positionMillis = clamped
        } catch (_: RuntimeException) {
            failPlayback()
        }
    }

    fun refreshProgress() {
        val player = mediaPlayer ?: return
        if (isPreparing) return
        try {
            positionMillis = player.currentPosition.coerceAtLeast(0)
            durationMillis = player.duration.coerceAtLeast(0)
            updatePlaying(player.isPlaying)
        } catch (_: RuntimeException) {
            failPlayback()
        }
    }

    fun stop() = releaseCurrent(clearSelection = true)

    fun release() = releaseCurrent(clearSelection = true)

    private fun start(clip: RecordingClip) {
        releaseCurrent(clearSelection = true)
        activeClip = clip
        isPreparing = true
        positionMillis = 0
        durationMillis = (clip.durationSeconds * 1_000.0)
            .roundToLong()
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()

        val nextPlayer = MediaPlayer()
        mediaPlayer = nextPlayer
        try {
            nextPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            nextPlayer.setDataSource(clip.file.absolutePath)
            loudnessEnhancer = runCatching {
                LoudnessEnhancer(nextPlayer.audioSessionId).apply {
                    setTargetGain(BOOST_GAIN_MILLIBELS)
                    enabled = boostEnabled
                }
            }.getOrNull()
            nextPlayer.setOnPreparedListener { preparedPlayer ->
                if (mediaPlayer !== preparedPlayer || activeClip?.file != clip.file) return@setOnPreparedListener
                isPreparing = false
                durationMillis = preparedPlayer.duration.coerceAtLeast(0)
                try {
                    preparedPlayer.start()
                    updatePlaying(true)
                } catch (_: RuntimeException) {
                    failPlayback()
                }
            }
            nextPlayer.setOnCompletionListener { completedPlayer ->
                if (mediaPlayer === completedPlayer) stop()
            }
            nextPlayer.setOnErrorListener { failedPlayer, _, _ ->
                if (mediaPlayer === failedPlayer) failPlayback()
                true
            }
            nextPlayer.prepareAsync()
        } catch (_: Exception) {
            failPlayback()
        }
    }

    private fun pause() {
        val player = mediaPlayer ?: return
        try {
            player.pause()
            positionMillis = player.currentPosition.coerceAtLeast(0)
            updatePlaying(false)
        } catch (_: RuntimeException) {
            failPlayback()
        }
    }

    private fun resume() {
        val player = mediaPlayer ?: return
        try {
            player.start()
            updatePlaying(true)
        } catch (_: RuntimeException) {
            failPlayback()
        }
    }

    private fun failPlayback() {
        releaseCurrent(clearSelection = true)
        errorMessage = "녹음을 재생할 수 없습니다. 파일을 확인해 주세요."
    }

    private fun releaseCurrent(clearSelection: Boolean) {
        val player = mediaPlayer
        mediaPlayer = null
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
        runCatching { player?.stop() }
        runCatching { player?.reset() }
        runCatching { player?.release() }
        updatePlaying(false)
        isPreparing = false
        if (clearSelection) {
            activeClip = null
            positionMillis = 0
            durationMillis = 0
        }
    }

    private fun updatePlaying(value: Boolean) {
        if (isPlaying == value) return
        isPlaying = value
        runCatching { onPlaybackStateChanged(value) }
    }

    private fun applyBoost() {
        runCatching { loudnessEnhancer?.enabled = boostEnabled }
    }

    private companion object {
        const val BOOST_GAIN_MILLIBELS = 600
    }
}

private sealed interface PendingMerge {
    data object Selected : PendingMerge
    data object Today : PendingMerge
}

private fun List<String>.toggled(value: String): List<String> =
    if (value in this) this - value else this + value

@Composable
private fun recordingsBackground(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.linearGradient(
        colors = listOf(
            lerp(colors.background, colors.primary, 0.18f),
            lerp(colors.background, colors.surface, 0.72f),
            colors.background,
        ),
    )
}

private fun sessionTitle(session: RecordingSessionGroup): String {
    val zone = ZoneId.systemDefault()
    val date = session.startedAt.atZone(zone).toLocalDate()
    return when (date) {
        LocalDate.now(zone) -> "오늘 잠자리"
        LocalDate.now(zone).minusDays(1) -> "어제 잠자리"
        else -> SESSION_DATE_FORMATTER.format(session.startedAt.atZone(zone))
    }
}

private fun sessionTimeRange(session: RecordingSessionGroup): String {
    val zone = ZoneId.systemDefault()
    val start = session.startedAt.atZone(zone)
    val end = session.endedAt.atZone(zone)
    val suffix = if (start.toLocalDate() == end.toLocalDate()) "" else "다음 날 "
    return "${CLOCK_TIME_FORMATTER.format(start)}–$suffix${CLOCK_TIME_FORMATTER.format(end)}"
}

private fun sessionAccessibilityLabel(session: RecordingSessionGroup): String = buildString {
    if (session.isInferred) append("시간 추정, ")
    append(sessionTitle(session))
    append(", ${sessionTimeRange(session)}, 녹음 ${session.clips.size}개")
    append(", 화들짝 ${session.startleEvents.size}회, 총 ")
    append(formatDuration(session.totalDurationSeconds))
}

private fun formatClockTime(instant: Instant): String =
    CLOCK_TIME_FORMATTER.format(instant.atZone(ZoneId.systemDefault()))

private fun formatRecordingTime(clip: RecordingClip): String =
    RECORDING_TIME_FORMATTER.format(clip.createdAt.atZone(ZoneId.systemDefault()))

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.coerceAtLeast(0.0).roundToLong()
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val remainingSeconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
    }
}

private val RECORDING_TIME_FORMATTER = DateTimeFormatter.ofPattern("M월 d일 HH:mm:ss", Locale.KOREAN)
private val SESSION_DATE_FORMATTER = DateTimeFormatter.ofPattern("M월 d일 E", Locale.KOREAN)
private val CLOCK_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
private const val MILLIS_PER_SECOND = 1_000.0
private const val PROGRESS_UPDATE_MILLIS = 100L
