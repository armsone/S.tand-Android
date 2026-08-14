# S.tand iOS 0.25.1 ↔ Android 동등성 감사

감사일: 2026-08-14 KST

iOS 기준: `main` / `33711c009b7568779504a73685d616d6ec115db0`, `1.0.0 (0.25.1)`

Android 기준: 작업 트리의 versionName `0.0.1`, versionCode `48`, 빌드 `0.0.48`

## 보이소 v2 진행 상태 (2026-08-14)

- 사용자 최신 지시로 기존 8자리 코드 방식보다 `docs/BOYISO_V2_PROTOCOL.md`를 우선한다.
- 앱 표시는 **에스텐드(S.tand)**, 돌봄 연결 서비스 표시는 **보이소(BOISO)**로 구분한다. 보이소
  화면과 설정의 보조 설명은 `BOISO · 보이는 소리`이며, 홈 버튼과 알림은 한글 `보이소`를 우선한다.
- 사용자 표시는 `볼 사람·말할 사람`으로 통일하고 내부 wire value만 `host·guest`를 유지한다.
- Android에 설정 카드, 홈의 설정 오른쪽 보이소 버튼, QR 생성·촬영, 다중 기기 목록,
  LAN·BLE 메시 relay, 선택적 WSS relay, 톡톡 3초 인사·알림 자산을 구현했다.
- 사용자 지시에 따라 현재 완료 우선순위는 LAN·BLE 근거리 연결이며, 기본 빌드에서 WSS는
  비활성이다. 원거리 서버와 APNs·FCM은 후속 단계로 보류한다.
- Kotlin·Java compile과 관련 JVM·계측 테스트 source compile은 통과했으나 실제 QR 카메라,
  여러 기기 LAN/BLE, 백그라운드 톡톡, WSS 서버, iPhone 상호 운용은 아직 실행하지 않았다.
- 서버 URL, 공개 배포, APNs·FCM 인증정보가 없어 원거리 연결은 완료로 판정하지 않는다.
- iPhone 이식 기준과 완료 조건은 `docs/BOYISO_IOS_HANDOFF.md`에 기록했다.

## 2026-08-14 권한·큰 글자 회귀 보강

- 새 프로세스가 Activity 저장 상태를 복원해도 이번 프로세스의 3~7회 재안내 결정을 우선하도록
  분리했다. 같은 프로세스의 화면 회전은 기존 표시 결정을 유지한다.
- 앱 시작이나 매이트 자동 상태 변화가 카메라 권한창을 띄우던 경로를 제거했다. 첫 화면의
  `권한 확인하고 시작`과 설정의 명시적 복구 버튼만 OS 권한창을 연다.
- 권한 안내 중에는 홈 제스처·제어·하단 상태를 렌더링하지 않아 큰 글자와 가로 화면에서도 안내와
  기존 홈 요소가 겹치지 않는다.
- 초기화한 Android 16 임시 시험기에서 최종 3카드, 카메라→마이크→대략적 위치 거부 순서,
  거부 뒤 자동 권한창 없음, 무작위 값 7 저장과 다음 6회 생략·7회째 재안내를 확인했다. 회전은
  카운터를 바꾸지 않았다.
- 글자 크기 130% 가로와 200% 세로에서 안내 세 카드·하단 설명·시작 버튼까지 스크롤 가능하고,
  홈 요소가 함께 노출되지 않음을 확인했다. 200%에서 설정 Hero·라디오·브라우저 진입과 브라우저
  주소·조작 버튼이 화면 안에 있음을 확인했다.
- 같은 임시 시험기에서 TalkBack 서비스를 실제로 켜고 홈 제어, 날씨, 모드, 녹음, 설정, 빌드
  번호가 접근성 노드로 노출됨을 확인했다. 실제 음성 탐색 순서와 사용자 실기기 촉감은 미검증이다.
- 전체 JVM 단위시험·`assembleDebug`·`lintDebug`가 성공했다. 같은 APK를 `SM-F968N`, `SM-T500`,
  임시 시험기에 데이터 유지 설치하고 versionCode 37을 확인했으며, 임시 시험기에서는 하단
  `0.0.37` 표시까지 확인했다. 폰 화면은 깨우지 않아 빌드 37의 실제 UI는 미확인이다.
- GitHub `android-v37` Release의 공개 태그·APK 이름·크기·다운로드 주소를 확인했다. 임시 시험기의
  v36에서 앱 내부 안내→다운로드→서명 확인→출처 허용→Android 설치를 거쳐 v37로 갱신했고,
  기존 `3개 녹음`과 밝기 72% 표시가 유지됨을 확인했다.

## 2026-08-13 iOS 0.25.1 델타 적용

- 권한 안내를 최종 3카드 원문으로 맞추고 카메라→마이크→대략적 위치 순서와 프로세스 실행
  3~7회 재안내를 유지했다. 회전·Activity 재생성·foreground 복귀는 중복 계산하지 않는다.
- 설정 Hero 상태를 읽기 전용으로 만들고 `자동/오브제 유지/매이트 유지` 직접 선택을 추가했다.
  카드 순서는 Hero→화면/권한/수면/정보→라디오로 변경했다.
- 좁은 브라우저 주소줄을 두 행으로 배치하고 history/popup과 무관한 별도 닫기 X를 추가했다.
- 편집 패널을 상단 편집 도구와 하단 안내/글꼴 선택 영역 안으로 제한한다.
- 세 라디오 삭제 경로를 채널 이름·복구 불가 안내·`채널 삭제/취소` 확인 뒤 실행하도록 통일했다.
- 매이트 진입 monotonic 시각을 기준으로 최초 60초에는 화들짝 event/lamp/조건부 torch만 막는다.
  같은 매이트 재적용은 시각을 유지하고 오브제 왕복은 새 기준을 만든다. 감지·녹음 흐름은 유지한다.
- 폐기된 YouTube 실험은 Android 소스·설정·화면에 추가하지 않았고 전역 검색 결과 제품 참조는 0건이다.

검증: 전체 `testDebugUnitTest`, `assembleDebug`, `lintDebug` 성공. `SM-F968N`과 `SM-T500`에
데이터 유지 방식으로 versionCode 36을 설치·실행하고 두 기기의 설치 버전을 확인했다. 폰 홈과
설정 segmented UI, 패드 홈과 하단 `0.0.36` 표시는 화면 캡처로 확인했다. 권한 거부 순서,
브라우저 두 행, 편집 clamp, 60초 실제 센서 반응, 큰 글자/TalkBack은 이번 기기 검증에서 직접
실행하지 않았으므로 완료로 표시하지 않는다.

## 2026-08-13 iOS 인수인계 체크리스트 재감사

### 첫 실행 권한 안내

- Android 첫 화면에 플래시·카메라·마이크·위치 정보의 사용 이유와 거부해도 앱이 시작된다는
  설명을 추가했다.
- 버튼 한 번으로 아직 없는 권한을 카메라→마이크→대략적 위치 순서로 요청한다. 플래시와
  카메라는 같은 Android 카메라 권한을 사용한다.
- 권한이 하나라도 없으면 최초 확인 때 안내하고 이후에는 기기에 저장한 무작위 3~7회 실행 간격으로
  다시 안내한다. 화면 회전과 같은 실행 중 재진입은 중복 계산하지 않는다.
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

## 현재 설치 후보·버전 상태

- Android versionName `0.0.1`, versionCode `48`, 빌드 표기 `0.0.48`.
- versionCode 48에서 중복 mesh event가 참여자 상태를 갱신하지 않도록 보강했고, 백그라운드
  매이트 모드의 소리 알림은 고우선 알림·진동·두 번의 띵동과 알림 탭 후 화들짝 화면 진입을 제공한다.
- versionCode 48의 기기 설치·실행은 다음 설치 단계에서 확인한다.
- `SM-T500`에서 역할·이름·공간 선택, 모든 참여자의 QR 공유, source ID·연결 경로 기준
  역할별 참여자 목록, 연결 중 이름 수정, 밝은 화들짝 알림 문구의 Boyiso 화면 계측 테스트
  5개가 통과했다. `SM-F968N`과 `SM-T500` 모두 화면이 꺼진 상태에서 foreground service
  유지를 확인했다. 실제 두 기기 사이 장시간 재연결과 최신 소리·진동 전체 흐름은 사용자
  실사용 확인이 남아 있다.
- 홈 날씨 패널 더블 터치 계측 테스트를 `SM-T500`에서 실행해 실패 0을 확인했다.
  `SM-F968N`은 빈 공간 실기기 더블 터치로 테마 변경을 확인했고, 같은 계측 테스트는 테스트 서비스
  미설치로 시작 전에 중단됐다.
- 두 기기에 체크섬이 같은 APK를 데이터 유지 설치하고 versionCode 33과 MainActivity 실행을 확인했다.
- GitHub `android-v33` Release를 공개하고 태그, 공개 상태, APK 이름과 크기를 확인했다. 32→33
  앱 내부 업데이트 설치 흐름은 아직 별도로 실행하지 않았다.
- GitHub `android-v34` Release를 공개하고 태그, 공개 상태, APK 이름과 크기를 확인했다. 첫 실행
  권한 안내는 `SM-T500`에서 실제 권한 순서까지 검증했으며 두 기기에 versionCode 34를 설치했다.
- GitHub `android-v35` Release를 공개하고 두 기기에 versionCode 35를 데이터 유지 설치했다.
  `SM-F968N`에서 최초 권한 안내와 무작위 값 4 저장, 다음 실행에서 안내 생략과 값 3 감소를 확인했다.
- GitHub `android-v32` Release를 공개하고, `SM-F968N`과 Android 10 `SM-T500`의 versionCode 30
  설치본에서 업데이트 안내→다운로드→서명 검사→설치를 진행해 versionCode 32와 기존 녹음 파일
  유지를 확인했다. 최초 사용자는 Android의 `이 출처 허용`을 한 번 켜야 한다.
