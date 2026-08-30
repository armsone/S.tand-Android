# Mate overnight device trace — 2026-08-30

## Devices

- Samsung Galaxy Z Fold, model `SM-F956N`
- Samsung Galaxy Z TriFold, model `SM-F968N`

Both devices retained their existing app data and microphone permission. The final `2.4.0` (`versionCode 347640`, build `202608301000`) debug APK was installed with `adb install -r`.

## Behavior verified

1. An active automatic session entered Mate mode and showed the live microphone state (`방 소리 익히는 중`).
2. `KEYCODE_SLEEP` turned the display off while the devices remained wirelessly connected.
3. `dumpsys activity services com.armsone.stand` reported `MateMonitoringService` as `isForeground=true`, notification id `5101`, and foreground type `0x80` (microphone) on both devices.
4. `cmd appops get com.armsone.stand RECORD_AUDIO` reported `allow` and `(running)` on both devices after the displays were off.
5. The Fold power state reported `Dozing`, proving the microphone result was captured after screen-off rather than while the activity remained interactive.
6. Both devices were woken again and `svc power stayon true` was restored for continued validation.
7. The final versioned APK repeated the screen-off check successfully on both devices: the foreground microphone service remained active and `RECORD_AUDIO` remained `(running)`.

## Build verification

- `./gradlew testDebugUnitTest` — passed.
- `./gradlew assembleDebug lintDebug` — passed.
