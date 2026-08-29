@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.armsone.stand.ui

import androidx.annotation.RawRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsone.stand.R
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockVisualPolicy
import com.armsone.stand.ui.components.flipTextSplitMask
import com.armsone.stand.ui.components.standFocusable
import com.armsone.stand.ui.components.standPanelSurface
import com.armsone.stand.ui.theme.fontFamily
import com.armsone.stand.ui.theme.fontWeight

@Composable
fun ClockFontOptionsScreen(
    selectedFont: ClockFontChoice,
    onFontSelected: (ClockFontChoice) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accessibilityText = LocalDensity.current.fontScale >= 1.3f
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("시계 글꼴") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.standFocusable(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (accessibilityText) 1 else 3),
            modifier = Modifier
                .fillMaxSize()
                .background(settingsRouteBackground())
                .padding(padding)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ClockFontChoice.entries, key = { it.name }) { font ->
                ClockFontOptionTile(
                    font = font,
                    selected = selectedFont == font,
                    onClick = { onFontSelected(font) },
                )
            }
        }
    }
}

@Composable
private fun ClockFontOptionTile(
    font: ClockFontChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .standFocusable(shape = RoundedCornerShape(15.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${font.displayName} 플립시계 미리보기${if (selected) ", 선택됨" else ""}"
                this.selected = selected
                role = Role.RadioButton
            },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                Color.White.copy(alpha = 0.05f)
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
            } else {
                Color.White.copy(alpha = 0.07f)
            },
        ),
        shape = RoundedCornerShape(15.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .standPanelSurface(
                        isDimmed = false,
                        cornerRadius = 12.dp,
                        splitGap = 2.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "12:34",
                    color = Color.White.copy(alpha = 0.88f),
                    fontFamily = font.fontFamily(),
                    fontSize = 23.sp,
                    fontWeight = font.fontWeight(),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .offset(y = ClockVisualPolicy.verticalOffset(font, 23f).dp)
                        .flipTextSplitMask(2.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = font.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun FontLicensesScreen(
    onOpenLicense: (ClockFontChoice) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bundledFonts = ClockFontChoice.entries.filterNot {
        it == ClockFontChoice.SYSTEM_ROUNDED
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("폰트 저작권") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.standFocusable(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(settingsRouteBackground())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { SectionTitle("내장 폰트 저작권") }
            item {
                LicenseBody(
                    "S.tand는 시계 표시를 위해 HanClip에서 검증해 보관한 프리텐다드, 카카오 Big Sans, 나눔고딕, 태나다, 검은고딕, 도현, 페이퍼로지 Bold, 넥슨 Lv.1 고딕, Poppins의 원본 서체 파일을 수정하지 않고 포함합니다.",
                )
            }
            item {
                LicenseBody(
                    "프리텐다드, 카카오 Big Sans, 나눔고딕, 태나다, 검은고딕, 도현, 페이퍼로지와 Poppins는 SIL Open Font License 1.1에 따라 앱·소프트웨어 번들 및 임베딩이 허용됩니다. 서체 파일 자체를 단독 판매하지 않으며, 각 저작권 고지와 라이선스 전문을 앱 번들에 함께 보관합니다.",
                )
            }
            item {
                LicenseBody(
                    "페이퍼로지는 제작자의 공식 저장소에서 배포한 1.001 Bold 원본이며, Poppins는 Google Fonts 공식 저장소의 Regular 원본입니다. 넥슨 Lv.1 고딕의 저작권은 NEXON Korea에 있으며 공식 이용 조건에 따라 원본 파일과 저작권 안내를 함께 번들합니다.",
                )
            }
            item { SectionTitle("라이선스 전문") }
            items(bundledFonts.size, key = { bundledFonts[it].name }) { index ->
                val font = bundledFonts[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .standFocusable(shape = RoundedCornerShape(12.dp))
                        .clickable { onOpenLicense(font) }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(font.displayName, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            }
            item {
                LicenseBody(
                    "시스템 둥근체는 Android 시스템 서체이며 S.tand 앱 번들에 별도 서체 파일로 포함하지 않습니다.",
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun FontLicenseDetailScreen(
    font: ClockFontChoice,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val license = remember(font) {
        font.licenseResource()?.let { resource ->
            runCatching {
                context.resources.openRawResource(resource).bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: "라이선스 전문을 불러올 수 없습니다."
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(font.displayName) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.standFocusable(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(settingsRouteBackground())
                .padding(padding),
        ) {
            item {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = license,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LicenseBody(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun settingsRouteBackground(): Brush = Brush.linearGradient(
    listOf(
        Color(0xFF1D1614),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
        Color(0xFF161313),
    ),
)

@RawRes
internal fun ClockFontChoice.licenseResource(): Int? = when (this) {
    ClockFontChoice.SYSTEM_ROUNDED -> null
    ClockFontChoice.PRETENDARD -> R.raw.pretendard_license
    ClockFontChoice.KAKAO_BIG_SANS -> R.raw.kakao_big_sans_ofl
    ClockFontChoice.NANUM_GOTHIC -> R.raw.nanum_gothic_ofl
    ClockFontChoice.TENADA -> R.raw.tenada_license
    ClockFontChoice.BLACK_HAN_SANS -> R.raw.black_han_sans_ofl
    ClockFontChoice.DO_HYEON -> R.raw.do_hyeon_ofl
    ClockFontChoice.PAPERLOGY_BOLD -> R.raw.paperlogy_ofl
    ClockFontChoice.NEXON_LV1_GOTHIC -> R.raw.nexon_lv1_gothic_license
    ClockFontChoice.POPPINS -> R.raw.poppins_ofl
}
