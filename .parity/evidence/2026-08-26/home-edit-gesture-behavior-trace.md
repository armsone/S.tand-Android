# Home edit gesture behavior trace — 2026-08-26

## Apple runtime

- Target: iPhone 17 Pro simulator, iOS 26.5, landscape.
- Build: S.tand 2.2.1 (342257).
- XCTest: `STandUICatalogTests.testLandscapeEditorKeepsTheHomeLayoutVisibleWithoutBottomHint`.
- A stationary 1.0-second press left the editor closed.
- A 0.6-second press followed by a horizontal drag left the editor closed.
- A 0.6-second press followed by a vertical drag left the editor closed.
- A stationary 2.1-second press opened the editor and exposed the `저장` control.
- Result: passed on 2026-08-26 at 16:27 KST.
- Result bundle: `/Users/armsone/Library/Developer/Xcode/DerivedData/STand-aevsoipavfpxbjfgeyharxrgpykt/Logs/Test/Test-STand-2026.08.26_16-26-53-+0900.xcresult`.

## Android verification

- APK: S.tand 2.2.1, versionCode 342257, Build-Number 202608261617.
- `testDebugUnitTest`, including the 1,999/2,000 ms boundary, touch-slop boundary, and multi-pointer cancellation policy, passed.
- `lintDebug` and `assembleDebug` passed.
- The same APK was installed with `adb install -r` on SM-T500 and versionCode 342257 was confirmed. The tablet was locked, so touch automation could not run.
- An already-running phone emulator accepted the same APK and launched it, but went offline during the first behavior sequence. It was not restarted automatically.
- Result: Android device behavior remains runtime-unverified; the ledger row must stay `implemented_source_only`.
