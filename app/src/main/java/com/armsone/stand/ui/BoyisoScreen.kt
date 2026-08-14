package com.armsone.stand.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsone.stand.BuildConfig
import com.armsone.stand.R
import com.armsone.stand.boyiso.BoyisoConfiguration
import com.armsone.stand.boyiso.BoyisoQrCode
import com.armsone.stand.boyiso.BoyisoRole
import com.armsone.stand.boyiso.BoyisoState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoyisoScreen(
    state: BoyisoState,
    invitationUri: String?,
    onUpdateConfiguration: (BoyisoConfiguration) -> Unit,
    onCreateRoom: () -> Unit,
    onScanInvitation: () -> Unit,
    onShareInvitation: () -> Unit,
    onStart: () -> Unit,
    onLeaveRoom: () -> Unit,
    onTokTok: () -> Unit,
    onBack: () -> Unit,
) {
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var connectionDetailsExpanded by remember { mutableStateOf(false) }
    var nameEditorOpen by remember { mutableStateOf(false) }
    var nameDraft by remember(state.configuration.deviceName) {
        mutableStateOf(state.configuration.deviceName)
    }

    LaunchedEffect(state.configuration.roomId, state.configuration.roomKey) {
        if (state.configuration.hasRoom && !state.running) onStart()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("보이소")
                        Text(
                            "BOISO · 보이는 소리",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.configuration.hasRoom) {
                BoyisoCard {
                    Text("1. 나는 누구인가요?", style = MaterialTheme.typography.headlineSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BoyisoRole.entries.forEach { role ->
                            val selected = state.configuration.role == role
                            if (selected) {
                                Button(
                                    onClick = {
                                        onUpdateConfiguration(state.configuration.copy(role = role))
                                    },
                                    modifier = Modifier.weight(1f).height(64.dp),
                                ) {
                                    Text(role.title, style = MaterialTheme.typography.titleMedium)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onUpdateConfiguration(state.configuration.copy(role = role))
                                    },
                                    modifier = Modifier.weight(1f).height(64.dp),
                                ) {
                                    Text(role.title, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.configuration.deviceName,
                        onValueChange = {
                            onUpdateConfiguration(state.configuration.copy(deviceName = it))
                        },
                        label = { Text("2. 내 이름") },
                        placeholder = { Text("예: 엄마, 거실 태블릿") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("3. 공간 연결", style = MaterialTheme.typography.titleLarge)
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val createAction = {
                            validationMessage = if (state.configuration.deviceName.isBlank()) {
                                "내 이름을 입력해 주세요."
                            } else {
                                null
                            }
                            if (validationMessage == null) onCreateRoom()
                        }
                        val joinAction = {
                            validationMessage = if (state.configuration.deviceName.isBlank()) {
                                "내 이름을 입력해 주세요."
                            } else {
                                null
                            }
                            if (validationMessage == null) onScanInvitation()
                        }
                        if (maxWidth < 600.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                BoyisoConnectionChoice(
                                    title = "공간 만들기",
                                    description = "QR을 만들고 사람을 기다려요",
                                    icon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                                    primary = true,
                                    onClick = createAction,
                                )
                                BoyisoConnectionChoice(
                                    title = "공간 입장",
                                    description = "카메라로 QR을 찍고 들어가요",
                                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                                    primary = false,
                                    onClick = joinAction,
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BoyisoConnectionChoice(
                                    title = "공간 만들기",
                                    description = "QR을 만들고 사람을 기다려요",
                                    icon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                                    primary = true,
                                    onClick = createAction,
                                    modifier = Modifier.weight(1f),
                                )
                                BoyisoConnectionChoice(
                                    title = "공간 입장",
                                    description = "카메라로 QR을 찍고 들어가요",
                                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                                    primary = false,
                                    onClick = joinAction,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    validationMessage?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                if (state.running) {
                    Button(
                        onClick = onTokTok,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) {
                        Icon(Icons.Default.ChildCare, contentDescription = null)
                        Text(" 톡톡 보내기", style = MaterialTheme.typography.titleLarge)
                    }
                }

                BoyisoCard {
                    Text("공간에 연결됨", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        when {
                            state.devices.isNotEmpty() -> "다른 사람과 함께 연결되어 있습니다."
                            state.hadConnectedDevice -> "연결이 끊어져 다시 찾고 있습니다."
                            state.configuration.canInvite -> "사람을 기다리고 있습니다."
                            else -> "공간에 들어왔습니다. 다른 사람을 찾고 있습니다."
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "내 이름: ${state.configuration.deviceName}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (nameEditorOpen) {
                        OutlinedTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it },
                            label = { Text("내 이름") },
                            placeholder = { Text("예: 엄마, 거실 태블릿") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    nameDraft = state.configuration.deviceName
                                    nameEditorOpen = false
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("취소") }
                            Button(
                                onClick = {
                                    val newName = nameDraft.trim()
                                    if (newName.isNotEmpty()) {
                                        onUpdateConfiguration(state.configuration.copy(deviceName = newName))
                                        nameEditorOpen = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("저장") }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { nameEditorOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("내 이름 수정") }
                    }
                    OutlinedButton(
                        onClick = { connectionDetailsExpanded = !connectionDetailsExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            if (connectionDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                        )
                        Text(if (connectionDetailsExpanded) " 연결 자세히 닫기" else " 연결 자세히 보기")
                    }
                    if (connectionDetailsExpanded) {
                        Text(
                            "Wi-Fi ${state.lanConnectionCount} · Bluetooth ${state.bluetoothConnectionCount}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.running) BoyisoPeopleSection(state)

                invitationUri?.let { uri ->
                    BoyisoCard {
                        val qr = remember(uri) { BoyisoQrCode.create(uri) }
                        Text("우리 공간 QR코드", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "누구나 이 QR을 찍고 우리 공간에 들어올 수 있어요.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "보이소 초대 QR",
                            modifier = Modifier.size(230.dp).align(Alignment.CenterHorizontally),
                        )
                        OutlinedButton(
                            onClick = onShareInvitation,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text(" QR 사진 보내기")
                        }
                    }
                }

            }

            BoyisoCard {
                Text("알아 두세요", fontWeight = FontWeight.Bold)
                Text(
                    "보이소는 직접 돌봄이나 의료기기를 대신하지 않습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.configuration.hasRoom) {
                OutlinedButton(
                    onClick = onLeaveRoom,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Text("공간에서 나오기", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BoyisoConnectionChoice(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Text(" $title", style = MaterialTheme.typography.titleMedium)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().heightIn(min = 82.dp),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().heightIn(min = 82.dp),
        ) { content() }
    }
}

private data class BoyisoParticipant(
    val id: String,
    val name: String,
    val role: BoyisoRole,
    val isMe: Boolean,
    val batteryPercent: Int?,
    val status: String,
    val transportPaths: Set<String>,
)

private fun deduplicatedParticipants(
    participants: List<BoyisoParticipant>,
): List<BoyisoParticipant> = participants
    .groupBy(BoyisoParticipant::id)
    .values
    .map { matching ->
        val mine = matching.firstOrNull(BoyisoParticipant::isMe)
        val primary = mine ?: matching.maxByOrNull { participant ->
            when (participant.status) {
                "감지 중" -> 2
                "연결됨" -> 1
                else -> 0
            }
        } ?: matching.first()
        primary.copy(
            isMe = matching.any(BoyisoParticipant::isMe),
            batteryPercent = matching.firstNotNullOfOrNull(BoyisoParticipant::batteryPercent),
            status = when {
                matching.any { it.status == "감지 중" } -> "감지 중"
                matching.any { it.status == "연결됨" } -> "연결됨"
                else -> primary.status
            },
            transportPaths = matching.flatMap(BoyisoParticipant::transportPaths).toSet(),
        )
    }
    .sortedWith(
        compareByDescending<BoyisoParticipant>(BoyisoParticipant::isMe)
            .thenBy { it.name.lowercase() },
    )

@Composable
private fun BoyisoPeopleSection(state: BoyisoState) {
    val participants = deduplicatedParticipants(buildList {
        add(
            BoyisoParticipant(
                id = state.localDeviceId.ifBlank { "local:${state.configuration.role.wireValue}" },
                name = state.configuration.deviceName,
                role = state.configuration.role,
                isMe = true,
                batteryPercent = null,
                status = when (state.configuration.role) {
                    BoyisoRole.VIEWER -> "연결됨"
                    BoyisoRole.SPEAKER -> if (state.microphoneMonitoring) "감지 중" else "대기 중"
                },
                transportPaths = setOf("이 기기"),
            ),
        )
        state.devices.forEach { device ->
            add(
                BoyisoParticipant(
                    id = device.id,
                    name = device.name,
                    role = device.role,
                    isMe = device.id == state.localDeviceId,
                    batteryPercent = device.batteryPercent,
                    status = when (device.role) {
                        BoyisoRole.VIEWER -> "연결됨"
                        BoyisoRole.SPEAKER -> if (device.monitoring) "감지 중" else "대기 중"
                    },
                    transportPaths = device.transportPaths,
                ),
            )
        }
    })
    val viewers = participants.filter { it.role == BoyisoRole.VIEWER }
    val speakers = participants.filter { it.role == BoyisoRole.SPEAKER }
    val hasDuplicateNames = participants
        .groupBy { it.role to it.name.trim().lowercase() }
        .any { (_, matching) -> matching.size > 1 }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .semantics {
                                contentDescription = "공간에 있는 사람, 나를 포함해 총 ${participants.size}명"
                                heading()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                        Column {
                            Text(
                                "공간에 있는 사람",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "나를 포함해 총 ${participants.size}명",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    BoyisoParticipantRoleSection(BoyisoRole.VIEWER, viewers)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f),
                    )
                    BoyisoParticipantRoleSection(BoyisoRole.SPEAKER, speakers)
                }
            }
        }
        if (hasDuplicateNames) {
            Text(
                "같은 이름을 쓰는 기기가 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        state.issueMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun BoyisoParticipantRoleSection(
    role: BoyisoRole,
    participants: List<BoyisoParticipant>,
) {
    val viewer = role == BoyisoRole.VIEWER
    val accent = if (viewer) Color(0xFF176B9B) else Color(0xFFAA5314)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = "${role.title} ${participants.size}명"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (viewer) Icons.Default.Visibility else Icons.Default.GraphicEq,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Text(role.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${participants.size}명",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (participants.isEmpty()) {
            Text(
                "아직 없음",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 46.dp, vertical = 12.dp),
            )
        } else {
            participants.forEachIndexed { index, participant ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 46.dp, end = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f),
                    )
                }
                BoyisoParticipantRow(participant = participant, role = role, accent = accent)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BoyisoParticipantRow(
    participant: BoyisoParticipant,
    role: BoyisoRole,
    accent: Color,
) {
    val paths = participant.transportPaths
        .filterNot { participant.isMe && it == "이 기기" }
        .sortedBy(String::displayTransportOrder)
    val accessibilityText = buildString {
        append(participant.name)
        if (participant.isMe) append(", 이 기기")
        append(", ${role.title}, ${participant.status}")
        participant.batteryPercent?.let { append(", 배터리 ${it}퍼센트") }
        if (paths.isNotEmpty()) {
            append(", ${paths.joinToString(" 및 ") { it.displayTransportName() }}")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = accessibilityText },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                participant.name,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(participant.status, style = MaterialTheme.typography.bodyMedium, color = accent)
                if (participant.isMe) {
                    Text("이 기기", style = MaterialTheme.typography.bodyMedium)
                }
                paths.forEach { path ->
                    Text(
                        path.displayTransportName(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        participant.batteryPercent?.let {
            Text(
                "$it%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun String.displayTransportName(): String = when (this) {
    "LAN" -> "Wi‑Fi"
    "BLE" -> "Bluetooth"
    "INTERNET", "인터넷" -> "인터넷"
    else -> this
}

private fun String.displayTransportOrder(): Int = when (this) {
    "이 기기" -> 0
    "LAN" -> 1
    "BLE" -> 2
    "INTERNET", "인터넷" -> 3
    else -> 4
}

@Composable
private fun BoyisoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
