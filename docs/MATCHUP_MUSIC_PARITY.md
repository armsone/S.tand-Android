# S.tand 음악 매치업 기준

기준 iOS 소스는 `../S.tand`의 `1c92b69` (`1.0.0 (0.32.5)`)이며 Android 구현은 Apple Music/Apple
Music Classical을 Spotify/YouTube Music으로 대체한다. 이 문서는 소스 매핑이며 paired capture
전에는 시각 일치를 완료로 판정하지 않는다.

| 원자 항목 | iOS | Android | 상태 |
|---|---|---|---|
| 설정 카드 제목 | 음악 | 음악 | source matched |
| 외부 서비스 순서 | Apple Music, Apple Music Classical | Spotify, YouTube Music | platform service substitution |
| 홈 음악 카드 구성 | 6카드: 외부 서비스 2개 + 안정적 라디오 슬롯 4개(빈 슬롯 포함) | 동일 6카드 구성 | source matched |
| 홈 음악 표시 | 로고/헤더 아래 고정 수평 스트립, 카드 8dp 간격·12dp 좌우 여백·60dp 높이 | 동일 토큰의 `MusicChannelStrip` | implemented from source |
| 가로 폰 카드 폭 | 기본 폭의 0.8배 축소 | `MusicChannelStripLayoutPolicy.PHONE_LANDSCAPE_CARD_WIDTH_SCALE = 0.8` | source matched |
| 설정 카드 순서 편집 | 드래그로 6카드 순서 변경(`moveHomeMusicChannel`) | 카드별 드롭다운 선택으로 6카드 순서 변경(`assignHomeMusicChannel`) | intentional interaction substitution, same outcome |
| 서비스 행 | 58dp 이상, 10dp 좌우, 15dp 모서리 | 동일 토큰 | implemented from source |
| 곡 제목 탭 동작 | 재생 중이면 다음 곡, 아니면 재생(MusicKit) | 공식 앱 열기만 지원 | 플랫폼 차이 — Android에 MusicKit과 동등한 공개 인앱 다음곡 API가 없어 이식하지 않음 |
| 활성 서비스 | 앱 내부 재생/일시 정지 | 공식 앱에서 로그인·재생 후 S.tand 복귀 | intentional platform constraint |
| 감지·녹음 정책 | 외부 음악 세션 동안 중단 | 음악 듣기 모드 동안 중단 | source matched |
| 내부 라디오 | 홈 스트립 카드 안의 등록/재생, 연필로 자리 유지 수정 | 홈 스트립 카드 안의 등록/재생·수정 | source matched |
| 앱 아이콘 | SF Symbol | Material 임시 아이콘 | unresolved icon path/bounds/stroke |

## Android 기능 trace

1. 설정 → 음악 → Spotify 또는 YouTube Music을 누른다.
2. 앱이 설치되어 있으면 공식 앱을 열고 음악 서비스가 요구하는 로그인·재생을 진행한다.
3. S.tand로 돌아오면 해당 서비스가 `음악 듣기 모드`로 표시되고, 소리 감지와 자동 녹음은 중단된 상태를 유지한다.
4. 활성 행의 닫기 버튼 또는 홈 음악 버튼 길게 누르기로 음악 듣기 모드를 끝낸다.
5. 앱이 없으면 Play 스토어 설치 화면을 열며 음악 듣기 모드는 활성화하지 않는다.

Spotify App Remote는 앱별 Client ID와 redirect URI 등록이 있어야 한다. YouTube Music은 동등한 공식 앱 내 재생 제어 API가 없으므로, 현재 버전은 재생 상태를 추측하거나 비공식 제어를 하지 않는다.
