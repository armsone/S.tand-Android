# iOS↔Android 밝기·모드 동등성 매트릭스

기준: iOS `20a3393`, Android 작업 트리. 실기기 설치·캡처는 실행하지 않았으므로 시각·센서 동등성은 미검증이다.

| Route/state ID | Element/anatomy | Dimension/action | Fixture/profile | iOS exact reference | Android observed | Difference | Required action | Evidence/confidence | Status/exception proof |
|---|---|---|---|---|---|---|---|---|---|
| home_auto_system_brightness | 하단 밝기 표시 | 시스템 밝기 100% | 자동, 앞면 | `displayBrightness` 100% | 임시 램프값 49% | Functional/Content | 시스템 밝기를 별도 기본 조명값으로 반영 | 소스, High | 구현; 실기기 확인 필요 |
| home_auto_system_change | 전체 화면 조명 | 시스템 밝기 변경 | 자동, 세션 실행 | 변경 통지를 앱 조명에 반영 | 주변광 대체값만 갱신 | Functional | 변경 통지에서 기본 조명·화면을 같이 갱신 | 소스, High | 구현; 실기기 확인 필요 |
| home_auto_ambient_dark | 오브제→매이트 | 어두움 20초 확인 | 자동, 조도 센서 | 매이트 전환 | 조도 센서 판정 경로 존재 | 없음(소스) | 유지 | 소스, Medium | 구현; 실제 조도 확인 필요 |
| home_auto_ambient_bright | 매이트→오브제 | 밝음 35초 확인 | 자동, 조도 센서 | 오브제 전환 | 조도 센서 판정 경로 존재 | 없음(소스) | 유지 | 소스, Medium | 구현; 실제 조도 확인 필요 |
| home_manual_adjust | 상하 드래그 | 수동 조명 조절 | 세션 실행 | 조절 중 시스템 동기 억제 | 조절 중 억제 필요 | Functional | 정책 테스트 추가 | 소스+단위, High | 구현 |
| home_face_down | 뒤집힘 암전 | 시스템 밝기 변경 | 뒤집힘 | 암전 유지 | 동기가 암전을 해제하면 안 됨 | Functional | 뒤집힘 중 동기 금지 | 소스+단위, High | 구현 |
