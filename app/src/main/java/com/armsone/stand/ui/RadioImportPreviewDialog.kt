package com.armsone.stand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armsone.stand.model.InternetRadioConfiguration
import com.armsone.stand.model.RadioImportPreview
import com.armsone.stand.ui.components.standFocusable

@Composable
fun RadioImportPreviewDialog(
    preview: RadioImportPreview,
    onConfirmAdd: (List<InternetRadioConfiguration>) -> Unit,
    onConfirmReplace: (List<InternetRadioConfiguration>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "라디오 채널 가져오기",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val summaryText = when {
                    preview.isAllDuplicates -> "가져온 채널이 이미 모두 등록되어 있습니다."
                    preview.canAddAll -> "가져온 채널 ${preview.newChannels.size}개를 등록합니다."
                    preview.isFull -> "저장 슬롯이 가득 찼습니다. 계속하면 기존 채널 전체가 가져온 채널로 교체됩니다."
                    else -> "빈 슬롯이 ${preview.availableSlots}개 남았습니다. 일부만 추가하거나 기존 채널 전체를 교체할 수 있습니다."
                }
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (preview.hasUnencryptedStreams) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF422006).copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "암호화되지 않은 방송 주소(HTTP)가 포함되어 있습니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFDE68A),
                        )
                    }
                }

                Text(
                    "가져올 채널 목록 (${preview.importedChannels.size}개)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(preview.importedChannels, key = { it.id }) { channel ->
                            val isDuplicate = preview.duplicateChannels.any { it.id == channel.id || it.streamUrl.equals(channel.streamUrl, ignoreCase = true) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDuplicate) Color.White.copy(alpha = 0.03f) else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isDuplicate) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            channel.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        if (isDuplicate) {
                                            Text(
                                                "(이미 등록됨)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (channel.isUnencrypted) {
                                            Text(
                                                "HTTP",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFFBBF24),
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    Text(
                                        channel.streamUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (preview.isAllDuplicates) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.standFocusable(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Text("확인")
                    }
                } else if (preview.canAddAll) {
                    Button(
                        onClick = { onConfirmAdd(preview.newChannels) },
                        modifier = Modifier.standFocusable(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Text("채널 추가")
                    }
                } else if (preview.isFull) {
                    Button(
                        onClick = { onConfirmReplace(preview.importedChannels) },
                        modifier = Modifier.standFocusable(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Text("기존 채널 전체 교체")
                    }
                } else {
                    // Partial capacity: offer add available or replace
                    Button(
                        onClick = { onConfirmAdd(preview.addableChannels) },
                        modifier = Modifier.standFocusable(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Text("${preview.addableChannels.size}개만 추가")
                    }
                    Button(
                        onClick = { onConfirmReplace(preview.importedChannels) },
                        modifier = Modifier.standFocusable(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Text("기존 채널 전체 교체")
                    }
                }
            }
        },
        dismissButton = {
            if (!preview.isAllDuplicates) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.standFocusable(shape = RoundedCornerShape(12.dp)),
                ) {
                    Text("취소")
                }
            }
        },
    )
}
