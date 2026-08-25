# Google TV top cluster capture pair — 2026-08-25

This pair verifies the user-requested Google TV-only change after the last layout implementation. Apple has no tvOS target, so the applicable visual reference is the earlier installed Google TV build and the post-change installed Google TV build on the same 1920×1080 AVD.

| State | Capture | SHA-256 | Observation |
|---|---|---|---|
| Before | `google-tv-active-home.png` | `0fe633d5b6b88fbe186508ae74a80c430bef27f04ed5185a5336f5c2e8beba62` | Large top whitespace, mode label, oversized music and lower controls. |
| After | `google-tv-home-music-clock-weather.png` | `f8a86c37b89c5b63cc5e9a87b15db75f859845e6bdafb051f5594dbf926104b5` | 11.4dp top inset, centered S.tand brand and six music cards moved upward; weather, clock, date, and compact controls remain unobstructed. |

The post-change capture is from the installed API 36 release source and shows the requested top spacing at its final runtime size.
