package com.armsone.stand

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.armsone.stand.model.RadioShareImportPolicy
import com.armsone.stand.ui.theme.STandTheme

class RadioShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedUrl = RadioShareImportPolicy.validatedUrlOrNull(
            intent?.getStringExtra(Intent.EXTRA_TEXT),
        )
        setContent {
            STandTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("S.tand 라디오", style = MaterialTheme.typography.headlineSmall)
                        if (sharedUrl == null) {
                            Text("웹페이지가 아닌 합법적인 HTTPS 직접 스트림 주소 한 개를 공유해 주세요.")
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(
                                onClick = ::finish,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("닫기") }
                        } else {
                            Text("웹페이지가 아니라 라디오가 직접 재생되는 합법적인 HTTPS 주소여야 합니다.")
                            Text(sharedUrl, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedButton(
                                    onClick = ::finish,
                                    modifier = Modifier.weight(1f),
                                ) { Text("취소") }
                                Button(
                                    onClick = { openRadioDraft(sharedUrl) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("라디오 주소로 가져오기") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openRadioDraft(url: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_RADIO_SHARE_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
