# TV emulator behavior trace — 2026-08-25

Targets:

- Google TV API 36, 1080p, arm64-v8a (`S_tand_GoogleTV_API_36`)
- Android TV API 36, 1080p, arm64-v8a (`S_tand_AndroidTV_API_36`)

Verified observations:

1. The packaged APK installed successfully and resolved `com.armsone.stand/.MainActivity` for `MAIN + LEANBACK_LAUNCHER`.
2. In the earlier pre-simplification TV build, the first permission-review action had visible focus and D-pad Select opened microphone and approximate-location permission dialogs; no camera dialog appeared. This was superseded by the location-only release behavior in steps 20–21.
3. A TV without a battery reported `present: false, level: 0`. After the fix, the app treated the level as unknown, did not enter low-battery protection, and did not show a misleading 0% battery value.
4. Active and inactive home layouts rendered without the pre-fix overlap between the clock, start action, and large TV controls.
5. Active-home initial focus landed on the automatic recording control. D-pad Select stopped the session, focus moved to the start action, and Select started the session again.
6. D-pad navigation reached recordings and settings. Settings displayed `TV에서는 지원하지 않음` for flash and kept the switch disabled.
7. The TV-only theme, brightness, and clock-size controls changed the theme from Orange to Gray, brightness from 40% to 50%, and clock size from 101% to 111%.
8. Back returned from Settings to the app home. A second Back returned to the TV launcher.
9. The Android TV launcher listed the named 16:9 S.tand banner and displayed it in the first Favorite Apps slot after selection.
10. Settings rendered the same unified six-slot music list as the Apple reference, with Android-native Spotify and YouTube Music service identities and inline radio actions.
11. D-pad navigation selected a row's order handle, exposed explicit up/down controls, and moved YouTube Music from 2/6 to 1/6; the position label and persisted list updated immediately.
12. The wide Settings screen used independent staggered columns, eliminating the fixed-row blank gaps while keeping the Music card full width.
13. The iOS-matching `인터넷 라디오` shortcut appeared directly below the Settings hero; D-pad Select scrolled to the full-width Music card.
14. A phone API 37 AVD rendered the shortcut and card order in portrait, then changed to the independent wide layout in landscape. The shortcut opened the same six-slot list with Spotify, YouTube Music, and four radio slots in default order.
15. After the TV-only simplification build was installed, the Google TV home no longer exposed Boyiso, recordings, automatic recording, mode cycling, `오브제 모드`, or `매이트 모드`; the remaining D-pad controls were Settings, theme, app brightness, and clock size.
16. The effective TV home top inset changed from 38dp to 11.4dp, keeping the centered S.tand brand and six-channel music strip together at the top while leaving the clock and weather unobstructed.
17. D-pad Select opened Settings. The TV Settings hierarchy contained `음악·시간·날씨`, Internet Radio, Screen and Clock, Location permission, and Information, with no Boyiso, sleep-sound, recording, Object-mode, or Mate-mode labels.
18. The TV AVD had coarse-location permission and location services enabled but no last network location. After authorizing the emulator shell as the mock-location provider and injecting Seoul coordinates into the network provider, relaunching S.tand displayed 27°, drizzle, apparent temperature, and `서울특별시 중구`.
19. D-pad Right reached the remaining brightness control and three Select presses changed app brightness from 40% to 70%, leaving visible focus feedback and the compact four-control row on screen.
20. The 2.1.0 release APK (`versionCode 340467`) was installed without clearing user 0 data. A separate ephemeral Android user 10 provided clean app data; its first S.tand screen listed only `위치 정보` and focused `권한 확인하고 시작`. It contained no microphone, camera, Boyiso, recording, or sleep-sound request.
21. D-pad Select from that clean first-launch screen opened exactly one Android permission dialog: approximate location. The dialog contained only location choices (`While using the app`, `Only this time`, `Don’t allow`); no microphone or camera request preceded or followed it.

Limits:

- The Google TV launcher itself requires account setup before showing its populated home screen. Launcher banner rendering was therefore verified with the Android TV API 36 launcher, which consumes the same Leanback activity and banner contract.
- A physical Google TV device remains useful for final remote feel, overscan behavior, audio input, and vendor-launcher differences.
- The Seoul weather values are deterministic emulator evidence from an injected test location, not evidence that a TV contains GPS hardware.
- Clean first-launch permission evidence was isolated in an ephemeral Android user, so the representative user 0 app data and settings were not deleted or reset.
