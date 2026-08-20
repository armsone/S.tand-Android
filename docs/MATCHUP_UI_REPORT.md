# iOS ↔ Android Matchup UI 보고서

기준일: 2026-08-15 KST

- iOS 기준: `../S.tand` `main@2335ec382883c769bf70113ce6a724014af4eaa9`, `1.0.0 (0.29.5)`, sourceStateSha256 `c0c90c049857a0ac56d94b44d7a93e78089ce7fcffad22905bd45bc7d5de22b3`
- Android 기준: 현재 작업 트리, `0.0.1` / versionCode 52
- 공통 fixture/profile: `ui_catalog_v2`, `ko-KR`, `Asia/Seoul`, dark, animations off, fontScale 1.0, `2026-08-15T07:42:05+09:00`

## 재현 가능한 캡처

- iOS: `../S.tand/artifacts/ui-catalog/manifest.json`, iPhone 17 Pro, 15상태. 연속 2회 중 12상태는 exact SHA stable이다. `radio_channel_editor`, `restore_confirmation`, `settings_lower_sections`는 XCUITest scroll 정지 위치만 달라 element anchor 정렬 대상으로 분류한다.
- Android: `artifacts/matchup/android/R3KYB061JTZ/manifest.json`, SM-F968N / Android 16, 15상태. 두 pass의 PNG와 metadata가 모두 exact SHA stable이며 `stable=true`다.
- Android 재생성: `MATCHUP_ANDROID_SERIAL=R3KYB061JTZ ./scripts/capture-matchup-ui-catalog.sh`
- Android trace: `docs/matchup-traces/android.jsonl`. 카탈로그가 실제 수행한 click/scroll/orientation 입력과 direct-fixture render를 구분해 기록한다.

## 구현·검증 결과

- 15개 shared semantic ID 전부 Android에서 렌더·캡처됐다.
- `시계 글꼴`은 별도 route, 10개 3열 split-card preview, 정확한 순서·표시명·선택 check로 구현했다.
- `폰트 저작권`은 별도 route, 9개 상세 route와 선택 가능한 전문으로 구현했다.
- 라디오 shared 상태는 설정의 첫 채널 `수정` inline editor와 그 안의 삭제 확인으로 고정했다.
- 수면 리포트/잠소리 관리, 고정 녹음 fixture, 홈 고정 시각·날씨·배터리·라디오 상태를 결정론 카탈로그에 포함했다.

## 강화 matchup 판정

`rendered` 또는 `repeat-stable`은 `matched`와 다르다. 현재 paired 원본 육안 대조 결과 전체 matchup은 **미완료**다.

| 영역 | 확인 결과 | 판정 |
|---|---|---|
| 글꼴 선택 | 10개 순서·label·선택·3열 구조 일치. app bar 높이, back glyph, 카드/배경 색과 여백 차이 존재 | partial / unresolved |
| 홈 | 주요 정보와 상태는 대응. header·날씨·하단 control 순서/아이콘/path/세부 spacing과 fixture 표시가 다름 | unresolved |
| 설정 | title/완료, hero, theme/font route는 대응. 카드 색·폭·세로 밀도·toggle·아이콘 geometry가 다름 | unresolved |
| 라디오 편집/삭제 | 합의된 inline route와 기능 상태는 대응. 상단 정보 카드, 입력 surface, dialog anatomy 및 glyph가 다름 | unresolved |
| 녹음·보이소·권한 | shared 상태 렌더는 존재. app-owned 아이콘과 atomic geometry의 paired 확정이 남음 | unresolved |

Material icon은 의미가 같아도 SF Symbol과 path/bounds/stroke가 달라 matched로 처리하지 않았다. Apple SF Symbols를 복제하지 않았으며, `docs/MATCHUP_MATRIX.md`의 app-owned icon 행을 모두 unresolved로 유지한다. iOS의 scroll-offset 불안정 3상태도 exact SHA matched라고 주장하지 않는다.

## 남은 게이트

- `docs/MATCHUP_MATRIX.md`와 iOS `docs/MATCHUP_IOS_STATE_INVENTORY.md`에 열거된 additional distinct states의 paired capture/기능 trace
- 합법적 custom vector 또는 공용 asset을 사용한 app-owned 아이콘 path/bounds/stroke 일치
- phone 외 600dp tablet, large-text, TalkBack, 실제 오디오·공유·권한·센서 lifecycle 검증
- iOS/Android 서로 다른 pixel profile은 임의 리스케일하지 않고 component anchor와 앱 bounds를 기준으로 비교

따라서 이번 산출물은 source/render coverage와 재현 가능한 Android capture 기반을 완성했지만, 강화 matchup의 전체 parity 완료 증거는 아니다.

2026-08-20에는 `SM-F968N`에서 최신 6카드 스트립 카탈로그 11개 테스트를 연속 2회 실행해
각 패스 실패 0을 확인했다. 다만 트라이폴드의 두 물리 디스플레이 중 캡처 대상이 패스 사이에
달라져 `home_portrait`의 앱 프레임이 달라졌으므로 exact SHA stable 판정은 올리지 않았다.

## 2026-08-20 추가 노트

iOS `1c92b69` (`1.0.0 (0.32.5)`)에서 홈 라디오 표시가 이동식 2패널에서 고정 6카드 수평
스트립으로 바뀌었고, Android도 같은 소스 구조(`MusicChannelStrip`)로 이식했다. 위 15상태
카탈로그의 `home_*` fixture와 manifest는 아직 이 변경 전 캡처이므로, 다음 재캡처 라운드 전까지
홈 행의 판정은 `unresolved`를 유지한다. 잠소리 관리의 좌측 스와이프 즉시 삭제(`바로 삭제`
접근성 액션)도 이번에 새로 추가된 소스 동작이라 기존 `recordings_management` capture에는
반영되어 있지 않다.
