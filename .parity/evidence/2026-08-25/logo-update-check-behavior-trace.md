# Logo update check — 2026-08-25

- Build: 2.1.0 (340467), common build stamp 202608251027
- Target: Google TV API 36 AVD (`emulator-5554`)
- Input: D-pad Up from the bottom controls until the centered S.tand logo was focused, then D-pad Select.
- Network source: latest published GitHub Release for the Android app.
- Result: the app displayed `최신 버전입니다` and `현재 설치된 에스텐드가 최신 버전입니다.` with a focused `확인` action.
- Accessibility: the centered logo exposes `최신 버전 확인` and is reachable and selectable without touch.
- Screenshot: `google-tv-logo-update-latest.png` (`sha256:c33a36983021f7cccc09e4edfdef73dd498748d6ee877b57897cc7c562e2942e`).
- Automated UI coverage: `AppUpdateDialogTest` ran 4 tests on the Google TV API 36 AVD with 0 failures.
