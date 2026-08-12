package com.armsone.stand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.armsone.stand.update.AppUpdateState

@Composable
fun AppUpdateDialog(
    state: AppUpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    when (state) {
        AppUpdateState.Idle,
        AppUpdateState.Checking,
        -> Unit

        is AppUpdateState.Available -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("새 버전이 있습니다") },
            text = {
                Text(
                    state.message
                        ?: "GitHub에서 S.tand ${state.release.tagName.removePrefix("android-v")} 버전을 받습니다. 기존 설정과 녹음은 유지됩니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = onDownload) { Text("업데이트") }
            },
            dismissButton = {
                TextButton(onClick = onLater) { Text("나중에") }
            },
        )

        is AppUpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("업데이트 받는 중") },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator()
                    Text("안전한 APK인지 확인한 뒤 설치 화면을 엽니다.")
                }
            },
            confirmButton = {},
        )

        is AppUpdateState.Ready -> AlertDialog(
            onDismissRequest = onLater,
            title = { Text("업데이트 준비 완료") },
            text = { Text("Android 설치 화면에서 설치를 눌러 주세요.") },
            confirmButton = {
                TextButton(onClick = onInstall) { Text("설치 화면 열기") }
            },
            dismissButton = {
                TextButton(onClick = onLater) { Text("나중에") }
            },
        )
    }
}
