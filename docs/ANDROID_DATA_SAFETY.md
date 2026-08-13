# Android 개인정보·Data Safety 근거

검토일: 2026-08-14 KST
소스 기준: versionName `0.0.1`, versionCode `44`, 빌드 `0.0.44`

이 문서는 Android 소스와 Play Console Data Safety 신고가 어긋나지 않도록 실제 권한, 저장,
기기 밖 전송 경로를 기록한다. 이 문서 자체가 Play Console 신고를 제출하거나 외부 서비스의
보관·처리 방침을 보증하지는 않는다.

## 권한과 사용 목적

| 권한 | 사용 목적 | 권한 거부 시 |
|---|---|---|
| `INTERNET` | 날씨, 사용자가 선택한 라디오 스트림과 앱 내부 브라우저 | 해당 네트워크 기능만 사용할 수 없음 |
| `RECORD_AUDIO` | 사용자가 켠 수면 소리 감지와 로컬 녹음 | 감지·녹음을 사용할 수 없음 |
| `ACCESS_COARSE_LOCATION` | 현재 날씨 요청용 대략적 위치 | 시계 등 나머지 기능은 유지 |
| `CAMERA` | 카메라 플래시를 이용한 조명 | 화면 조명 등 나머지 기능은 유지 |
| `REQUEST_INSTALL_PACKAGES` | GitHub에서 받은 새 S.tand APK의 Android 설치 화면 열기 | 자동 업데이트만 사용할 수 없음 |

첫 화면은 플래시·카메라·마이크·대략적 위치의 사용 이유를 권한창보다 먼저 설명한다. 사용자가
`권한 확인하고 시작`을 누르면 아직 허용하지 않은 카메라, 마이크, 대략적 위치 권한만 차례로
요청한다. 플래시와 카메라는 Android의 같은 `CAMERA` 권한을 사용한다. 일부 또는 전부를 거부해도
권한 순서를 끝낸 뒤 앱은 시작되고, 허용한 기능만 작동한다.
권한이 하나라도 없으면 최초 확인 때 안내하고, 이후에는 기기에만 저장한 무작위 3~7회 실행 간격으로
다시 안내한다. 화면 회전이나 같은 실행 중 화면 재진입은 실행 횟수에 중복 산입하지 않는다.
자동 앱 시작과 일반 기능 갱신은 권한창을 띄우지 않는다. 사용자가 첫 화면의 시작 버튼이나 설정의
권한 복구 버튼을 직접 누른 경우에만 Android 권한창을 연다.

계정, 광고 SDK, 분석 SDK, 자체 백엔드와 클라우드 업로드는 없다. Manifest의
`allowBackup=false`, `fullBackupContent=false`와 `data_extraction_rules.xml`은 앱의 파일,
데이터베이스, 설정을 cloud backup과 device transfer에서 제외한다.

## 저장과 기기 밖 전송

| 데이터·기능 | 앱 내부 저장 | 기기 밖 전송 |
|---|---|---|
| 수면 소리 녹음 | 앱 전용 `files/recordings` | 자동 전송 없음. 사용자가 공유를 누를 때 선택한 앱에 해당 파일만 FileProvider로 전달 |
| 설정·화면 배치·라디오 목록 | 앱 전용 저장소 | 자체 서버로 전송하지 않음 |
| 날씨 위치 | 마지막 대략 위치와 날씨를 앱 내부 캐시에 보관하고 기능 종료 시 제거 | 날씨 새로고침 때 위도·경도를 소수점 4자리로 반올림해 `api.open-meteo.com`에 HTTPS 전송. 위치 이름 변환은 Android Geocoder를 사용 |
| 앱 내부 브라우저 | 화면이 열린 동안 WebView 저장소 사용 | 사용자가 연 HTTPS 사이트에 일반 웹 요청 전송. 타사 쿠키, 파일 접근·선택·drop, 다운로드, 웹 카메라·마이크·위치는 차단 |
| 인터넷 라디오 | HTTPS 스트림 주소와 표시 설정을 앱 내부에 저장 | 재생할 때 사용자가 저장한 스트림 서버에 HTTPS 요청 전송 |
| 앱 업데이트 | 다운로드한 APK를 앱 캐시에 임시 보관하고 설치 후 OS가 관리 | 앱 시작 시 `api.github.com`에서 최신 Release를 확인하고, 사용자가 업데이트를 누르면 `github.com`에서 APK를 받음 |

업데이트 APK는 지정된 S.tand 저장소의 HTTPS Release만 허용한다. 파일명, versionCode, package name과
현재 설치 앱의 서명이 모두 맞지 않으면 설치 화면을 열지 않는다. GitHub 확인 실패는 앱의 기존 기능을
막지 않으며 계정·녹음·설정은 GitHub로 보내지 않는다.

브라우저를 닫거나 renderer를 다시 만들 때 로딩과 미디어를 중지하고 history, form data,
cache, cookies, WebStorage를 지운다. 브라우저에서 파일 업로드·다운로드를 허용하지 않는다.

## 파일 생성 시각 metadata

녹음 목록은 S.tand가 파일명에 넣은 생성 시각을 우선한다. 이전 형식처럼 파일명에서 시각을
읽을 수 없을 때만 앱 전용 녹음 폴더에서 이미 관리 중인 해당 파일의 `File.lastModified()`를
fallback으로 읽는다. 외부 저장소를 탐색하거나 이 metadata를 서버로 전송하지 않는다.

코드 근거는 `RecordingTimestampPolicy`이며 JVM 테스트는 다음을 고정한다.

- 앱 파일명의 시각이 `lastModified`보다 우선한다.
- 파일명이 이전 형식일 때만 양수인 로컬 `lastModified`를 사용한다.
- 유효한 시각이 없으면 `Instant.EPOCH`을 사용한다.

## 출시 전 Play Console 확인

- 현재 APK의 권한과 SDK 목록을 다시 확인한다.
- 대략 위치의 Open-Meteo 전송, Android Geocoder, 외부 웹 탐색·라디오 스트림을 당시 Play
  Console Data Safety 정의와 각 외부 서비스 방침에 따라 신고한다.
- 사용자가 직접 실행한 녹음 공유가 신고 문항에서 별도 공개 대상인지 당시 안내를 확인한다.
- 실제 Play Console 답변과 공개 개인정보처리방침을 이 문서의 데이터 흐름과 대조한다.
- 실기기 네트워크 관찰과 Play Console 제출을 하지 않았다면 완료로 표시하지 않는다.
