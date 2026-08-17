# S.tand 음악 매치업 기준

기준 iOS 소스는 `../S.tand`의 현재 음악 구현이며 Android 구현은 Spotify와 YouTube Music을 대상으로 한다. 이 문서는 소스 매핑이며 paired capture 전에는 시각 일치를 완료로 판정하지 않는다.

| 원자 항목 | iOS | Android | 상태 |
|---|---|---|---|
| 설정 카드 제목 | 음악 | 음악 | source matched |
| 외부 서비스 순서 | Apple Music, Apple Music Classical | Spotify, YouTube Music | platform service substitution |
| 홈 음악 버튼 | 두 슬롯, 서비스·라디오 선택 | 두 슬롯, 서비스·라디오 선택 | source matched |
| 서비스 행 | 58dp 이상, 10dp 좌우, 15dp 모서리 | 동일 토큰 | implemented from source |
| 활성 서비스 | 앱 내부 재생/일시 정지 | 공식 앱에서 로그인·재생 후 S.tand 복귀 | intentional platform constraint |
| 감지·녹음 정책 | 외부 음악 세션 동안 중단 | 음악 듣기 모드 동안 중단 | source matched |
| 내부 라디오 | 음악 카드 안의 inline 편집/재생 | 음악 카드 안의 inline 편집/재생 | source matched |
| 앱 아이콘 | SF Symbol | Material 임시 아이콘 | unresolved icon path/bounds/stroke |

## Android 기능 trace

1. 설정 → 음악 → Spotify 또는 YouTube Music을 누른다.
2. 앱이 설치되어 있으면 공식 앱을 열고 음악 서비스가 요구하는 로그인·재생을 진행한다.
3. S.tand로 돌아오면 해당 서비스가 `음악 듣기 모드`로 표시되고, 소리 감지와 자동 녹음은 중단된 상태를 유지한다.
4. 활성 행의 닫기 버튼 또는 홈 음악 버튼 길게 누르기로 음악 듣기 모드를 끝낸다.
5. 앱이 없으면 Play 스토어 설치 화면을 열며 음악 듣기 모드는 활성화하지 않는다.

Spotify App Remote는 앱별 Client ID와 redirect URI 등록이 있어야 한다. YouTube Music은 동등한 공식 앱 내 재생 제어 API가 없으므로, 현재 버전은 재생 상태를 추측하거나 비공식 제어를 하지 않는다.
