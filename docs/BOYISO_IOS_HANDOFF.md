# 보이소 v2 iPhone 구현 인수인계

기준 문서: `docs/BOYISO_V2_PROTOCOL.md`

## 사용자 흐름

1. 설정의 보이소 카드와 첫 화면의 독립 보이소 버튼을 제공한다.
2. 첫 볼 사람은 `새 보이소 만들기`로 v2 QR을 생성한다.
3. 다른 볼 사람·말할 사람은 카메라로 QR을 촬영하고 역할·기기 이름을 정한다.
4. 모든 역할이 주변 LAN·BLE 연결을 가능한 범위에서 동시에 유지한다.
5. 연결 화면은 기기를 역할별로 보여주고 배터리, 감시 여부, 마지막 수신, 직접·중계 경로를
   구분한다. 근거리 우선 단계에서는 인터넷 상태를 노출하지 않는다.
6. 연결된 누구나 톡톡을 보낼 수 있다.

사용자 표시에는 `호스트·게스트`, `보호자 폰·아이 곁 폰`을 쓰지 않고 정확히
`볼 사람·말할 사람`을 사용한다.

## iPhone 구현 항목

- 기존 `BoyisoModels.swift` codec을 v2 QR의 256-bit key와 event version 2로 전환
- `BoyisoConnectivityService`에 role field, 모든 역할 heartbeat, source 목록과 10분 dedupe 추가
- 모든 역할이 가능한 동안 Bonjour listener/browser와 BLE central/peripheral을 동시에 유지
- 처음 받은 사건을 수신 경로 외 LAN·BLE·인터넷으로 전달
- 후속 원거리 단계에서만 `URLSessionWebSocketTask` 기반 WSS relay를 추가
- `stand://boyiso` URL scheme에서 v2 초대 수락
- QR 생성은 Core Image, 촬영은 AVFoundation 또는 DataScanner 지원 범위에 맞춰 구현
- 첫 QR 생성자만 초대 QR·공유를 표시하고 참여자는 역할만 선택
- 톡톡 전경 3초 overlay, 짧은 notification haptic, VoiceOver announcement, Reduce Motion에서는 opacity 전환만 사용
- 근거리 연결이 실제 유지되는 동안 톡톡 백그라운드 알림과 custom sound 사용
- 앱 종료 뒤 원거리 톡톡을 깨우는 APNs는 후속 원거리 단계로 분리
- notification 권한 거부·집중 모드·무음 상태를 연결 오류로 오인하지 않고 설정 안내 제공
- 앱 종료·iOS background 제한 시 BLE relay가 항상 유지된다고 표시하지 않음

## 공통 자산

- Android 생성 이미지: `app/src/main/res/drawable-nodpi/boyiso_toktok_greeting.png`
- Android 참고음: `app/src/main/res/raw/boyiso_toktok.wav`

iPhone에는 같은 시각 자산을 Assets에 넣되 해당 작업 저장소에서 복사·최적화하고, WAV는
Apple notification sound 지원 형식과 길이를 확인한 뒤 번들에 포함한다.

## iPhone 완료 기준

- Android가 만든 QR을 iPhone이 읽고, iPhone QR을 Android가 읽는다.
- 동일 AES-GCM test vector와 routing channel이 양쪽에서 일치한다.
- 볼 사람 2대·말할 사람 2대가 한 공간에 동시에 나타난다.
- LAN과 BLE 각각 단독 경로와 경로 중복에서 사건이 한 번만 표시된다.
- 중간 기기 하나를 거치는 메시 relay와 재연결을 확인한다.
- 톡톡 전경 3초 표시·햅틱, 백그라운드 custom sound·진동, 알림 탭 진입을 확인한다.
- 앱 종료·집중 모드·알림 거부·네트워크 단절 상태를 실제와 다르게 정상으로 표시하지 않는다.
- 원음·녹음이 LAN·BLE로 전송되지 않음을 packet/log 수준에서 확인한다.

## 현재 Android 근거와 미완료

- versionCode 46 전체 JVM 테스트·`lintDebug`·`assembleDebug` 통과
- `SM-T500`에서 역할·이름·공간 선택, 모든 참여자의 QR 공유, source ID·연결 경로 기준
  역할별 참여자 목록, 연결 중 이름 수정, 밝은 화들짝 알림 문구의 Boyiso 화면 계측 테스트 5개 통과
- `SM-F968N`과 `SM-T500`에 같은 versionCode 46 APK를 데이터 유지 설치·실행했고,
  두 기기 모두 화면이 꺼진 상태에서 Boyiso foreground service 유지를 확인
- 실제 여러 기기의 QR 촬영, LAN·BLE 다중 hop, 최신 백그라운드 톡톡 소리·진동과 소리 감지
  전체 흐름은 최종 실사용 검증이 남아 있음
- 원거리 relay server, 공개 배포, APNs·FCM은 사용자 지시에 따라 후순위로 보류

iPhone 작업은 위 미완료를 Android 완료로 오해하지 말고, 최종 Android 검증 결과가 갱신된 뒤
상호 운용 시험을 시작한다.
