# S.tand iOS ↔ Android 강화 매치업 매트릭스

기준일: 2026-08-15 KST

- iOS 기준: `../S.tand` clean `main` / `2335ec382883c769bf70113ce6a724014af4eaa9` / `1.0.0 (0.29.5)`
- Android 기준: 현재 작업 트리 / `0.0.1` / versionCode 57

## 2026-08-20 추가 노트

iOS 기준이 `1c92b69` (`1.0.0 (0.32.5)`)로 이동하며 `home_portrait`/`home_landscape`의 라디오
fixture 전제(2 radios, 이동식 캔버스 패널)가 바뀌었다. 최신 Android는 이동식 `radio`/
`secondaryRadio` 캔버스 패널을 제거하고 로고 아래 고정 6카드 수평 스트립(`MusicChannelStrip`)을
사용한다. 아래 인벤토리의 `home_*` fixture 설명과 신규 paired capture는 다음 캡처 라운드에서
"4 radio slots, fixed music strip"으로 갱신해야 하며, 이번 소스 변경만으로는 표의 상태를
`matched`로 올리지 않는다. 자세한 소스 매핑은 `MATCHUP_MUSIC_PARITY.md`와
`docs/IOS_PARITY.md`의 2026-08-20 델타를 참조한다.
- 공통 fixture: `ui_catalog_v2`, `ko-KR`, `Asia/Seoul`, dark, animations off, fontScale 1.0
- 고정 시각: `2026-08-15T07:42:05+09:00`; state별 palette는 manifest의 `stateThemeOverrides`에 기록
- 추가 profile: large text, phone portrait/landscape, tablet 600dp+ portrait/landscape
- 판정 원칙: fresh paired PNG와 같은 입력의 기능 trace가 모두 없으면 `matched`로 판정하지 않는다.

## 증거 품질

기존 iOS 카탈로그 14장은 1206×2622 lossless PNG와 SHA-256이 있으나 build 0.29.3이고
`recordings_management`가 빠졌으며 locale/font scale이 고정되지 않았다. landscape는 portrait
컨테이너 안에 회전된 화면이어서 방향 정규화가 필요하다. 기존 Android 카탈로그는 잠소리 3상태만
Compose root로 캡처하며 저장 경로·SHA·app bounds를 저장소 manifest에 남기지 않는다. 따라서 둘 다
최신 전체 parity 판정에는 불충분하고, 아래 행은 fresh paired 증거가 붙기 전까지 `확인 필요`다.

## 전체 route/state 인벤토리

| Route/state ID | 화면 또는 상태 | iOS 진입 | Android 진입 | 기본 fixture/profile | 상태 |
|---|---|---|---|---|---|
| `first_launch_permissions` | 최초 권한 설명 | 신규/권한 누락 예약 실행 | 동일 | permissions-missing/phone portrait | 확인 필요 |
| `home_portrait` | 오브제 홈 | 일반 실행 | 일반 실행 | 2 radios, fixed clock/weather/phone portrait | 확인 필요 |
| `home_landscape` | 오브제 홈 가로 | 기기 회전 | 기기 회전 | iOS 0.32.5 대표 iPhone 기본 좌표·배율 이식, fresh paired capture 필요 | 확인 필요 |
| `home_landscape` | 상단 음악 채널 | horizontal drag/clipping/edge fade | iPhone 원본 1280×588 / Android phone landscape | 오른쪽 제어 버튼 전까지의 음악 영역만 좌우 드래그, 24/28pt edge fade | 부모 음악 viewport가 드래그를 선점하고 영역 밖을 자르며 같은 edge fade 적용 | 화면 꺼짐으로 fresh runtime capture 실패 | Android 실기기 swipe + before/after capture | source High | 구현·단위 테스트, 실화면 확인 필요 |
| `home_landscape` | 잠소리·보이소·설정 | fixed placement/order | same | 음악 영역 오른쪽 상단에 3개 고정, 설정이 맨 오른쪽 | 같은 순서와 57.12dp 폭, 음악 영역과 별도 고정 Row | 화면 꺼짐으로 fresh screenshot 없음 | 실기기 landscape capture | source/image High | 구현·설치, 실화면 확인 필요 |
| `home_mate` | 매이트 홈/잠금 | 자동 또는 유지 선택 | 동일 | mate locked | 확인 필요 |
| `home_startle` | 화들짝 일시 상태 | 60초 뒤 유효 이벤트 | 동일 | dark recent camera + event | 확인 필요 |
| `home_inactive` | 세션 중지 시작 화면 | 보호 중지 | 동일 | session inactive | 확인 필요 |
| `home_brightness_hud` | 앱 밝기 HUD | 세로 drag | 동일 | 50% | 확인 필요 |
| `home_radio_volume_hud` | 라디오 볼륨 HUD | 가로 drag | 동일 | 50% | 확인 필요 |
| `home_clock_scale_hud` | 시계 크기 HUD | pinch | 동일 | 100% | 확인 필요 |
| `home_editor` | 패널 편집 | 홈 long press | 동일 | portrait draft | 확인 필요 |
| `home_editor_landscape` | 가로 패널 편집 | 회전/진입 | 동일 | landscape draft | 확인 필요 |
| `home_editor_font_palette` | 편집 글꼴 팔레트 | 시계 panel tap | 동일 | font palette | 확인 필요 |
| `recordings_report_empty` | 빈 수면 리포트 | 잠소리 | 동일 | no sessions | 확인 필요 |
| `recordings_report_populated` | 채워진 수면 리포트 | 잠소리 | 동일 | fixed 8h session | 확인 필요 |
| `recordings_management` | 잠소리 관리 | segmented page | 동일 | fixed 3 clips | 확인 필요 |
| `recordings_selection_dock` | 선택 작업 dock | 원본 선택 | 동일 | 2 selected | 확인 필요 |
| `recordings_playback_dock` | 재생 dock | clip play | 동일 | fixed playable clip | 확인 필요 |
| `recordings_delete_clip_confirmation` | 단일 삭제 확인 | delete | 동일 | fixed clip | 확인 필요 |
| `recordings_operation_error` | 작업 실패 | injected failure | 동일 | deterministic error | 확인 필요 |
| `boyiso_setup` | 보이소 초기 설정 | 홈/설정 | 동일 | disconnected | 확인 필요 |
| `boyiso_connected` | 보이소 연결/사람 목록 | joined room | 동일 | fixed participants | 확인 필요 |
| `boyiso_greeting_overlay` | 톡톡/소리 overlay | received event | 동일 | fixed sender/event | 확인 필요 |
| `settings_top` | 설정 상단 | 홈 설정 | 동일 | orange/default | 확인 필요 |
| `settings_midnight_theme` | 미드나이트 설정 | theme select | 동일 | midnight | 확인 필요 |
| `settings_lower_sections` | 정보·라디오 | scroll | 동일 | two radios | 확인 필요 |
| `clock_font_options` | 시계 글꼴 | settings row | 별도 route 구현 | default | source 구현, capture 필요 |
| `font_licenses` | 폰트 저작권 목록 | settings row | 별도 route 구현 | default | source 구현, capture 필요 |
| `font_license_detail` | 라이선스 전문 | font row | 별도 detail route 구현 | selected font | source 구현, trace 필요 |
| `radio_inline_editor` | 설정 inline 채널 편집 | pencil/add | 동일 | fixed first channel | 확인 필요 |
| `radio_management` | 채널 관리 | editor/management | 동일 | two radios | 확인 필요 |
| `radio_channel_editor` | 채널 추가·수정 | management | 동일 | fixed first channel | 확인 필요 |
| `radio_delete_confirmation` | 채널 삭제 확인 | trash/swipe/context | 동일 | fixed first channel | 확인 필요 |
| `restore_confirmation` | 추천 설정 복원 | info action | 동일 | settings changed | 확인 필요 |
| `radio_browser` | 앱 내 브라우저 toolbar | 3+ paths | 3 paths | fixed HTTPS/wide+narrow | 확인 필요 |
| `radio_browser_favorites` | 즐겨찾기 panel | star | 동일 | defaults | 확인 필요 |
| `radio_browser_error` | 브라우저 오류 | invalid/injected | 동일 | fixed invalid | 확인 필요 |
| `share_radio_import_confirmation` | 공유 주소 초안 | Share extension | ACTION_SEND | fixed HTTPS | 확인 필요 |
| `first_launch_request_camera` | OS 카메라 권한 | permission sequence | Android permission UI | OS-owned | forced OS exception 후보 |
| `first_launch_request_microphone` | OS 마이크 권한 | permission sequence | Android permission UI | OS-owned | forced OS exception 후보 |
| `first_launch_request_location` | OS 위치 권한 | permission sequence | Android permission UI | OS-owned | forced OS exception 후보 |
| `system_share_sheet` | 리포트/QR/녹음 공유 | ShareLink/share sheet | ACTION_SEND chooser | OS-owned | forced OS exception 후보 |
| `system_keyboard` | 텍스트 입력 keyboard | editor/browser | IME | OS-owned | forced OS exception 후보 |

## Atomic 비교 매트릭스

| Route/state ID | Element/anatomy | Dimension/action | Fixture/profile | iOS exact reference | Android observed | Difference | Required action | Evidence/confidence | Status/exception proof |
|---|---|---|---|---|---|---|---|---|---|
| `first_launch_permissions` | root/background | RGBA/gradient/safe area | permissions-missing/phone portrait | black→`(0.12,0.075,0.055)` vertical, edge-to-edge | `StandHomeScreen` permission branch | runtime pixels 미측정 | paired flat-color/geometry 검사 | source High, runtime Low | 확인 필요 |
| `first_launch_permissions` | header icon | asset/bounds/tint | same | shield check, 42pt semibold, accent | Material Security, 42dp | glyph geometry 다름 | iOS와 같은 vector path/size 사용 | source High | remaining |
| `first_launch_permissions` | title | Unicode/font/baseline | same | `시작하기 전에`, title2 bold | 동일 문구/headlineSmall bold | font metrics 미측정 | paired baseline/font token 검사 | source High, runtime Low | 확인 필요 |
| `first_launch_permissions` | subtitle | exact text | same | `S.tand가 필요한 이유를 먼저 알려드릴게요.` | 동일하게 수정 | fresh capture 없음 | capture/trace | source High | 확인 필요 |
| `first_launch_permissions` | permission cards | count/order | same | 3: 카메라와 플래시→마이크→위치 정보 | 3개 동일 순서 | 없음(source) | capture + TalkBack order | source High | 확인 필요 |
| `first_launch_permissions` | permission row | icon container/spacing | same | 36×36, radius10, icon18, row padding14 | 36dp/r10/icon18/p14 | density rendering 미측정 | geometry compare | source High | 확인 필요 |
| `first_launch_permissions` | permission text | exact Unicode/wrap | same | iOS 3개 원문 | Android 원문 동일 | line wrap 미측정 | phone/tablet/large text capture | source High | 확인 필요 |
| `first_launch_permissions` | CTA | text/height/radius/action | same | `권한 확인하고 시작`, 52pt, r16; camera→mic→location | 52dp/r16, 문구·순서 동일 | runtime trace 없음 | trace deny/allow permutations, relaunch | source High | 확인 필요 |
| `home_portrait` | header mode | icon/text/placement | ui_catalog_v2/phone portrait | left `오브제 모드` | Android source equivalent | pixel/token 미측정 | paired capture | source Medium | 확인 필요 |
| `home_portrait` | header brand | asset/title/center | same | app icon + `S.tand` | `S.tand`로 수정 | fresh capture 없음 | paired bounds and asset hash | source High | 확인 필요 |
| `home_portrait` | header battery | icon/value/pill | same | right battery | Android equivalent | content fixture 미고정 | fixed battery fixture | source Medium | 확인 필요 |
| `home_portrait` | radio row anatomy | two icons/titles/status/divider | fixed radios | 편안한 재즈/밤의 클래식, radio glyph, `대기 중` | equivalent Compose panels | exact geometry/icon 미측정 | paired decomposition | source Medium | 확인 필요 |
| `home_portrait` | weather anatomy | icon/location/temp/title/apparent | fixed weather | current-location fixture | equivalent Compose panel | exact fixture currently absent | inject fixed weather | source Medium | 확인 필요 |
| `home_portrait` | flip clock | font/card/gap/baseline | fixed 07:42:05 | selected font, split mask, seconds panel | Compose flip clock | time currently live | inject fixed clock then pixel compare | source Medium | 확인 필요 |
| `home_portrait` | bottom controls | titles/status/icons/order | 3 clips/disconnected | 잠소리 확인, 보이소, 설정 열기 | same semantic controls | icon/geometry 미측정 | paired anatomy compare | source High | 확인 필요 |
| `home_portrait` | bottom build | exact text/opacity/position | same | iOS build + brightness | Android build + brightness | platform versions differ(content/state) | classify/mask only version value, retain geometry | source High | 확인 필요 |
| `home_landscape` | root/app bounds | orientation/geometry | same fixture/landscape | landscapeLeft normalized app frame | SM-F968N versionCode 56 실제 가로 캡처 확인 | fresh iOS pair missing | deterministic paired capture | Medium | Android 실기기 확인, paired 확인 필요 |
| `home_mate` | center lock | icon/text/opacity | mate locked | 매이트 잠금 overlay | Android lock overlay | pixel mismatch unknown | paired capture + action trace | source High | 확인 필요 |
| `home_startle` | mode/banner/background | state transition | 60s+dark event | `화들짝 모드`, temporary lamp/optional torch | Android same state names/policy | 5s/30s trace not paired | monotonic trace and captures at enter/exit | source High | 확인 필요 |
| `home_inactive` | start content | exact text/CTA | inactive | `S.tand가 곁에 있을게요`, `S.tand 시작` | 동일하게 수정 | fresh capture 없음 | capture + start trace | source High | 확인 필요 |
| `home_brightness_hud` | HUD | label/value/lifetime | 50% drag | `앱 밝기 50%`, drag 동안만 | Android equivalent | animation/timing 미측정 | timestamped trace + frame capture | source Medium | 확인 필요 |
| `home_radio_volume_hud` | HUD | label/value/lifetime | 50% drag | `라디오 볼륨 50%` | Android equivalent | runtime 미측정 | trace/system volume invariant | source Medium | 확인 필요 |
| `home_clock_scale_hud` | HUD | label/value/lifetime | 100% pinch | `시계 크기 100%`, 1.2s+fade | Android equivalent | timing 미측정 | trace/capture | source Medium | 확인 필요 |
| `home_editor` | toolbar | cancel/reset/save | portrait draft | app-owned iOS toolbar | Android app-owned toolbar | glyph/placement 미측정 | exact capture/anatomy compare | source Medium | 확인 필요 |
| `home_editor` | protected region | bounds/clamp | phone portrait | iOS measured policy | Android policy/tests exist | runtime edge cases 미측정 | drag/pinch/resize trace | source High | 확인 필요 |
| `home_editor` | panels | asset/text/handles/bounds | same | clock/date/weather/radio/battery | Android corresponding panels | pixel mismatch unknown | per-panel paired rows after capture | source Medium | 확인 필요 |
| `recordings_report_empty` | page picker | labels/selected/geometry | no sessions | 수면 리포트/잠소리 관리 segmented | custom rounded 2-segment Compose | custom geometry not proven exact | paired bounds/color compare | source High | 확인 필요 |
| `recordings_report_empty` | empty anatomy | icon/title/body | no sessions | moon.zzz + exact two strings | GraphicEq + same strings | icon geometry mismatch | use same vector path as iOS reference | source High | remaining |
| `recordings_report_populated` | session header | title/time/badge/timeline | fixed 8h session | fixed session values | corresponding Android calculation | timezone/date now fixed only in iOS v2 | Android fixture timezone/date fix | source High | 확인 필요 |
| `recordings_report_populated` | metrics | 3 cards/order/text | same | 기록 구간/소리 후보/화들짝 반응 | same | geometry/color 미측정 | paired component compare | source High | 확인 필요 |
| `recordings_report_populated` | activity distribution | 12 bars/a11y | same | 12 buckets + description | 12 bars + semantics | bucket fixture must match | compare values/bounds | source High | 확인 필요 |
| `recordings_report_populated` | actions | share/queue | same | 리포트 공유, 핵심 소리 이어 듣기 | same behavior | actual chooser/audio not traced | trace queue order/share payload | source High | 확인 필요 |
| `recordings_management` | summary | title/count/storage/duration | 3 clips | 보관 현황 + exact counts | same calculation | actual file bytes are zero in fake paths | deterministic real-size fixture | source Medium | 확인 필요 |
| `recordings_management` | rows | checkbox/title/badge/support/trailing | fixed clips | 48pt targets/anatomy | Android 48dp targets | visual mismatch unknown | paired anatomy compare/TalkBack | source High | 확인 필요 |
| `recordings_selection_dock` | dock | responsive layout/actions | large text | clear/merge/delete | Android responsive Compose | large-text runtime missing | capture + trace | source Medium | 확인 필요 |
| `recordings_playback_dock` | dock | play/seek/boost/close | playable clip | iOS actions 48pt | Android MediaPlayer dock | real audio absent | device trace with fixed asset | source Medium | 확인 필요 |
| `boyiso_setup` | steps | exact text/order/actions | disconnected | iOS BoyisoView | Android BoyisoScreen | several labels differ in source | align against fresh capture/trace | source High | remaining |
| `boyiso_connected` | participant row anatomy | role/name/self/status/path | fixed participants | iOS rows | Android rows | exact text and icons pending | paired decomposition | source Medium | 확인 필요 |
| `settings_top` | app bar | title/action/placement | orange/default | title `설정`, trailing `완료` | 동일하게 수정 | fresh capture 없음 | paired app-bar bounds/colors | source High | 확인 필요 |
| `settings_top` | hero | title/subtitle/status/selector | same | `S.tand`, `낮에는 오브제\n밤에는 매이트`, 3 choices | source aligned | icon/panel geometry 미측정 | paired decomposition | source High | 확인 필요 |
| `settings_top` | section order | screen/permission/Boyiso/sleep/info/radio | same | current iOS order | Android CardOrder same | 없음(source) | scroll trace/captures | source High | 확인 필요 |
| `settings_top` | screen card | subtitle/theme/font row | same | exact punctuation-free copy, navigation row | navigation row와 별도 route 구현 | pixel anatomy 미검증 | paired capture/trace | source High | 확인 필요 |
| `settings_top` | permission card | rows/toggle/status | same | four toggles and exact status policy | Android same functions | many status strings differ | align state-specific copy | source High | remaining |
| `settings_lower_sections` | info card | version/creator/font/weather rows | same | four navigation/info rows + 3 notices + restore | 폰트 목록/detail route 구현 | pixel anatomy 미검증 | paired capture/trace | source High | 확인 필요 |
| `settings_lower_sections` | radio card | channel anatomy/help | fixed radios | play button/title/status/pencil, exact help | Android source aligned help; Material anatomy | geometry/icons unknown | paired compare | source Medium | 확인 필요 |
| `clock_font_options` | route/grid | navigation/title/3→1 columns | fontScale 1.0/large | separate `시계 글꼴` route | 별도 route, 10개 3열/large 1열, split-card/check 구현 | runtime pixels 미검증 | deterministic capture + selection trace | source High | 확인 필요 |
| `font_licenses` | route/list | title/sections/rows | default | separate `폰트 저작권` route | 별도 목록/9개 detail/선택 가능 전문 구현 | runtime pixels·전문 대조 미검증 | paired capture + 9 route trace | source High | 확인 필요 |
| `radio_channel_editor` | toolbar/form/actions | exact text/targets | fixed channel | `채널 수정`, name/url/paste/browser/delete/save | title/field/help/replay/delete/save copy와 순서 정렬 | Material form·icon anatomy 미검증 | paired capture/trace | source High | 확인 필요 |
| `radio_delete_confirmation` | dialog | title/body/buttons/side effect | fixed channel | channel name, irreversible, 삭제/취소 | same semantics | Material dialog rendering app-owned mismatch | custom surface matching iOS | source High | remaining |
| `restore_confirmation` | dialog | title/body/buttons | changed settings | exact copy | same copy | app-owned rendering mismatch unknown | paired capture | source High | 확인 필요 |
| `radio_browser` | toolbar anatomy | back/address/go/reload/star/close | HTTPS/wide | exact controls and order | Android corresponding controls | glyph/spacing unknown | paired anatomy compare | source High | 확인 필요 |
| `radio_browser` | toolbar | two-row responsive | narrow/large | two rows, X always visible | Android responsive source | runtime missing | capture at threshold/large text | source Medium | 확인 필요 |
| `radio_browser_error` | error panel | text/close/recovery | invalid URL | iOS exact validation/errors | Android many error copies differ | content mismatch | unify user-visible validation copy | source High | remaining |
| `share_radio_import_confirmation` | intent/validation/persistence | action trace | fixed HTTPS | 1 pending draft, save/cancel semantics | ACTION_SEND receiver equivalent | relaunch trace missing | paired behavioral trace | source High | 확인 필요 |
| `first_launch_request_camera` | OS dialog | pixels/wording | iOS/Android OS | iOS-owned prompt | Android-owned prompt | OS-imposed | mask exact OS dialog only; app pre-prompt unmasked | High | forced OS exception 후보 |
| `system_share_sheet` | OS chooser | pixels/behavior | share payload | iOS share sheet | Android chooser | OS-imposed | mask chooser chrome only; payload/destination trace remains | High | forced OS exception 후보 |

## App-owned 아이콘 매핑 게이트

아래 Android Material glyph는 의미가 같더라도 SF Symbol과 path·bounds·stroke가 다르므로
현재 모두 `unresolved mismatch`다. Apple SF Symbols를 복제하지 않고, paired capture로 실제
차이를 측정한 뒤 합법적인 custom vector 또는 공용 브랜드 asset이 있을 때만 교체한다.

| Route/state | Atomic element | iOS symbol/asset | Android asset/vector | Bounds/stroke/placement evidence | Status |
|---|---|---|---|---|---|
| `first_launch_permissions` | header shield | app-owned shield/check symbol | `Icons.Default.Security` | iOS 42pt/Android 42dp source only, path 미일치 | unresolved mismatch |
| `first_launch_permissions` | camera/mic/location rows | iOS permission symbols | `CameraAlt` / `Mic` / `LocationOn` | 18pt↔18dp container source only, path/stroke 미검증 | unresolved mismatch |
| `home_portrait` | center brand | app icon asset | `R.drawable.stand_brand_icon` | 공용 브랜드 계보 확인, paired bounds/hash 필요 | 확인 필요 |
| `home_portrait` | mode/status/battery/editor | `sun.max.fill`, `moon.fill`, `battery.100percent.bolt`, `pencil.circle.fill` 등 | `LightMode`, `Bedtime`, `BatteryChargingFull`, home control vectors | path·bounds·stroke·placement 미검증 | unresolved mismatch |
| `settings_top` | brand | app icon asset | `R.drawable.stand_brand_icon` | 공용 브랜드 계보 확인, 54pt/dp placement 미검증 | 확인 필요 |
| `settings_top` | screen section | `clock.fill` | `Icons.Default.TextFields` | 의미와 path 모두 다름 | unresolved mismatch |
| `settings_top` | permission section | `checkmark.shield.fill` | `Icons.Default.Security` | check glyph/path/stroke 다름 | unresolved mismatch |
| `settings_top` | Boyiso section | custom `BoyisoBabyFaceIcon` | `Icons.Default.ChildCare` | custom face와 Material child path 다름 | unresolved mismatch |
| `settings_top` | sleep section | `moon.zzz.fill` | `Icons.Default.GraphicEq` | 의미·path 다름 | unresolved mismatch |
| `settings_lower_sections` | information/radio | `info.circle.fill` / `radio.fill` | `Info` / `Radio` | fill/stroke/bounds 미검증 | unresolved mismatch |
| `clock_font_options` | selected tile | `checkmark.circle.fill` | `Icons.Default.CheckCircle` | glyph stroke/fill/bounds 미검증 | unresolved mismatch |
| `clock_font_options` | back | NavigationStack chevron | `AutoMirrored.ArrowBack` | arrow anatomy 다름 | unresolved mismatch |
| `font_licenses` | row disclosure/back | `chevron.right` / NavigationStack chevron | `KeyboardArrowRight` / `ArrowBack` | path·bounds·placement 미검증 | unresolved mismatch |
| `recordings_report_empty` | empty marker | `moon.zzz` | `GraphicEq` | 의미·path 다름 | unresolved mismatch |
| `radio_channel_editor` | browser/delete/save | `safari.fill` / `trash` / `checkmark.circle.fill` | Material browser/delete/save vectors | path·bounds·stroke 미검증 | unresolved mismatch |
| `restore_confirmation` | restore action | `arrow.counterclockwise` | `RestartAlt` | path·stroke·dialog placement 미검증 | unresolved mismatch |

## 기능 trace 필수 집합

각 행은 `docs/matchup-traces/{platform}.jsonl`에 동일 schema로 기록한다.

`routeStateId`, `fixtureId`, `profile`, `entrySteps`, `preState`, `input`, `expectedPostState`,
`actualPostState`, `sideEffects`, `persistedAfterRelaunch`, `validationOrError`, `accessibility`,
`capture`, `verifier`, `status`를 필수로 한다.

최소 trace는 권한 allow/deny 순서와 3/7회 재안내, 홈 tap/double/long/vertical/horizontal/pinch,
오브제↔매이트↔화들짝 및 60초 경계, 편집 save/cancel/relaunch, 라디오 play/retry/edit/delete,
브라우저 history/popup/security/close, 잠소리 queue/share/merge/delete/partial failure,
보이소 invite/join/톡톡/중복 억제, 위치·카메라·마이크 generation과 lifecycle 정리를 포함한다.
