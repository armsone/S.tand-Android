# S.tand iOS 0.24.1 ↔ Android 동등성 감사

감사일: 2026-08-13 KST

iOS 기준: `main` / `0f664b2`, `1.0.0 (0.24.1)`

Android 기준: 작업 트리의 versionName `0.0.1`, versionCode `34`, 빌드 `0.0.34`

## 2026-08-13 iOS 인수인계 체크리스트 재감사

### 첫 실행 권한 안내

- Android 첫 화면에 플래시·카메라·마이크·위치 정보의 사용 이유와 거부해도 앱이 시작된다는
  설명을 추가했다.
- 버튼 한 번으로 아직 없는 권한을 카메라→마이크→대략적 위치 순서로 요청한다. 플래시와
  카메라는 같은 Android 카메라 권한을 사용한다.
- `SM-T500`에서 설명 화면 표시, 세 권한창 순서와 모두 허용 후 오브제 모드 시작을 확인했다.
- 이 흐름은 현재 iOS 기준 문서에 없는 신규 사용자 요구다. iOS에는 별도 구현 요청을 전달하며,
  iPhone 실기기 결과를 받기 전까지 동등 완료로 표시하지 않는다.

`ANDROID_IMPLEMENTATION_HANDOFF.md`와 `ANDROID_PARITY_ACCEPTANCE_CHECKLIST.md`를 처음부터
끝까지 읽고 iOS `0f664b2`의 실제 정책과 Android 작업 트리를 다시 대조했다. 변경 전
`testDebugUnitTest`, `assembleDebug`, `lintDebug`가 통과했다. 같은 APK를 `SM-T500`과
`SM-F968N`에 데이터 유지 설치하고 첫 화면을 확인했다. `SM-F968N`에서는 빈 홈 영역 더블 터치로
미드나이트에서 세이지 테마 전환을 확인했다. 그 밖의 상세 실기기 항목은 완료로 보지 않는다.

### 화면·기능별 현재 증거

| 체크리스트 영역 | 구현 상태 | 자동 테스트 | UI 테스트 | 실기기 | 남은 확인 |
|---|---|---|---|---|---|
| H0 정지 홈 | 시작 아이콘·제목·설명·`S.tand 시작` CTA 대응 | 컴파일 | 미실행 | 미실행 | 세로/가로, 큰 글자, 안전 영역 |
| H1 활성 홈 | 날씨 셀 94dp/123.333dp, 제어부 상시 표시 대응 | 정책/JVM | 미실행 | 미실행 | 실제 회전·좁은 폭 배치 |
| H2 단일 탭 | 전 화면 탭과 가벼운 햅틱 연결 | 기존 정책/JVM | 미실행 | 미실행 | 자식 패널과의 제스처 경쟁, 실제 진동 |
| H3 더블 탭 | 전 화면 테마 전환과 햅틱 연결 | 컴파일 | 미실행 | SM-F968N 테마 전환 확인 | 단일 탭 오발 방지 추가 확인 |
| H4 길게 누르기 | 전 화면 편집 진입과 햅틱 연결 | 컴파일 | 미실행 | 미실행 | 편집/시트 위 비활성 조건 |
| H5 세로 밝기 | 최초 우세 축 고정, 화면 높이 절반에 0~100%, 0 즉시 Mate·100만 1초 Object 고정 | 정책/JVM | 미실행 | 미실행 | 끝점 체감·HUD 드래그 중 노출 |
| H6 좌우 라디오 볼륨 | 최초 우세 축 고정, 화면 폭 절반에 0~100%, 앱 플레이어 볼륨만 변경 | 정책/JVM | 미실행 | 미실행 | 실제 스트림·재연결 후 유지, 시스템 볼륨 불변 |
| H7 핀치 | 시계 0.7~1.35 배율 저장 연결 | 정책/JVM | 미실행 | 미실행 | 2포인터 전환·회전 후 유지 |
| H8 HUD | 밝기/볼륨 캡슐 HUD를 드래그 중만 표시, 시계 크기는 1.2초 유지 | 컴파일 | 미실행 | 미실행 | 0% 음소거 아이콘·정확한 시각 패리티 |
| H9 얼굴 아래 | 기존 검은 화면·토치 차단 정책 유지 | 정책/JVM | 미실행 | 미실행 | 센서 실기기 |
| H10 움직임 조명 | Mate+움직임+최근 60초 암부에서만 허용, 설정 on 100%/off 10% | 정책/JVM | 미실행 | 미실행 | 카메라/토치 실기기·인터럽션 |
| E 편집 | 기존 좌표·크기·자석·방향별 저장 유지 | 정책/JVM | 편집 제스처 3개 통과 | SM-T500·SM-F968N 통과 | 홈 제스처가 편집 위에서 개입하지 않는지 추가 확인 |
| S 설정 | 기존 정보 구조 유지, 모드 전환은 ViewModel 단일 경로 | 정책/JVM | 미실행 | 미실행 | 권한·복구·문구 전체 시각 확인 |
| R 녹음 | 기존 감지·세션·재생·병합·삭제 구현 유지 | JVM | 미실행 | 미실행 | 마이크·AudioFocus·부분 실패 |
| B 브라우저 | 큰 글자에서도 주소줄 높이 적응, renderer 재생성 시 현재 안전 주소 복구 | 정책/JVM·컴파일 | 실제 화면 확인 | SM-F968N에서 Google HTTPS·주소줄 확인 | 팝업·파일 차단·미디어·회전 |
| A 앱 수명주기 | 기존 foreground/background 정책 유지 | JVM | 해당 없음 | 미실행 | 전화/이어폰/화면 잠금 인터럽션 |

### 제스처 보호 판정

- 홈 제스처는 터치 slop 뒤 최초 우세 축을 한 번 고정한다. 세로는 앱 화면 밝기, 가로는
  `InternetRadioPlayer`의 앱 내부 볼륨만 바꾸며 시스템/녹음 볼륨 API는 호출하지 않는다.
- 하단 제어 영역은 기존 104dp 제외 영역을 유지한다. 편집·설정·녹음·브라우저는 홈 제스처
  레이어 밖의 별도 destination이므로 같은 제스처가 적용되지 않는다.
- 위 두 항목은 소스 연결과 JVM 정책까지만 확인했다. 실제 멀티터치 경합과 TalkBack 동작은
  Compose UI/실기기 미검증이다.

### 플랫폼 차이와 미완료 판정

- iOS 햅틱은 UIKit feedback generator, Android는 `performHapticFeedback`의 플랫폼 상응 효과를
  사용한다. 기기 설정과 하드웨어에 따라 세기가 달라질 수 있어 실기기 확인 전 완료로 보지 않는다.
- iOS 카메라/토치 API는 AVFoundation, Android는 Camera2다. 1초 관찰, 45초 주기, 최근 60초
  암부 판정의 의미는 맞췄지만 실제 센서 노출·토치 레벨은 기기별 확인이 남았다.
- WKWebView non-persistent store는 Android WebView 종료 시 cookie/cache/storage 정리로 대응한다.
  renderer 복구는 현재 HTTPS 주소 재로드로 보강했으나 process kill 실검증은 남았다.

## 판정 기준과 증거

- `구현`: 현재 Android 소스에서 실제 연결을 확인했다.
- `단위`: JVM 테스트가 해당 정책을 직접 검증한다.
- `UI`: Compose 계측 또는 동등한 자동 UI 검증을 실행했다.
- `기기`: 이 기준 후보를 허가된 기기에서 직접 확인했다.
- `역대조`: iOS 기준 커밋의 실제 Swift/XCTest와 항목별로 비교했다.
- 실행하지 않은 칸은 `대기`, 차이가 있으면 `누락`, 플랫폼 고유 대응은 `플랫폼 차이`다.

이번 감사에서 읽은 iOS 근거는 `RootView.swift`, `SettingsView.swift`, `RecordingsView.swift`,
`InternetRadioBrowserView.swift`, `AppSettings.swift`, `AudioCaptureService.swift`,
`InternetRadioPlayer.swift`, `RecordingLibrary.swift`, `WeatherService.swift`,
`WakeMotionMonitor.swift`와 XCTest 126개다. Android는 navigation/Compose 화면, 관련 서비스와
JVM `@Test` 125개, 계측 `@Test` 4개를 양방향으로 대조했다.

2026-08-12에 `testDebugUnitTest`, `assembleDebug`, `lintDebug`는 성공했다. `SM-T500`에는 데이터
유지 방식으로 versionCode 24를 설치하고 Activity 실행과 설치 버전을 확인했다. UI hierarchy로 홈
화면까지 확인했지만 브라우저 진입·사이트 탐색은 아직 실행하지 않았다. 이후 `SM-F968N`과
`SM-T500` 모두에 같은 versionCode 24 APK를 데이터 유지 방식으로 설치하고 Activity 실행과
설치 버전을 확인했다.

같은 날 `0.0.25`/versionCode 25 후보를 두 기기에 데이터 유지 방식으로 설치했다. `SM-T500`은
Activity 실행과 홈 하단 `0.0.25 · 밝기 86%`의 안전 영역 내 표시를 화면 캡처로 확인했다.
`SM-F968N`은 설치와 versionCode 25를 확인했지만 HanClip이 전경에서 사용 중이어서 강제 실행과
UI 검증은 보류했다.

## 17장 증거표

| 영역 | 구현 | JVM/단위 | UI/계측 | 실기기 | iOS 역대조 |
|---|---|---|---|---|---|
| 전체 화면·진입점 | 구현 | 해당 없음 | 최신 소스 UI 대기 | versionCode 26 설치·Activity 실행, 화면별 UI 대기 | 별도 채널 관리, 홈 라디오 길게 편집, 브라우저 3경로와 편집 중 빈 라디오의 관리 진입 구현 |
| 홈·제스처·모드 | 부분 구현 | 밝기·모드·라디오 볼륨 정책 통과 | 최신 소스 계측 대기 | SM-T500의 이전 0.0.25 후보에서 홈 빌드·밝기 표시 확인 | 최초 우세 축 고정, 세로 앱 밝기와 좌우 앱 내 라디오 볼륨을 대응. 홈 밝기 adjustable/custom action 구현, TalkBack 검증 대기 |
| 패널 편집·기본 배치 | 구현 | 좌표·크기·자석·40% 결합 테스트 통과 | 계측 3개 두 기기 통과 | 태블릿 가로·600dp 설정 2열, 폴더블 라디오 분리 확인 | 방향별 초안·결합 대응. 묶인 라디오는 한 번/두 번 터치로 분리 |
| 설정 정보 구조 | 구현 | 카드 순서 테스트 통과 | 대기 | 대기 | Hero/인라인 라디오/별도 관리·추가·수정/화면·권한·수면·정보가 존재 |
| 라디오 채널·재생 | **부분 구현** | HTTPS·2채널·변경 중지·`2/4/8/15/30초` 5회 상한 통과 | 대기 | 스트림/AudioFocus 대기 | AudioFocus는 transient 손실 뒤 gain에서만 재개하도록 구현. 삭제 확인 구현. 실제 인터럽션 검증과 삭제 실패 재동기화는 남음 |
| 앱 내부 브라우저 | **부분 구현** | 주소·검색·credential·즐겨찾기 테스트 통과 | 최종 주소줄 시각 확인 | versionCode 27 SM-F968N에서 Google HTTPS 로드·주소줄 확인 | 설정/관리/편집 3경로 구현. popup·미디어 실검증은 남음 |
| 공유 수신 | 구현 | 한 개 HTTPS 초안 정책 통과 | 계측 대기 | 최신 후보 대기 | `ACTION_SEND/text/plain` 별도 receiver, `ACTION_VIEW` 미추가 확인 |
| 감지·녹음·세션 | **부분 구현** | 감지·90초 rollover·pending 4개 상한·세션 테스트 통과 | 대기 | 마이크 실입력 대기 | 다섯 번째 segment를 열지 않고 후보를 정리. 실제 연속 입력과 인터럽션 재개 조건은 대기 |
| 녹음 재생·병합·삭제 | 부분 구현 | 병합·저장·참조 제거 테스트 통과 | 대기 | 최신 후보 대기 | 선택 삭제는 성공 파일만 manifest에서 제거하고 reload함. 실패/부분 성공 UI와 전체 삭제 경로의 직접 회귀 테스트가 부족 |
| 날씨·위치 | 구현 | 요청·캐시·좌표 테스트 통과 | 대기 | 최신 후보 대기 | request generation과 위치 사용 종료 시 weather/locationName/lastUpdated 제거 구현. 비활성 실기기 검증 대기 |
| 설정 복구·마이그레이션 | **부분 구현** | 레거시/기본값·payload decodability 테스트 통과 | 해당 없음 | 업데이트 시나리오 대기 | 최초 migration에서 디코딩 불가 layout 원본을 보존하고 실제 설정 변경 때 대체. 실제 과거 APK 업데이트 검증 대기 |
| 수명주기·배터리·개인정보 | 부분 구현 | 수명주기·배터리 정책과 file timestamp 우선/fallback 통과 | 대기 | 최신 소스 대기 | 백업 제외, 실제 권한·로컬 저장·외부 전송과 file timestamp 근거 문서화. Play Console 제출·네트워크 관찰 대기 |
| 접근성·큰 글자 | **부분 구현** | 직접 JVM 대상 아님 | 계측 대기 | TalkBack/큰 글자 대기 | 편집 패널 5% 이동/10% 크기 custom action과 홈 조명 adjustable/custom action 구현. 동적 글자 브라우저 배치와 실제 TalkBack 검증 대기 |
| GitHub 업데이트 | Android 전용 구현 | 태그·저장소·자산 정책 통과 | 안내 화면 계측 통과 | 폴더블·태블릿 30→32 원터치 업데이트·실행·녹음 유지 확인 | iOS에는 없는 Android 배포 방식. APK package/version/현재 서명 검증 후 Android 설치 화면 사용 |

## 구현 누락

1. 라디오 삭제 실패 후 저장 상태 재동기화 UI의 직접 회귀 테스트와 실제 AudioFocus 인터럽션 검증.
2. 녹음·라디오 오디오 인터럽션의 실기기 재개 조건 검증.
3. 홈 앱 조명과 편집 패널 custom action의 TalkBack·큰 글자 실검증.
4. Android Data Safety의 Play Console 제출과 실제 네트워크 관찰.
5. 브라우저 renderer 재생성, 파일 차단, popup·미디어의 UI·실기기 검증.

위 항목은 이번 브라우저 승인 범위를 넘어 임의 구현하지 않았다. 다음 구현 묶음에서 영향 범위와
회귀 계획을 먼저 확인받아야 한다.

## 의도적 Android 플랫폼 차이

| 영역 | iOS | Android 대응 | 현재 판정 |
|---|---|---|---|
| UI | SwiftUI | Jetpack Compose | 허용, 시각·동작 역대조 필요 |
| 브라우저 | WKWebView non-persistent store | Android WebView + 종료 시 cookie/cache/storage 정리 | 허용, process/popup/회전 실검증 대기 |
| 공유 | Safari extension | exported `ACTION_SEND/text/plain` receiver | 허용, 입력 정책 동일 |
| 오디오 | AVAudioSession | AudioFocus/AudioManager | 의미 대응 구현, 실기기 검증 대기 |
| 움직임 | CoreMotion | SensorManager | generation 의미 대응, 실기기 검증 대기 |
| 위치 | iOS approximate location | coarse location | cache 종료 의미 대응, 실기기 검증 대기 |
| 백업 | iOS 앱 로컬 정책 | Android cloud backup/device transfer 전체 제외 | 사용자 데이터 보호 방향으로 허용 |
| 파일 시각 | URL resource values | 앱 파일명 우선, 앱 전용 파일의 `File.lastModified()` fallback | 플랫폼 차이. 코드·JVM·Data Safety 근거 기록 완료, Play Console 확인 대기 |

## 앱 내부 브라우저 상세 감사

| 항목 | Android 소스 | 자동 검증 | 남은 검증 |
|---|---|---|---|
| Google 홈·주소·검색 | 구현 | JVM 통과 | 실제 검색 |
| 기본 즐겨찾기 4개 | iOS와 같은 순서/URL | JVM 통과 | 패널 표시·탭 |
| 이동/중지·새로고침·뒤로/닫기 | 구현 | 없음 | history·길게 누르기·회전 |
| HTTPS/host/credential | 구현 | JVM 통과 | redirect·popup |
| cleartext/mixed/file/content | Manifest/WebSettings 차단 | lint 통과 | 실제 차단 |
| 다운로드/파일 선택/웹 권한/HTTP auth/SSL | callback 차단 | 없음 | 사이트별 실검증 |
| 팝업 같은 WebView | 구현 | 없음 | 원래 페이지 복귀 |
| 백그라운드 미디어 정지 | lifecycle observer와 JS pause | 없음 | 오디오·영상 실검증 |
| 자동 스트림 감지·편집 전달 | 관련 분석/전달 코드 없음 | 소스 역대조 | 네트워크 관찰 검증은 미실행 |
| renderer 종료·파일 drop/clipboard | renderer 재생성, URI drop·파일 선택·WebView 문맥 메뉴 차단 | 컴파일 확인 | renderer/drop/clipboard 실제 검증 |

## 실기기 검증 대기

- `SM-T500`: versionCode 33 데이터 유지 설치·실행 확인. 브라우저 popup,
  다운로드·업로드·권한 거부, 백그라운드 미디어 정지는 대기.
- `SM-F968N`: versionCode 33 데이터 유지 설치·실행 확인. 이전 27번에서 Google HTTPS·주소줄 확인.
- 두 기기: 라디오/마이크 상호배제, AudioFocus, 권한 거부·복구, 화면 회전, TalkBack,
  삭제 부분 실패는 별도 안전한 테스트 데이터로 확인.

## 현재 배포·버전 상태

- Android versionName `0.0.1`, versionCode `34`, 빌드 표기 `0.0.34`.
- 로컬 JVM 테스트·`assembleDebug`·`lintDebug` 성공.
- 홈 날씨 패널 더블 터치 계측 테스트를 `SM-T500`에서 실행해 실패 0을 확인했다.
  `SM-F968N`은 빈 공간 실기기 더블 터치로 테마 변경을 확인했고, 같은 계측 테스트는 테스트 서비스
  미설치로 시작 전에 중단됐다.
- 두 기기에 체크섬이 같은 APK를 데이터 유지 설치하고 versionCode 33과 MainActivity 실행을 확인했다.
- GitHub `android-v33` Release를 공개하고 태그, 공개 상태, APK 이름과 크기를 확인했다. 32→33
  앱 내부 업데이트 설치 흐름은 아직 별도로 실행하지 않았다.
- GitHub `android-v34` Release를 공개하고 태그, 공개 상태, APK 이름과 크기를 확인했다. 첫 실행
  권한 안내는 `SM-T500`에서 실제 권한 순서까지 검증했으며 두 기기에 versionCode 34를 설치했다.
- GitHub `android-v32` Release를 공개하고, `SM-F968N`과 Android 10 `SM-T500`의 versionCode 30
  설치본에서 업데이트 안내→다운로드→서명 검사→설치를 진행해 versionCode 32와 기존 녹음 파일
  유지를 확인했다. 최초 사용자는 Android의 `이 출처 허용`을 한 번 켜야 한다.
