@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.armsone.stand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.armsone.stand.model.InternetRadioConfiguration

@Composable
fun InternetRadioScreen(
    configuration: InternetRadioConfiguration?,
    initialUrl: String? = null,
    onSave: (String, String) -> String?,
    onDelete: () -> Unit,
    onOpenBrowser: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var name by remember(configuration, initialUrl) {
        mutableStateOf(configuration?.displayName.orEmpty())
    }
    var url by remember(configuration, initialUrl) {
        mutableStateOf(initialUrl ?: configuration?.streamUrl.orEmpty())
    }
    var error by remember(configuration, initialUrl) { mutableStateOf<String?>(null) }
    var confirmsDeletion by remember(configuration) { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (configuration == null) "채널 추가" else "채널 수정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        error = onSave(name, url)
                        if (error == null) onBack()
                    }) { Text("저장") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("채널 정보", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30); error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("이름 (선택)") },
                placeholder = { Text("비워 두면 인터넷 라디오로 저장됩니다") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            val isHttp = url.trim().startsWith("http://", ignoreCase = true)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.take(2_048); error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("주소") },
                placeholder = { Text("https://… 또는 http://…") },
                singleLine = true,
                isError = error != null,
                supportingText = {
                    when {
                        error != null -> Text(error!!)
                        isHttp -> Text("암호화되지 않은 방송 주소", color = androidx.compose.ui.graphics.Color(0xFFEAB308))
                    }
                },
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedButton(
                onClick = {
                    clipboardManager.getText()?.text?.let { pasted ->
                        url = pasted.take(2_048)
                        error = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("복사한 주소 붙여넣기")
            }
            Text(
                "직접 이용 권한을 확인한 합법적인 HTTP 또는 HTTPS 스트림 주소를 등록해 주세요. 이름은 최대 30자, 주소는 최대 2,048자로 저장됩니다.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = onOpenBrowser,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Public, contentDescription = null)
                Text("웹에서 주소 찾기", modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                "브라우저에서 이용 권한이 있는 주소를 직접 복사한 뒤 이 화면으로 돌아와 붙여넣어 주세요. 웹페이지 주소는 자동으로 입력되지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("재생 중 동작", style = MaterialTheme.typography.titleMedium)
            Text("소리 감지와 수면 녹음은 일시 중지됩니다.")
            Text("기기 움직임 감지는 계속됩니다.")
            if (configuration != null) {
                OutlinedButton(
                    onClick = { confirmsDeletion = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("이 채널 삭제") }
            }
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
    if (confirmsDeletion) {
        AlertDialog(
            onDismissRequest = { confirmsDeletion = false },
            title = { Text("${configuration?.displayName.orEmpty()}을 삭제할까요?") },
            text = { Text("삭제한 채널 주소는 되돌릴 수 없습니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmsDeletion = false
                        onDelete()
                        onBack()
                    },
                ) { Text("채널 삭제") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmsDeletion = false }) { Text("취소") }
            },
        )
    }
}
