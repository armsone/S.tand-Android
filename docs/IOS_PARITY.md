# S.tand iOS 0.24.1 ↔ Android 동등성 감사

감사일: 2026-08-12 KST

iOS 기준: `main` / `0f664b2`, `1.0.0 (0.24.1)`

Android 기준: 작업 트리의 versionName `0.0.1`, versionCode `26`, 빌드 `0.0.26`

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
JVM `@Test` 125개, 계측 `@Test` 3개를 양방향으로 대조했다.

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
| 패널 편집·기본 배치 | 구현 | 좌표·크기·자석·40% 결합 테스트 통과 | 기존 계측 2개, 최신 후보 재실행 대기 | 최신 후보 회전/600dp 대기 | 방향별 초안·결합은 대응. 소스에 과거 하단 버튼 순서 편집 코드가 남아 실제 노출 여부 재확인 필요 |
| 설정 정보 구조 | 구현 | 카드 순서 테스트 통과 | 대기 | 대기 | Hero/인라인 라디오/별도 관리·추가·수정/화면·권한·수면·정보가 존재 |
| 라디오 채널·재생 | **부분 구현** | HTTPS·2채널·변경 중지·`2/4/8/15/30초` 5회 상한 통과 | 대기 | 스트림/AudioFocus 대기 | AudioFocus는 transient 손실 뒤 gain에서만 재개하도록 구현. 삭제 확인 구현. 실제 인터럽션 검증과 삭제 실패 재동기화는 남음 |
| 앱 내부 브라우저 | **부분 구현** | 주소·검색·credential·즐겨찾기 테스트 통과 | 대기 | versionCode 26 두 기기 설치, 브라우저 UI 대기 | 설정/관리/편집 3경로, renderer 재생성, 파일 선택·drop·WebView 문맥 메뉴 차단 구현. popup·미디어 실검증은 남음 |
| 공유 수신 | 구현 | 한 개 HTTPS 초안 정책 통과 | 계측 대기 | 최신 후보 대기 | `ACTION_SEND/text/plain` 별도 receiver, `ACTION_VIEW` 미추가 확인 |
| 감지·녹음·세션 | **부분 구현** | 감지·90초 rollover·pending 4개 상한·세션 테스트 통과 | 대기 | 마이크 실입력 대기 | 다섯 번째 segment를 열지 않고 후보를 정리. 실제 연속 입력과 인터럽션 재개 조건은 대기 |
| 녹음 재생·병합·삭제 | 부분 구현 | 병합·저장·참조 제거 테스트 통과 | 대기 | 최신 후보 대기 | 선택 삭제는 성공 파일만 manifest에서 제거하고 reload함. 실패/부분 성공 UI와 전체 삭제 경로의 직접 회귀 테스트가 부족 |
| 날씨·위치 | 구현 | 요청·캐시·좌표 테스트 통과 | 대기 | 최신 후보 대기 | request generation과 위치 사용 종료 시 weather/locationName/lastUpdated 제거 구현. 비활성 실기기 검증 대기 |
| 설정 복구·마이그레이션 | **부분 구현** | 레거시/기본값·payload decodability 테스트 통과 | 해당 없음 | 업데이트 시나리오 대기 | 최초 migration에서 디코딩 불가 layout 원본을 보존하고 실제 설정 변경 때 대체. 실제 과거 APK 업데이트 검증 대기 |
| 수명주기·배터리·개인정보 | 부분 구현 | 수명주기·배터리 정책과 file timestamp 우선/fallback 통과 | 대기 | 최신 소스 대기 | 백업 제외, 실제 권한·로컬 저장·외부 전송과 file timestamp 근거 문서화. Play Console 제출·네트워크 관찰 대기 |
| 접근성·큰 글자 | **부분 구현** | 직접 JVM 대상 아님 | 계측 대기 | TalkBack/큰 글자 대기 | 편집 패널 5% 이동/10% 크기 custom action과 홈 조명 adjustable/custom action 구현. 동적 글자 브라우저 배치와 실제 TalkBack 검증 대기 |

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

- `SM-T500`: 설정→브라우저, 즐겨찾기 4개, Google 검색, history/팝업, 회전, 큰 글자,
  다운로드·업로드·권한 거부, 백그라운드 미디어 정지.
- `SM-F968N`: versionCode 25 데이터 유지 설치 완료. HanClip 사용 중이라 S.tand 강제 실행/UI 검증은 보류.
- 두 기기: 라디오/마이크 상호배제, AudioFocus, 권한 거부·복구, 화면 회전, TalkBack,
  삭제 부분 실패는 별도 안전한 테스트 데이터로 확인.

## 현재 배포·버전 상태

- Android versionName `0.0.1`, versionCode `26`, 빌드 표기 `0.0.26`.
- 로컬 JVM 테스트 125개·`assembleDebug`·`lintDebug` 성공.
- `SM-T500`에 데이터 유지 설치, versionCode 26과 MainActivity 전경 실행 확인.
- `SM-F968N`에 데이터 유지 설치, versionCode 26과 MainActivity 프로세스 생성 확인. 기기가 잠금
  화면 수면 상태라 전경 UI 확인은 수행하지 않았다.
- 설치 원본과 바탕화면 `S.tand-0812-1207.apk`의 SHA-256 일치를 확인했다.
