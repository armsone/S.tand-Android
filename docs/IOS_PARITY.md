# S.tand iOS 0.25.1 ↔ Android 동등성 감사

감사일: 2026-08-14 KST

iOS 기준: `main` / `1c92b69`, `1.0.0 (0.32.5)`

Android 기준: 작업 트리의 versionName `0.0.1`, versionCode `57`, 빌드 `0.0.57`

## 2026-08-30 밤샘 매이트 모드 소리 감시 신뢰성 및 상태 투명성 보강

- **밤샘 안전 기본값 및 마이그레이션**: 신규 설치에서 `backgroundModeEnabled`를 `true`로 기본 활성화하고, 기존 설치는 `settingsMigration.backgroundMateMonitoring.v1`을 통해 1회 `true`로 안전 승격하여 이후 사용자 명시적 변경을 온전히 존중합니다. 설정 화면 문구도 최신 기본값을 명확히 안내합니다.
- **잠금 및 백그라운드 전환 결정성**: 활성 세션 중 화면 꺼짐 또는 백그라운드 전환 시 자동 모드가 비동기 광원 타이머를 건너뛰고 결정적으로 `매이트 모드`로 즉시 전환된 후 백그라운드 감시 자격을 평가합니다. 고정 오브제/매이트 설정 및 TV 동작은 원본대로 보존됩니다.
- **Foreground Service 마이크 감시**: Android 백그라운드 마이크 실행을 위해 low-importance 알림 채널 `stand_mate_monitoring` ("매이트 모드 소리 감시")과 `FOREGROUND_SERVICE_TYPE_MICROPHONE`을 사용하는 `MateMonitoringService`를 구현했습니다. Activity가 포그라운드에 진입하면 서비스를 종료하고 화면 밖으로 나갈 때 적격하면 서비스를 가동합니다.
- **onStop/onStart 세션 연속성**: 백그라운드 감시가 적격한 경우 오디오 캡처를 끊지 않고 동일한 매이트 세션을 유지하여 아침 복귀 시 세션이 분할되거나 중복 생성되지 않도록 보장합니다.
- **정직한 단일 감시 상태 계약**: 기기 상태 진실(세션 활성, 모드, 마이크 권한, AudioRecord 상태, 노이즈 캘리브레이션, 일시중지 등)로부터 순수 함수 `MateMonitoringStatusPolicy`를 통해 단일 감시 상태를 산출하고 홈 상단 및 포그라운드 알림에 동일한 한글 상태(`소리 감시 중`, `방 소리 익히는 중`, `소리 저장 중`, `마이크 권한 필요`, `감시를 시작하지 못했어요`, `감시 일시 중지`)로 표시합니다.
- **조용한 밤 증거 및 0개 클립 잠소리 세션 투명성**: 세션 메타데이터에 `SessionMonitoringHealth`, 감시 시간, 실패 사유를 영속화하여 V3 manifest로 저장/복원합니다. 정상 감시되었으나 녹음할 소리가 없었던 세션은 `"감시는 정상적으로 진행됐고 저장할 소리가 없었어요"`를 표시하고, 권한 누락이나 초기화 실패 등으로 감시가 동작하지 않은 세션은 실패 사유를 정직하게 노출하여 조용한 밤으로 오인되지 않도록 합니다.
- **오프라인·개인정보 보호 원칙 유지**: 모든 수면 소리 및 설정은 기기 로컬에만 보관하며 외부 통신, 분석 SDK, 클라우드 업로드가 일절 없습니다.

## 2026-08-28 Android 라디오 설정 이동

- Android 휴대전화·태블릿·Google TV는 사용자 라디오 채널을 `S.tand-Radio.standradio.json`으로 내보내고 다시 가져올 수 있다.
- Google TV는 같은 Wi-Fi에서 일회용 QR 업로드 페이지를 열어 휴대전화의 파일을 직접 받을 수 있으며, 외부 서버·계정·클라우드 업로드를 사용하지 않는다.
- 가져오기는 미리보기와 명시적 확인을 거치며 기존 채널을 자동으로 덮어쓰거나 자동 재생하지 않는다.
- Android와 Apple은 같은 `s.tand-radio` v1 파일 규격을 사용하며 사용자 소유의 오래된 `http://` 스트림을 경고와 함께 지원한다.

## 2026-08-20 iPhone 가로 기본 배치 누락 보완

- iOS `1c92b69`의 `StandScreenLayout.landscape` 수치를 Android 가로 기본 배치에 직접 대응했다. 시계·초·날씨 3조각·날짜·상태·밝기 기준·배터리·두 라디오의 위치와 크기가 대상이다.
- iOS와 같은 일회성 가로 기본값 마이그레이션을 추가해 기존 설치에서도 가로 배치만 새 기준으로 바꾸고 세로 배치와 글꼴·대기 시간 등 다른 선택은 유지한다.
- iPhone 가로의 고정 음악 카드 옆 제어 버튼은 iOS의 `57.12`를 기준으로 이식한 뒤,
  Android에서 1.3배 글꼴의 두 줄 문구를 온전히 표시하라는 사용자 지시에 따라 짝수 값
  `68dp`로 넓혔다.
- versionCode 57에서는 첨부된 iPhone 가로 원본(1280×588, SHA-256 `325504f0f34b5cef8db1ce4f7301af8fc13d1a52ca0b923e901982889631dbce`)을 추가 기준으로 삼았다. 오른쪽 잠소리·보이소·설정 3개 버튼을 고정하고, 왼쪽 음악 채널만 고정 영역 안에서 좌우 드래그되도록 입력 범위와 clipping을 분리했으며 24/28pt 대응 양끝 페이드를 적용했다.
- 관련 `ScreenLayoutTest`·`AppPoliciesTest`와 `assembleDebug`, 가로 슬라이딩 계측 테스트 소스 빌드가 통과했고 `SM-F968N`에 versionCode 57 설치·실행을 확인했다. 슬라이딩 계측 실행은 검증 도중 기기 디스플레이가 꺼져 Compose hierarchy를 만들지 못해 환경 실패했으며, 이를 기능 통과로 과장하지 않는다. iOS와 Android의 최신 실화면 paired capture는 별도 검증으로 남는다.

- 밝기·모드 핵심 경로: 자동 모드에서 Android 시스템 밝기를 앱의 기본 조명과 홈 하단 표시에 반영하고, 조도 센서·카메라 대체 판정은 오브제/매이트 전환에 유지한다. 세부 표와 미검증 실기기 항목은 `BRIGHTNESS_MODE_PARITY_MATRIX.md` 참조.

## 2026-08-15 iOS 0.29.5 잠소리·수면 리포트 델타

- 기준은 iOS `main`의 `3c2aa8f`와 그 위 최신 작업 트리다. iOS 작업 트리는 읽기 전용으로
  확인했으며 Android 이식 중 변경하지 않았다.
- 기존 녹음 화면을 `수면 리포트`와 `잠소리 관리` 두 페이지로 분리했다. 첫 페이지는 최근
  잠자리의 기록 구간, 소리 후보, 화들짝 반응, 12구간 활동 분포, 가장 활동이 몰린 시간과
  시간당 이벤트를 보여 준다.
- 최근 잠자리의 소리를 발생 순서대로 이어 듣고 텍스트 리포트를 Android 공유 시트로 보낼 수
  있다. 리포트에는 의료 진단이 아니라는 안내를 포함한다.
- 관리 페이지에는 잠자리·원본 개수와 실제 오디오 파일 용량을 표시하며 기존 합치기, 선택,
  삭제, 개별 재생·공유 기능을 보존했다.
- 홈과 설정의 사용자 표시를 `잠소리 확인`, `잠소리 N개`, `잠소리 열기`로 맞추고 녹음 화면
  제목을 `잠소리`로 변경했다. 기존 작업 트리에 있던 만든 사람 GitHub 링크도 보존했다.
- iOS와 같은 12구간 분석 정책의 JVM 회귀 테스트와 `assembleDebug`가 통과했다. SM-F968N에서
  빈 리포트, 채워진 리포트, 잠소리 관리의 세로 화면 캡처를 검수했다. 가로·600dp 이상 시각 비교,
  Android 공유 시트와 실제 오디오 연속 재생은 대기다. 대응 상태와 재검수 항목은
  `MATCHUP_UI_REPORT.md`에 기록했다.

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
| H4 길게 누르기 | 2.0초 정지 터치로 전 화면 편집 진입, 터치 slop 초과 이동·멀티터치 시 취소와 햅틱 연결 | 정책/JVM | 미실행 | 미실행 | 편집/시트 위 비활성 조건 |
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
| 설정 정보 구조 | 구현 | 카드 순서 테스트 통과 | 대기 | 대기 | Hero/인라인 라디오/별도 관리·추가·수정/화면·권한·수면·정보와 만든 사람 GitHub 링크가 존재 |
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
| TV 플랫폼 | tvOS 별도 타깃 없음 | Android TV / Google TV 지원 (Leanback Launcher, D-pad 초점, 10-foot UI) | Android 확장. TvUiModePolicy, standFocusable, 리모컨 대체 제어 적용 |

## 앱 내부 브라우저 상세 감사

| 항목 | Android 소스 | 자동 검증 | 남은 검증 |
|---|---|---|---|
| Google 홈·주소·검색 | 구현 | JVM 통과 | 실제 검색 |
| 기본 즐겨찾기 5개 | iOS와 같은 순서/URL | JVM 통과 | 패널 표시·탭 |
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

- Android versionName `0.0.1`, versionCode `49`, 빌드 표기 `0.0.49`.
- versionCode 49에서 중복 mesh event가 참여자 상태를 갱신하지 않도록 보강했고, iOS의 대문자 UUID와 Android의 소문자 UUID를 같은 source/event로 정규화한다. 사람 목록은 source ID 기준 한 행과 경로만 표시하며 연결 수 상세 드롭다운은 제공하지 않는다. 백그라운드
  매이트 모드의 소리 알림은 고우선 알림·진동·두 번의 띵동과 알림 탭 후 화들짝 화면 진입을 제공한다.
- 보이소 화들짝 조명은 일반 움직임·핑거스냅에 2초 저조도→최대 40%, 큰소리·지속 소리에 1초→100%를 사용하고 모두 10초 안에 기존 모드·잠금으로 돌아온다. 일반 반응은 강도 조절형 torch에서만 낮은 세기를 사용한다.
- versionCode 49의 기기 설치·실행은 다음 설치 단계에서 확인한다.
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
# 2026-08-17 iOS 작업 트리 델타

| 항목 | Android 대응 | 검증 |
|---|---|---|
| 홈 좌우 드래그 시스템 볼륨 | `AudioManager.STREAM_MUSIC` 현재값을 기준으로 조절하고 HUD를 `시스템 볼륨`으로 표시 | 관련 JVM 테스트·컴파일 통과 |
| 공통 음량 정책 명칭 | `RadioVolumePolicy`를 `VolumeAdjustmentPolicy`로 변경하고 라디오 플레이어에도 공유 | 관련 JVM 테스트 통과 |
| 편집 패널 자유 배치 | 저장 좌표를 캔버스·안전영역으로 제한하지 않도록 변경 | 관련 JVM 테스트 통과 |
| UI 카탈로그 고정 날짜 | Android는 카탈로그 인자를 전달할 때만 고정 날짜를 사용하므로 기존부터 일치 | 소스 역대조 |

## 2026-08-20 iOS 1.0.0 (0.32.5) 음악 6카드·고정 스트립·잠소리 스와이프 삭제 델타

iOS 기준: `../S.tand`의 `1c92b69`, `1.0.0 (0.32.5)`. `AppSettings.swift`, `RootView.swift`,
`SettingsView.swift`, `RecordingsView.swift`, `StandViewModel.swift`,
`STandTests/AudioAnalysisTests.swift`를 읽기 전용으로 대조했다. 기존 미커밋 밝기/모드 변경
(`StandViewModel.kt`, `StandPolicies.kt`, `StandHomeScreen.kt`, `StandUiState.kt`,
`AppPoliciesTest.kt`)은 되돌리지 않고 그 위에 이번 델타를 적용했다.

| 항목 | Android 대응 | 근거/검증 |
|---|---|---|
| 6카드 음악 채널 모델 | `HomeMusicChannelSelection`에 `radioSlot`을 추가하고 `HomeMusicChannelPolicy.normalized`를 iOS `normalizedHomeMusicChannels`와 동일한 순서 규칙(요청 순서 유지 → 없는 Spotify/YouTube Music 보강 → 빈 슬롯 포함 4개 라디오 슬롯 보강)으로 이식 | `구현`·`단위`(`HomeMusicChannelPolicyTest`), 실기기 대기 |
| 라디오 채널 4개 확장 | `AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT`를 2→4로 올리고 기존 2개 슬롯 저장값·`SettingsRepository`의 `internetRadioId/Name/Url.<index>` 로딩 루프가 그대로 4개까지 확장되도록 유지 | `구현`·`단위`, 실기기 대기 |
| 홈 음악 채널 저장 키 이전 | `homeMusicChannel.0`..`.5` 6개 키로 확장하고 `HomeMusicChannelSelection.decode`가 구 `radio:<id>` 형식(슬롯 없음)과 신 `radio:<slot>:<id>` 형식을 모두 복호화 | `구현`·`단위`(레거시 디코드 테스트) |
| 고정 수평 음악 스트립 | 로고/헤더 아래 `MusicChannelStrip`을 새로 추가하고 기존 캔버스 이동식 `radio`/`secondaryRadio` 패널 렌더링을 제거. 간격 8dp, 좌우 여백 12dp, 카드 높이 60dp, 카드 폭 임계값·가로 폰 0.8배율, 클램프된 가로 드래그 스크롤을 `MusicChannelStripLayoutPolicy`로 이식. 1.3배 글꼴에서도 중앙 라인과 겹치지 않도록 첫 줄 `아이콘 + 제목`, 11dp 중앙 여백, 둘째 줄 상태로 고정하고 묶음을 2dp, 상태 문구를 추가 1dp 위로 보정했다. 등록된 라디오 카드는 터치 영역을 좌우 50:50으로 나눠 왼쪽은 재생/일시정지, 오른쪽은 재생 중 다음 등록 라디오(정지 중 누른 라디오 재생)로 동작한다. 홈 하단에는 내부 빌드 시각 대신 사용자용 `versionName`과 현재 밝기를 표시한다 | `구현`·`단위`(`AppPoliciesTest`의 strip·다음 라디오 정책 테스트)·에뮬레이터 세로/가로 시각 확인, 실기기 시각 검증 대기 |
| 화면 편집에서 라디오 패널 제거 | 최신 iOS `RootView.swift`에는 이동 가능한 라디오 편집 패널이 더 이상 존재하지 않는다. Android `ScreenEditorScreen.kt`의 `EditorPanelKey.Radio`/`SecondaryRadio`와 관련 병합·분리(`RadioGroupPolicy`) 연결을 제거했다 | `구현`, 실기기 대기 |
| 설정 음악 카드 6슬롯 | `SettingsScreen.kt`의 홈 버튼 배치를 2개→6개 카드로 확장하고 제목을 iOS 문구 `홈 음악 채널 순서`로 맞췄다. iOS는 드래그 재정렬(`moveHomeMusicChannel`)을 쓰지만 Android는 기존에 있던 카드별 드롭다운 선택 방식을 유지해 같은 결과(카드 순서 변경)를 접근성 친화적으로 제공한다. 이 상호작용 차이는 의도적 플랫폼 차이로 문서화한다 | `구현`, 실기기 대기 |
| 잠소리 좌측 스와이프 즉시 삭제 | `RecordingSwipeDeletePolicy`(임계 56dp, 최대 노출 112dp, 좌우 우세 판정)를 이식하고 `RecordingsScreen.kt`의 세션·기타·합본 목록 각 행에 `SwipeToDeleteRow`를 적용했다. 접근성 커스텀 액션 이름은 `바로 삭제`로 iOS와 동일하며, 기존 명시적 삭제 버튼·확인 다이얼로그 흐름은 그대로 보존했다 | `구현`·`단위`(`AppPoliciesTest`의 스와이프 판정 테스트), 실기기 제스처 검증 대기 |
| 하단 버튼 명칭 | `잠소리`/`설정`/`보이소`로 정확히 맞추고 기본 순서를 iOS `[.recordings, .boyiso, .settings]`와 동일하게 `StandControlKind.DefaultOrder`를 `[RECORDINGS, BOYISO, SETTINGS]`로 수정 | `구현`·`단위`(`AppPoliciesTest`) |
| 폰 가로 고정 컨트롤 | `PhoneLandscapeSideControlsPolicy.isEnabled(!isPortrait && !isExpandedWidth)`를 이식해 600dp 미만 가로에서는 음악 스트립 옆에 고정 세로 컨트롤 칼럼을, 600dp 이상은 기존 적응형 하단 배치를 유지 | `구현`·`단위`, 실기기 시각 검증 대기 |
| MusicKit 다음 곡 동작 | Android에는 Apple MusicKit에 해당하는 공개 인앱 재생·다음곡 API가 없다. Spotify/YouTube Music은 각 공식 앱을 여는 안전한 외부 실행만 지원하므로 iOS의 곡 제목 탭 재생/다음곡 구분(`ExternalMusicTitleTapPolicy`)은 이식하지 않았다. 이는 Android 공개 API 부재로 인한 플랫폼 제약이며, 추측 구현을 만들지 않고 기존 "앱 열기" 동작만 유지한다 | `플랫폼 차이`, 대체 불가 |

검증: `testDebugUnitTest` 177개, `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`가
통과했다. 동일 APK를 `SM-F968N`에 데이터 유지 설치하고 versionCode 55, 실행, 고정 6카드
스트립과 `[잠소리, 보이소, 설정]` 순서를 직접 확인했다. UI 카탈로그 11개 계측 테스트는 두 번
모두 실패 0이었지만, 트라이폴드의 다중 디스플레이 캡처가 두 번째 패스에서 앱 프레임을 다르게
기록해 exact SHA 안정성 판정은 보류한다.

## 2026-08-21 iOS 1.0.0 (0.33.0) 보이소 무전기 델타

iOS 작업 트리의 `BoyisoModels.swift`, `BoyisoConnectivityService.swift`, `BoyisoView.swift`,
`RootView.swift`, `StandViewModel.swift`와 관련 테스트를 읽기 전용 기준으로 대조했다.

| 항목 | Android 대응 | 근거/검증 |
|---|---|---|
| 세 번째 역할 `무전기` | wire value `walkie`, iOS와 동일한 제목·설명, 참가자 역할 섹션과 연결 상태를 추가 | 구현, 단위·계측 검증 예정 |
| 호출 입력 | 연결 중인 무전기 역할만 `walkie/press` 이벤트를 전송하고 3초 연타를 차단 | `WalkiePressPolicyTest`, protocol round-trip |
| 전송 경로 | 기존 LAN·BLE·인터넷 릴레이의 같은 암호화·중복 제거 경로로 전송하며 마이크를 시작하지 않음 | 소스 역대조, 실기기 교차 호출 예정 |
| 수신 반응 | 매이트 세션의 다중 자극 반응이 켜져 있으면 urgent 화들짝 조명, 2회 차임, 3초 고명도 호출 화면 | 정책 단위 테스트, 실기기 예정 |
| 백그라운드 | 앱이 보이지 않을 때 별도 고우선 무전기 알림으로 발신자와 `무전기 호출이 왔어요.` 표시 | 정책 단위 테스트, 알림 실기기 예정 |
| 보이소 화면·홈 타일 | 호출 버튼·안내·보조 톡톡 버튼, 무전기 참가자 그룹, 무전기 역할 홈 타일 탭 호출을 추가 | Compose 계측·폰/태블릿 캡처 예정 |

배포 후보는 versionName `0.0.1`, versionCode `58`, 빌드 표기 `0.0.58`이다.

## 2026-08-29 Android 2.3.5 보이소 v2 완성

| 항목 | Android 대응 | 근거/검증 |
|---|---|---|
| 역할·참여자 | `볼 사람`·`말할 사람`·`무전기`를 선택하고 같은 source ID의 Wi‑Fi·Bluetooth·인터넷 경로를 한 사람으로 표시 | `BoyisoStateTest`, 프로토콜 계측 테스트 |
| 무전기 호출 | 연결된 무전기만 호출하며 같은 기기의 연속 입력을 3초 동안 차단 | JVM 단위 테스트, protocol round-trip |
| 선택적 인터넷 중계 | 빌드에 `BOYISO_RELAY_URL`이 있을 때만 `wss` 중계를 사용하고, QR 키에서 파생한 익명 채널과 종단간 암호문만 전달 | `BoyisoManager`, `server/src/server.js`, Node 구문 검사 |
| 개인정보 보호 | 원음·녹음·공간 키·사건 본문을 서버로 평문 전송하지 않으며 기존 LAN·BLE 우선 연결을 유지 | 소스 역대조, 서버 운영 문서 |

배포 후보는 versionName `2.3.5`, versionCode `346507`, 빌드 표기 `202608291507`이다.

## 2026-08-25 Android TV / Google TV 확장

| 항목 | Android 대응 | 근거/검증 |
|---|---|---|
| TV 런처 선언 & 배너 | `LEANBACK_LAUNCHER` 카테고리 선언, `android.software.leanback` 및 `android.hardware.touchscreen` `required="false"`, `tv_banner.png` (320×180 xhdpi) 지정 | Manifest 선언, `TvUiModePolicyTest` |
| TV 플랫폼 감지 & 안전 영역 | `TvUiModePolicy.isTelevision()` (`UI_MODE_TYPE_TELEVISION`) 및 10-foot UI 오버스캔 마진 (가로 48dp, 세로 24dp) 적용 | `TvUiModePolicy`, `TvUiModePolicyTest` |
| D-pad 초점 가시성 | `Modifier.standFocusable()`을 통한 D-pad 초점 시 고대비 테두리 & 미세 확대 효과 | `TvFocusable.kt` |
| 터치 제스처의 리모컨 대체 | D-pad 선택으로 순환 조절 가능한 앱 밝기·시계 크기·테마 전환을 홈 제어부에 제공. 드래그 전용 화면 편집은 완결된 D-pad 편집 흐름이 없어 TV에서 숨김 | `TvUiModePolicy`, `StandHomeScreen.kt`, API 36 TV AVD |
| TV 미지원 하드웨어 제외 | 플래시·카메라 조도 측정·화면 회전 고정·AiShot 제어를 TV에서 숨김 처리하고, 카메라 권한 요청 흐름을 우회 | `TvUiModePolicy`, `MainActivity.kt`, `SettingsScreen.kt`, `BoyisoScreen.kt` |
| TV 홈 상단 여백 최적화 | Google TV 홈 상단 여백을 기존의 약 30%(11.4dp)로 줄여 헤더 브랜드와 음악 채널 스트립을 함께 상단으로 이동(폰/태블릿 간격 불변) | `TvUiModePolicy.TV_HOME_TOP_PADDING_DP`, `StandHomeScreen.kt`, `TvUiModePolicyTest` |
| TV 모드 명칭 및 불필요 제어 은닉 | TV 홈/설정에서 "오브제 모드", "매이트 모드" 등 모드 라벨 및 모드 전환 컨트롤을 제거하고, 보이소(Boyiso) 및 잠꼬대·코골이 수면 소리/녹음 제어와 설정 카드를 제외(폰/태블릿 및 내부 데이터 호환성 보존) | `TvUiModePolicy.allowedControls`, `allowedSettingsSections`, `SettingsScreen.kt`, `StandHomeScreen.kt`, `TvUiModePolicyTest` |
| TV 음악·시계·날씨 우선 & D-pad 탐색 | 6채널 음악 스트립, 시계, 날씨를 우선하고 잔여 제어부(설정, 테마 전환, 앱 밝기, 시계 크기)의 D-pad 포커스 및 리모컨 탐색을 보존 | `StandHomeScreen.kt`, `TvUiModePolicy` |
| TV 실행 검증 | Google TV API 36에서 첫 포커스, 권한, 시작·중지, D-pad 제어와 Back을 확인하고 Android TV API 36 런처에서 홈 배너 노출을 확인 | `.parity/evidence/2026-08-25/`, 실기기 촉감·제조사 런처 차이는 대기 |

## 2026-08-26 S.tand 2.2.1 릴리스 후보 및 홈 편집 제스처 보강

- 배포 메타데이터: versionName `2.2.1`, versionCode `342257`, Build-Number `202608261617`.
- 홈 편집 진입 제스처 보강:
  - 2.0초(`HomeEditGesturePolicy.HOLD_DURATION_MILLIS = 2000L`) 동안 이동 없는 정지 터치 유지 시에만 화면 편집 진입.
  - 가로 시스템 볼륨 조절 또는 세로 앱 밝기 조절 등 터치 slop을 초과하는 드래그/스크롤 동작 발생 시 편집 진입 타이머 즉시 취소 및 편집 모드 진입 방지.
  - 멀티터치(2포인터 이상 시계 배율 조절 등) 발생 시에도 편집 진입 즉시 취소.
  - Google TV / Android TV 환경의 D-pad 리모컨 동작 및 미지원 기능 정책(`TvUiModePolicy`) 완벽 보존.
  - TalkBack 접근성 액션("화면 편집 열기") 및 하단 제외 영역(104dp) 보존.
- 검증: `StandPoliciesTest` 단위 테스트 보강 완료, 실기기 런타임 캡처 및 제스처 트레이스는 미실행 상태로 보존.

## 2026-08-27 S.tand 2.2.2 릴리스 후보 (Google TV 하단 제어 폭 보강)

- 배포 메타데이터: versionName `2.2.2`, versionCode `343713`, Build-Number `202608271633`.
- Google TV 홈의 `설정`·`테마 전환`·`앱 밝기`·`시계 크기` 카드 크기와 내부 정렬을 통일하고, 설정 라벨 및 각 카드 보조 문구가 잘리지 않도록 간격·행간·하단 안전 여백을 조정했다.
- 배포 메타데이터: versionName `2.2.3`, versionCode `343760`, Build-Number `202608271720`. 대표님 지시에 따라 테스트·lint·화면 검증은 생략했다.
- Google TV / Android TV 홈 하단 콤팩트 제어 카드 폭 확장:
  - `StandHomeScreen.kt`의 `HomeControl`에서 비설정 콤팩트 TV 카드 폭을 `84.dp`에서 `112.dp`로 확장하여 "테마 전환", "앱 밝기", "시계 크기" 한글 라벨이 잘림 없이 온전히 표시되도록 수정.
  - 설정 버튼(정사각형 `52.dp × 52.dp`), 콤팩트 TV 카드 높이(`52.dp`), 일반 TV 크기(`160.dp × 100.dp`), 휴대전화/태블릿 레이아웃(`98.dp × 66.dp`) 및 D-pad 포커스/리모컨 제어 동작은 기존과 동일하게 유지.
- 검증: 소스 레벨 구현 확인 완료, 물리적 Google TV / Android TV 실기기 검증은 미실행(대기) 상태.

## 2026-08-29 Google TV UI 정제 (배터리·매이트 모드 제거, 리모컨 포커스 개선, 날씨 확대 & 시계 하단 이동)

- 배터리 UI 및 동작 제거:
  - Google TV 상단 헤더 배터리 아이콘/퍼센트 표기, 캔버스 배터리 플로팅 패널, 배터리 보호 상태 배너를 TV 경험에서 완전히 은닉.
  - TV에서는 `BatteryMonitor` 수집·시작과 저전력 보호 중단 로직을 비활성화하고, 휴대전화·태블릿에서만 기존 배터리 보호를 유지 (`TvUiModePolicy.supportsBattery(isTelevision)`).
- 매이트 모드(Mate Mode) 제거 및 오브제 전용 고정:
  - Google TV에서는 항상 `오브제 모드` 단일 모드로 작동하도록 고정하고, 시계 터치/클릭을 통한 오브제-매이트 토글 경로 차단.
  - TV 시작 화면 문구에서 매이트/잠자리 언급을 제거하고 "음악·시간·날씨를 편안하게 비춥니다."로 통일 (`TvUiModePolicy.supportsMateMode(isTelevision)`).
- 리모컨 포커스 및 선택 상태 검은 배경 제거:
  - Google TV 전체 테마에서 Foundation 눌림 표시와 Material 3 리플을 비활성화해 리모컨 D-pad 선택 시 생기던 검은 배경 채움을 모든 버튼에서 제거.
  - `Modifier.standFocusable()`의 고대비 테마 테두리(2.5dp)와 미세 확대(1.04배)를 통해 비-블랙 스타일의 명확한 포커스 어포던스를 제공.
- 날씨 패널 1.5배 확대 및 시계 패널 하단 이동:
  - Google TV 대시보드의 현재 저장 레이아웃에 `TvClockAlignmentPolicy`를 적용해 날씨 패널을 기존 대비 가로·세로 1.5배 확대(기본값 `scale = 0.55f * 1.5 = 0.825f`).
  - 시계 패널 세로 위치(`clock.y`)를 날씨 패널의 증가 높이(`addedWeatherHeightDp = 33.916575dp`, 캔버스 높이 540dp 기준 `+0.06280847f`)만큼 정확히 하단으로 이동 (`0.21553229f` → `0.27834076f`).
  - 초(seconds) 패널은 이동된 시계 패널 아래로 `TvClockAlignmentPolicy.calculateAlignedSecondsTransform`을 통해 자동 동기화 정렬.
- 휴대전화 및 태블릿 호환성 보존:
  - 모든 TV 전용 변경은 `isTelevision` 분기를 통해 안전하게 격리되어, 모바일 및 태블릿의 기존 배터리 표시, 매이트 모드 감지/전환, 화면 편집 및 기본 배치는 그대로 유지.
- 배포 후보: versionName `2.3.6`, versionCode `346696`, Build-Number `202608291816`.
- 검증: `TvUiModePolicyTest`, `ScreenLayoutTest` 단위 테스트 통과 및 소스 정합성 검증 완료. 실기기 검증은 대기 상태.

## 2026-08-29 Google TV 날씨-시계 간격 절반 축소 및 설정 버튼 리모컨 유휴 투명화

- 날씨-시계 세로 간격 50% 축소:
  - Google TV 대시보드에서 `TvClockAlignmentPolicy.calculateTvDashboardLayout`을 통해 날씨 영역 하단과 시계 상단 사이의 시각적 세로 간격(`visibleVerticalSpaceDp`)을 측정하고, 시계 위치를 보정하여 가시 세로 여백을 정확히 기존의 절반(`50%`)으로 축소.
  - 초(seconds) 패널은 이동된 시계 패널 아래로 `TvClockAlignmentPolicy.calculateAlignedSecondsTransform`을 통해 계속 자동 동기화 정렬.
- 하단 설정 버튼 리모컨 유휴 투명화 (Idle Auto-Hide):
  - Google TV 환경에서 리모컨 조작이 유휴 상태일 때 하단 설정 버튼(및 TV 보조 제어)을 완전 투명(`alpha = 0f`) 처리하여 시계·날씨·음악 중심의 미니멀 오브제 디스플레이를 제공.
  - 리모컨 방향키/선택/기능키 등 모든 버튼 및 내비게이션 입력 시 즉시 가시화(`alpha = 1f`, 부드러운 트윈 애니메이션)되며, 입력 시점부터 5초(`REMOTE_INACTIVITY_DELAY_MS = 5000L`) 후 다시 투명화. 연속 입력 시 대기 타이머 자동 재시작.
  - 투명 상태에서도 D-pad 포커스 탐색과 접근성 시맨틱스(Accessibility Semantics)를 온전히 유지.
- 휴대전화 및 태블릿 호환성:
  - 모든 변경은 `isTelevision` 분기 및 `TvUiModePolicy`로 격리되어 모바일 및 태블릿의 기존 레이아웃·동작에 전혀 영향을 주지 않음.
- 검증: `TvUiModePolicyTest`에 날씨-시계 가시 간격 50% 축소 및 리모컨 유휴 알파/지연 정책 테스트 추가. 소스 레벨 정합성 검증 완료.

## 2026-08-29 Android 휴대전화 음악 패널 재정렬 정상화

- 원인 분석 및 해결:
  - `HomeMusicChannelPolicy.normalized`가 `requested` 순서 정규화 시 미배치 라디오를 탐욕적으로 첫 빈 슬롯에 채우면서, 빈 슬롯이나 라디오 슬롯을 앞뒤로 이동했을 때 슬롯 순서가 뒤바뀌거나 복구되던 회귀를 해결.
  - `validRadioIDsInRequested`를 먼저 산출하여 요청된 모든 유효 라디오 ID를 보호하고, `unplacedRadios`만 새 빈 슬롯에 할당하도록 정제.
  - `HomeMusicChannelPolicy.assigning`에서 `radioSlot` 식별자를 대조하도록 보강하여 빈 라디오 슬롯 간 이동 및 임의 위치 재정렬이 온전히 동작하고 즉시 반영·영속화되도록 복원.
- 검증: `HomeMusicChannelPolicyTest`에 외부 서비스, 등록 라디오, 빈 슬롯 간 순서 변경 및 직렬화 라운드트립 단위 테스트 추가.

## 2026-08-29 Android 휴대전화 설정 음악 카드 직접 드래그앤드롭 재정렬 완성 (iOS 동등성)

- 직접 드래그앤드롭(Direct Drag-to-Reorder) 상호작용:
  - 기존 탭 핸들 확장 위/아래 화살표 UI(`reorderingMusicSlot`)를 완전히 제거하고, iOS와 동일하게 카드 길게 누르기(Long-press) 또는 드래그 핸들 직접 드래그로 즉시 수직 이동되도록 구현.
  - 드래그 중인 카드에 대해 `zIndex(10f)`, 부드러운 확대 스케일(`1.025f`), 그림자 입체감(`elevation 8.dp`), 테두리/배경 하이라이트 및 실시간 수직 이동(`translationY`) 피드백 적용.
  - 드래그 중 다른 카드들은 스프링 애니메이션(`animateFloatAsState`)으로 비어 있는 슬롯 위치로 부드럽게 밀려나는 변위(displacement) 효과 제공.
  - 슬롯 경계 이동 시 `HapticFeedbackType.TextHandleMove`, 드래그 시작 및 완료 시 `HapticFeedbackType.LongPress` 햅틱 피드백 연결.
- 접근성 및 TV/모바일 격리:
  - TalkBack 등 스크린 리더 환경을 위해 `CustomAccessibilityAction`("위로 이동", "아래로 이동")을 카드 및 드래그 핸들에 제공하여 시맨틱스 접근성 완벽 유지.
  - 재생/일시정지 및 인터넷 라디오 인라인 편집 연필 버튼 탭 상호작용 온전히 보존.
  - Google TV D-pad 포커스/내비게이션에 간섭하지 않도록 설계.
  - 하단 안내 문구를 iOS 동등 문구("길게 눌러 홈 순서를 바꾸고, 라디오의 연필을 누르면 같은 자리에서 바로 수정할 수 있습니다.")로 동기화.
- 순서 영속화 및 정책:
  - `HomeMusicChannelPolicy.moving`을 추가하여 6개 카드 연속 재정렬을 수행하고 `StandViewModel.moveHomeMusicChannel`을 통해 기존 단일 저장소(`SettingsRepository`) 경로로 즉시 영속화.
- 검증: `HomeMusicChannelPolicyTest`에 하향/상향 직접 드래그, 경계값, 슬롯 중심점/변위 계산 단위 테스트 추가.

## 2026-08-29 Google TV 설정 화면 리모컨 포커스 색상 강조

- Google TV 설정 화면에서 D-pad로 이동할 때 현재 선택된 버튼·카드·스위치 행에 테마 포인트색 배경과 2.5dp 포커스 링, 1.04배 확대를 함께 표시한다.
- 음악 재생·수정·순서 이동, 테마, 설정 스위치, 링크, 다이얼로그 등 리모컨이 도달하는 설정 조작부에 같은 시각 규칙을 적용했다.
- `settingsFocusable(isTelevision)` 분기로 설정 화면의 TV 환경에만 적용하며 홈 화면과 휴대전화·태블릿의 터치·드래그 동작은 유지한다.
- 배포 후보: versionName `2.3.9`, versionCode `346850`, Build-Number `202608292050`.
- 검증: `testDebugUnitTest`, `lintDebug`, `assembleDebug` 통과. 연결된 실제 Google TV가 없어 제조사 TV 실기기 리모컨 확인은 미실행 상태다.

## 2026-08-29 Google TV 전체 화면 리모컨 포커스 색상 확대

- 공통 `standFocusable`이 Google TV에서 기본적으로 테마 포인트색 배경, 2.5dp 포커스 링과 1.04배 확대를 표시하도록 중앙화했다.
- 홈·시작·업데이트 창·라디오 추가/브라우저·공유 수신·글꼴/저작권·잠소리/수면 리포트·화면 편집 등 TV에서 접근 가능한 조작부에 포커스 표시를 보강했다.
- `TvUiModePolicy.isTelevision`으로 공통 포커스 색상을 TV에만 적용하며 휴대전화·태블릿의 터치와 직접 드래그 동작은 유지한다.
- 배포 후보: versionName `2.3.10`, versionCode `346859`, Build-Number `202608292059`.
