# Google TV / Android TV 구현 및 운영 가이드

S.tand Android의 Google TV 및 Android TV(10-foot UI) 지원 아키텍처, 리모컨 인터랙션, 플랫폼 정책 및 검증 가이드입니다.

---

## 1. 공식 Android Developers 레퍼런스

- [TV 앱 생성과 실행](https://developer.android.com/training/tv/get-started/create)
- [TV 앱 품질 체크리스트](https://developer.android.com/docs/quality-guidelines/tv-app-quality)
- [TV 앱 아이콘과 배너 규격](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines)
- [TV 레이아웃 및 10-foot 디자인 가이드 (Design for Android TV)](https://developer.android.com/design/ui/tv)
- [TV 탐색 및 D-pad 포커스 처리](https://developer.android.com/training/tv/get-started/navigation)
- [Compose 포커스 처리](https://developer.android.com/develop/ui/compose/touch-input/focus)

---

## 2. 매니페스트 및 런처 배너 구성

단일 APK로 휴대폰·태블릿·폴더블 및 Android TV/Google TV를 모두 지원하기 위해 매니페스트를 다음과 같이 구성했습니다.

### 2.1 하드웨어 및 소프트웨어 피처 선언
TV 기기에는 터치스크린, 카메라, 플래시, GPS, 각종 센서가 없으므로 `required="false"`로 선언하여 스토어 배포 및 기기 설치가 필터링되지 않도록 합니다.

```xml
<!-- TV 필수 소프트웨어/하드웨어 피처 non-required 선언 -->
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.flash" android:required="false" />
<uses-feature android:name="android.hardware.sensor.light" android:required="false" />
<uses-feature android:name="android.hardware.sensor.accelerometer" android:required="false" />
```

### 2.2 TV 배너 및 런처 인텐트 필터
- TV 홈 런처에 표시되는 배너 이미지는 16:9 비율이어야 합니다. 이 앱은 xhdpi 폴더에 공식 최소 규격인 `320×180 px` 배너(`@drawable/tv_banner`)를 등록했습니다. mdpi/hdpi/xxhdpi/xxxhdpi별 권장 크기는 각각 160×90, 240×135, 480×270, 640×360 px입니다.
- 배너 이미지 안에는 앱 이름을 포함하고, 앱 아이콘은 xhdpi 기준 최소 160×160 px 이상을 제공합니다.
- `MainActivity`에 `LEANBACK_LAUNCHER` 카테고리를 함께 등록하여 TV 런처에서 독립된 타일로 실행됩니다.

```xml
<activity
    android:name=".MainActivity"
    android:banner="@drawable/tv_banner"
    android:configChanges="keyboard|keyboardHidden|orientation|screenLayout|screenSize|smallestScreenSize|uiMode"
    android:exported="true"
    android:resizeableActivity="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity>
```

---

## 3. 플랫폼 TV 감지 및 정책 (`TvUiModePolicy`)

순수 정책 객체 [`TvUiModePolicy`](../app/src/main/java/com/armsone/stand/model/TvUiModePolicy.kt)를 통해 Android 플랫폼의 `Configuration.UI_MODE_TYPE_TELEVISION`을 감지하고 하드웨어 의존 기능을 안전하게 제어합니다.

### 3.1 플랫폼 감지
```kotlin
fun isTelevision(uiMode: Int): Boolean =
    (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

fun isTelevision(configuration: Configuration?): Boolean =
    configuration?.let { isTelevision(it.uiMode) } ?: false
```

### 3.2 안전 영역 (Overscan Margin)
구형 TV 및 일부 모니터의 오버스캔 현상으로 인해 화면 테두리가 잘리는 문제를 방지하기 위해 기본 안전 마진은 가로 48dp, 세로 24dp로 둡니다. 다만 홈 상단의 브랜드·음악 묶음은 실제 1080p 캡처를 기준으로 기존 38dp의 30%인 11.4dp 전용 inset을 사용합니다. 화면 전체 안전 영역과 상단 시각 여백을 같은 상수로 묶지 않는 것이 핵심입니다.
- `TvUiModePolicy.SAFE_MARGIN_HORIZONTAL_DP` = `48f`
- `TvUiModePolicy.SAFE_MARGIN_VERTICAL_DP` = `24f`
- `TvUiModePolicy.TV_HOME_TOP_PADDING_DP` = `11.4f`

### 3.3 TV 미지원 기능 필터링
- **플래시 (Torch)**: TV 미지원 (`supportsTorch = false`)
- **카메라 및 조도 측정 (Camera Sensing)**: TV 미지원 (`supportsCamera = false`)
- **화면 회전 고정 (Orientation Lock)**: TV에서는 가로 모드 강제 (`supportsOrientationLock = false`)
- **외부 촬영 앱 연동 (AiShot)**: TV 미지원 (`supportsAiShot = false`)
- **보이소와 잠소리**: TV 제품 목적에서 제외하고 홈·설정·권한 요청에서 숨깁니다 (`supportsBoyiso = false`, `supportsSleepSounds = false`). 내부 저장 형식과 휴대전화·태블릿 기능은 유지합니다.
- **매이트/오브제 모드 전환**: TV에서는 노출하지 않고 오브제 동작으로 고정합니다 (`supportsModeCycling = false`). 헤더에도 모드 이름을 표시하지 않습니다.

---

## 4. 리모컨 및 10-foot UI 인터랙션

### 4.1 D-pad 초점 가시성 (`standFocusable`)
TV 화면은 3미터 이상 떨어진 거리(10-foot 환경)에서 감상하므로, 현재 포커스된 컨트롤이 명확히 식별되어야 합니다.
[`Modifier.standFocusable`](../app/src/main/java/com/armsone/stand/ui/components/TvFocusable.kt)를 통해 포커스 시 고대비 프라이머리 테두리와 부드러운 확대 애니메이션(1.04배)을 제공합니다.

```kotlin
fun Modifier.standFocusable(
    shape: Shape? = null,
    focusedBorderColor: Color? = null,
    focusedBorderWidth: Dp = 2.5.dp,
    scaleOnFocus: Boolean = true,
): Modifier
```

### 4.2 터치 제스처의 리모컨 대체 컨트롤
스마트폰의 터치/제스처 전용 기능을 TV 리모컨의 D-pad 중앙 선택(OK/Select) 버튼으로 조작할 수 있도록 홈 제어부에 리모컨 전용 타일을 제공합니다.

1. **테마 전환**: 스마트폰의 빈 화면 더블 탭 대신, D-pad 선택 시 디스플레이 테마를 순환 변경하는 "테마 전환" 타일 제공
2. **앱 밝기 조절**: 스마트폰의 화면 세로 드래그 대신, D-pad 선택 시 10%부터 100%까지 10단계를 순환합니다. 변경 직후 `현재 단계/10 · 퍼센트`를 잠시 표시합니다.
3. **시계 크기 조절**: 스마트폰의 핀치 줌 범위(0.7~1.35)는 유지하면서, TV에서는 D-pad 선택 시 0.7·0.9·1.1·1.3·1.5·1.7의 6단계를 순환합니다. 변경 직후 `현재 단계/6 · 퍼센트`를 잠시 표시해 남은 확대 범위를 알 수 있게 합니다.
4. **화면 편집**: 현재 편집기는 드래그 중심이므로 TV에서는 진입 버튼을 노출하지 않습니다. 새 앱에서는 D-pad로 `대상 선택 → 방향키 이동 → 선택으로 확정 → Back으로 취소`가 완결될 때만 TV에 편집 기능을 노출합니다.
5. **뒤로가기 키 (Back Navigation)**: 리모컨 Back 버튼 누름 시 `BackHandler`를 통해 홈 화면으로 안전하게 복귀
6. **시작 화면**: 실행 전에는 큰 `S.tand 시작` 버튼에 먼저 포커스를 줍니다. 실행 후에는 수면 감지·녹음 제어를 노출하지 않고 설정을 첫 포커스로 사용합니다.
7. **하단 제어 독**: 홈의 주요 내용과 시계를 가리지 않도록 `설정·테마 전환·앱 밝기·시계 크기`만 52dp 높이의 흐린 한 줄로 제한합니다. 일반 제어는 84×52dp, 설정은 52×52dp로 표시하며 리모컨 포커스 테두리와 선택 영역은 유지합니다.
8. **상단 음악 채널**: TV에서는 음악 채널을 112×44dp로 줄이고 여섯 칸 전체를 화면 가운데 정렬합니다. 평소 콘텐츠 밝기를 낮춰 시계보다 뒤에 보이게 하되 리모컨 포커스와 선택 동작은 그대로 유지합니다.

### 4.3 권한 요청 흐름 우회
- TV에서는 카메라와 수면 녹음용 마이크가 불필요하므로 시작 권한 안내 및 세션 시작 시 `CAMERA`와 `RECORD_AUDIO`를 필터링하고 날씨용 대략적 위치만 요청합니다. 이미 마이크 권한이 허용된 설치본도 TV ViewModel에는 미허용으로 전달해 감지 경로가 다시 켜지지 않게 합니다.
- 설정 화면에서도 플래시 및 카메라 사용 토글이 비활성화되며 `"TV에서는 지원하지 않음"` 안내 문구를 표시합니다.
- 배터리가 없는 TV의 `ACTION_BATTERY_CHANGED`는 `present=false`, `level=0`일 수 있습니다. 이 값을 실제 0%로 해석하면 보호 모드가 잘못 켜지므로, `EXTRA_PRESENT=false`이면 잔량을 미상으로 처리합니다.

---

## 5. 변경된 파일 목록

1. [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml): `LEANBACK_LAUNCHER` 카테고리, 배너 리소스 `@drawable/tv_banner`, 터치스크린 및 린백 non-required 피처 선언
2. [`TvUiModePolicy.kt`](../app/src/main/java/com/armsone/stand/model/TvUiModePolicy.kt): 플랫폼 감지, 안전 여백, 컨트롤/권한 필터링, D-pad 순환 제어 정책
3. [`TvFocusable.kt`](../app/src/main/java/com/armsone/stand/ui/components/TvFocusable.kt): D-pad 초점 시 고대비 테두리 및 스케일 모디파이어
4. [`MainActivity.kt`](../app/src/main/java/com/armsone/stand/MainActivity.kt): TV 모드 감지, 가로 화면 고정, 날씨 위치만 남긴 권한 시퀀스, 마이크 차단과 오브제 동작 고정
5. [`StandHomeScreen.kt`](../app/src/main/java/com/armsone/stand/ui/StandHomeScreen.kt): 10-foot 가로 레이아웃, 11.4dp 상단 inset, 음악·시계·날씨 중심 배치, TV 전용 리모컨 제어 타일과 포커스 링
6. [`SettingsScreen.kt`](../app/src/main/java/com/armsone/stand/ui/SettingsScreen.kt): TV에서 음악·화면과 시계·위치·정보만 노출하고 보이소·잠소리·모드 UI 제거
7. [`BoyisoScreen.kt`](../app/src/main/java/com/armsone/stand/ui/BoyisoScreen.kt): TV 환경에서 공간 만들기 권장 안내 및 카메라 스캔 제한 안내
8. [`TvUiModePolicyTest.kt`](../app/src/test/java/com/armsone/stand/model/TvUiModePolicyTest.kt): TV 감지, 기능 제어, D-pad 단계 조절 단위 테스트
9. [`IOS_PARITY.md`](IOS_PARITY.md): 플랫폼 차이와 Google TV 확장 이력
10. [`BatteryMonitor.kt`](../app/src/main/java/com/armsone/stand/platform/BatteryMonitor.kt): 배터리 미탑재 TV의 0% 오판 및 보호 모드 오작동 차단

---

## 6. 에뮬레이터 검증 결과와 남은 실기기 항목

> [!IMPORTANT]
> 2026-08-25에 Google TV API 36과 Android TV API 36 1080p arm64 AVD에서 확인했습니다. Google TV AVD는 계정 설정 전 홈 콘텐츠를 표시하지 않아 앱 내부 동작을 검증했고, 같은 Leanback 배너 계약의 Android TV AVD에서 런처 노출을 보조 검증했습니다.

- [x] **Android TV 홈 런처 배너**: 앱 선택 목록과 홈 즐겨찾기 첫 칸에서 이름이 포함된 16:9 배너 확인
- [x] **D-pad 리모컨 탐색**: 방향키와 선택으로 설정, 테마, 앱 밝기, 시계 크기 이동 확인
- [x] **리모컨 전용 제어 타일 동작**:
  - `테마 전환` 선택 시 디스플레이 테마 순환 변경 확인
  - `앱 밝기` 선택 시 10% 단위 순환 조절 및 시각 피드백 HUD 노출 확인
  - `시계 크기` 선택 시 TV 전용 0.7~1.7의 6단계 순환과 단계 HUD 확인
  - `앱 밝기` 선택 시 10단계 순환과 단계 HUD 확인
- [x] **초기 포커스**: 첫 실행 시작 버튼과 실행 후 설정 제어의 포커스 표시 확인
- [x] **1080p 안전 영역**: 가로 48dp·세로 24dp 안에서 헤더, 시계, 날씨와 하단 제어가 잘리지 않고 겹치지 않는지 확인
- [x] **리모컨 Back 키 복귀**: 설정 → 앱 홈 → TV 홈 순서 확인
- [x] **불필요한 권한 창 차단**: TV에서는 카메라·마이크를 요청하지 않고 날씨용 위치만 남기는 정책과 단위 테스트 확인
- [x] **배터리 미탑재 처리**: `present=false, level=0` TV가 저전력 보호 모드로 잘못 전환되지 않는지 확인
- [ ] **기존 스마트폰/태블릿 회귀 검증**: 휴대폰 세로/가로 및 태블릿 화면에서 기존 제스처, 플래시, 카메라 조도 감지, 사이드 컨트롤이 기존과 동일하게 유지되는지 확인
- [ ] **실제 Google TV 최종 확인**: 제조사 런처의 홈 노출, 실제 리모컨 촉감, 오버스캔, 마이크 입력과 장시간 실행 확인

### 6.1 다른 프로젝트에서 AVD를 재현하는 순서

1. Android Studio Device Manager에서 `Television (1080p)` 하드웨어를 고르고 Apple Silicon에서는 `arm64-v8a` Google TV 또는 Android TV 이미지를 설치합니다.
2. CLI로 자동화할 때는 최신 `android sdk install 'system-images;android-36;google-tv;arm64-v8a'`로 이미지를 설치합니다. 현재 새 Android CLI의 `emulator create`에는 TV 프로필이 없어 AVD 생성만 `avdmanager --device tv_1080p`를 사용합니다.
3. 구형 `avdmanager`가 최신 이미지의 `devices.xml` 부재를 오류처럼 출력해도 AVD를 만든 뒤 `android emulator list`에서 이름이 보이면 구성은 유효합니다. 시작·종료는 오류 재발을 피하기 위해 `android emulator start --cold <이름>`과 `android emulator stop <이름>`을 사용합니다.
4. 설치 뒤 `cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER <패키지>`로 런처 해석을 확인하고, 화면 검증은 D-pad 키 입력과 1920×1080 캡처를 함께 남깁니다.
5. AVD의 `config.ini`에서 `hw.dPad=yes`와 `hw.keyboard=yes`를 함께 확인합니다. `hw.keyboard=no`이면 앱의 포커스 구현이 정상이어도 호스트 방향키와 Enter가 가상 TV에 전달되지 않습니다. 값을 바꾼 뒤에는 데이터 초기화 없이 콜드 재시작합니다.
6. Extended Controls의 Directional Pad가 반응하지 않으면 같은 창에서 재시도만 반복하지 않습니다. 먼저 `adb shell input keyevent KEYCODE_DPAD_RIGHT`로 앱 입력과 에뮬레이터 UI 입력을 분리 진단합니다. ADB 입력만 정상이라면 기존 Extended Controls 창을 닫고 현재 AVD에서 다시 열거나, 에뮬레이터 본 화면을 한 번 클릭한 뒤 방향키·Enter를 사용합니다.

### 6.2 TV AVD에서 날씨가 비어 있을 때

Google TV AVD는 위치 권한과 위치 서비스가 켜져 있어도 `network provider`의 마지막 위치가 `null`일 수 있습니다. S.tand처럼 `LocationManager.NETWORK_PROVIDER`를 사용하는 앱은 Extended Controls에서 GPS 좌표만 보냈다고 바로 갱신되지 않을 수 있으므로 공급자 종류까지 확인합니다.

1. `dumpsys package`에서 `ACCESS_COARSE_LOCATION` 허용 여부를 확인합니다.
2. `cmd location is-location-enabled`와 `dumpsys location`에서 위치 서비스 및 `last location`을 확인합니다.
3. 테스트 AVD에서만 `com.android.shell`의 `android:mock_location` AppOps를 허용하고 `network` test provider에 위도·경도를 넣습니다.
4. 앱을 다시 실행해 `대략적 위치 확인 중 → 날씨 표시` 전환과 도시명·온도·상태를 확인합니다.
5. 모의 위치는 에뮬레이터 검증 장치일 뿐, 실제 TV에 GPS가 있다고 간주하는 근거가 아닙니다. 실기기에서는 네트워크 위치 제공 여부와 사용자가 설정한 위치 정책을 별도로 확인합니다.

## 7. 다른 앱에 적용할 때의 완료 조건

1. APK의 병합 Manifest에서 `MAIN + LEANBACK_LAUNCHER`, `touchscreen required=false`, 배너를 확인합니다.
2. TV용 가로 화면을 별도 정보 구조로 설계하고 텍스트·버튼을 시청 거리에서 읽을 수 있게 만듭니다.
3. 첫 포커스를 명시하고, 모든 노출 기능을 D-pad 상·하·좌·우·선택·Back만으로 처음부터 끝까지 실행합니다.
4. 터치 제스처에만 의존하는 기능은 리모컨 버튼 흐름을 추가하거나 TV에서 숨기고 이유를 설명합니다.
5. 카메라·마이크·GPS·센서·전화 기능을 Manifest에서 필수로 만들지 않습니다. 런타임에서도 실제 기능 보유 여부를 검사합니다.
6. 320×180 배너 안에 앱 이름이 보이는지, 앱 아이콘이 작은 정사각형 표시에서도 식별되는지 확인합니다.
7. 소스와 빌드만으로 완료 처리하지 않고 실제 Google TV에서 홈 아이콘, 오버스캔, 포커스 이동, Back, 권한 거부, 앱 재실행을 확인합니다.
