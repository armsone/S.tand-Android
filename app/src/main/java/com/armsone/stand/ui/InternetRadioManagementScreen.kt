@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.armsone.stand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.armsone.stand.model.AppSettings
import com.armsone.stand.model.InternetRadioConfiguration
import com.armsone.stand.platform.InternetRadioState

@Composable
fun InternetRadioManagementScreen(
    state: StandUiState,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onOpenBrowser: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channels = state.settings.internetRadioChannels
    var pendingDeletion by remember { mutableStateOf<InternetRadioConfiguration?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("인터넷 라디오") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "완료")
                    }
                },
                actions = {
                    if (channels.size < AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT) {
                        IconButton(onClick = onAdd) {
                            Icon(Icons.Default.Add, contentDescription = "채널 추가")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Text(
                    text = "등록한 채널마다 홈 음악 스트립에 고정 카드가 배정됩니다",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (channels.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("저장한 채널이 없습니다", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "직접 이용할 수 있는 HTTPS 스트림 주소를 추가해 주세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onAdd) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("첫 채널 추가")
                        }
                    }
                }
            } else {
                items(channels.size, key = { channels[it].id }) { index ->
                    val channel = channels[index]
                    val status = channelStatus(state.internetRadioState, channel)
                    ListItem(
                        modifier = Modifier.semantics {
                            contentDescription = "${channel.displayName}, $status, ${index + 1}번째 홈 채널"
                        },
                        headlineContent = { Text(channel.displayName) },
                        supportingContent = { Text(status) },
                        leadingContent = {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                            )
                        },
                        trailingContent = {
                            Row {
                                if (index > 0) {
                                    IconButton(onClick = { onMove(channel.id, index - 1) }) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "${channel.displayName} 위로 이동",
                                        )
                                    }
                                }
                                if (index < channels.size - 1) {
                                    IconButton(onClick = { onMove(channel.id, index + 1) }) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "${channel.displayName} 아래로 이동",
                                        )
                                    }
                                }
                                IconButton(onClick = { onEdit(channel.id) }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "${channel.displayName} 수정",
                                    )
                                }
                                IconButton(onClick = { pendingDeletion = channel }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "${channel.displayName} 삭제",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                        tonalElevation = 0.dp,
                    )
                    TextButton(
                        onClick = { onToggle(channel.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) { Text(if (status == "재생 중") "재생 중지" else "재생") }
                    HorizontalDivider()
                }
            }
            item {
                TextButton(
                    onClick = onOpenBrowser,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Public, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("웹에서 주소 찾기")
                }
            }
            item {
                Text(
                    "브라우저는 주소를 자동으로 감지하거나 저장하지 않습니다. 주소를 직접 복사해 채널 편집 화면에 붙여넣어 주세요.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    pendingDeletion?.let { channel ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("${channel.displayName}을 삭제할까요?") },
            text = { Text("삭제한 채널 주소는 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletion = null
                        onDelete(channel.id)
                    },
                ) { Text("채널 삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) { Text("취소") }
            },
        )
    }
}

private fun channelStatus(
    state: InternetRadioState,
    channel: InternetRadioConfiguration,
): String = when (state) {
    is InternetRadioState.Loading -> if (state.channelID == channel.id) "연결 중" else channel.streamUrl
    is InternetRadioState.Reconnecting -> if (state.channelID == channel.id) "자동 재연결 중" else channel.streamUrl
    is InternetRadioState.Playing -> if (state.channelID == channel.id) "재생 중" else channel.streamUrl
    is InternetRadioState.Failed -> if (state.channelID == channel.id) "연결 실패" else channel.streamUrl
    InternetRadioState.Idle -> channel.streamUrl
}
