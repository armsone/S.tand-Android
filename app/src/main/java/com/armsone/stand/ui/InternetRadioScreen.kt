@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.armsone.stand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.armsone.stand.model.InternetRadioConfiguration

@Composable
fun InternetRadioScreen(
    configuration: InternetRadioConfiguration?,
    initialUrl: String? = null,
    onSave: (String, String) -> String?,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(configuration, initialUrl) {
        mutableStateOf(configuration?.displayName.orEmpty())
    }
    var url by remember(configuration, initialUrl) {
        mutableStateOf(initialUrl ?: configuration?.streamUrl.orEmpty())
    }
    var error by remember(configuration, initialUrl) { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("인터넷 라디오") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("라디오 패널 설정", style = MaterialTheme.typography.titleLarge)
            Text(
                "안전한 HTTPS 스트림 주소 하나를 기기에만 저장합니다. 재생 중에는 수면 소리 감지와 녹음이 일시 중지됩니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30); error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("표시 이름") },
                placeholder = { Text("인터넷 라디오") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.take(2_048); error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("HTTPS 스트림 주소") },
                placeholder = { Text("https://…") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (configuration != null) {
                    OutlinedButton(
                        onClick = { onDelete(); onBack() },
                        modifier = Modifier.weight(1f),
                    ) { Text("삭제") }
                }
                Button(
                    onClick = {
                        error = onSave(name, url)
                        if (error == null) onBack()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("저장") }
            }
        }
    }
}
