package com.armsone.stand.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.armsone.stand.BuildConfig
import com.armsone.stand.R
import com.armsone.stand.boyiso.BoyisoConfiguration
import com.armsone.stand.boyiso.BoyisoQrCode
import com.armsone.stand.boyiso.BoyisoRole
import com.armsone.stand.boyiso.BoyisoState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    LaunchedEffect(state.configuration.roomId, state.configuration.roomKey) {
        if (state.configuration.hasRoom && !state.running) onStart()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("보이소") },
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
                    Text("3. 어떻게 할까요?", style = MaterialTheme.typography.titleLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                validationMessage = if (state.configuration.deviceName.isBlank()) {
                                    "내 이름을 입력해 주세요."
                                } else {
                                    null
                                }
                                if (validationMessage == null) onCreateRoom()
                            },
                            modifier = Modifier.weight(1f).height(72.dp),
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null)
                            Text(" 공간 만들기", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                validationMessage = if (state.configuration.deviceName.isBlank()) {
                                    "내 이름을 입력해 주세요."
                                } else {
                                    null
                                }
                                if (validationMessage == null) onScanInvitation()
                            },
                            modifier = Modifier.weight(1f).height(72.dp),
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Text(" 공간 입장", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Text(
                        "공간 만들기: 사람을 기다려요.  공간 입장: 카메라로 QR을 찍어요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                        Icon(Icons.Default.Pets, contentDescription = null)
                        Text(" 톡톡 보내기", style = MaterialTheme.typography.titleLarge)
                    }
                }

                BoyisoCard {
                    Text("공간에 연결됨", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${state.configuration.deviceName} · ${state.configuration.role.title}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        when {
                            state.devices.isNotEmpty() -> "다른 사람과 함께 연결되어 있습니다."
                            state.hadConnectedDevice -> "연결이 끊어져 다시 찾고 있습니다."
                            state.configuration.canInvite -> "사람을 기다리고 있습니다."
                            else -> "공간에 들어왔습니다. 다른 사람을 찾고 있습니다."
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                invitationUri?.let { uri ->
                    BoyisoCard {
                        val qr = remember(uri) { BoyisoQrCode.create(uri) }
                        Text("우리 공간 QR코드", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "이 QR로 같은 공간에 들어올 수 있어요.",
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

                if (state.running) {
                    BoyisoCard {
                        Text("같은 공간안에 있는 사람", style = MaterialTheme.typography.titleLarge)
                        if (state.devices.isEmpty()) {
                            Text(
                                if (state.configuration.canInvite) {
                                    "QR을 찍고 들어올 사람을 기다리고 있습니다."
                                } else {
                                    "같은 공간의 사람을 찾고 있습니다."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.devices.forEach { device ->
                                HorizontalDivider()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            device.role.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    device.batteryPercent?.let { Text("$it%") }
                                }
                            }
                        }
                        state.issueMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
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
