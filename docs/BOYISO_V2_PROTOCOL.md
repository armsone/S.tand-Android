# 보이소 QR·메시·인터넷 연동 규약 v2

상태: Android 근거리 구현 기준, iPhone 근거리 이식 대기. 인터넷 중계는 후순위다.

보이소는 한 돌봄 공간에 `볼사람`과 `말할사람` 기기를 각각 여러 대 연결한다. 가까운
기기는 LAN과 BLE로 직접 연결하고, 연결된 모든 기기는 처음 받은 사건을 다른 경로로 한 번
전달한다. 현재 제품 우선순위는 이 근거리 연결이다. 인터넷 중계가 나중에 설정되면 먼 기기도
같은 암호화 사건을 실시간으로 받을 수 있다.

원음과 녹음 파일은 어떤 전송 경로로도 보내지 않는다. 사용자에게는 `볼사람·말할사람`만
표시하고 내부 wire value는 기존 호환을 위해 각각 `host·guest`를 유지한다.

## QR 초대

첫 볼사람 한 명만 새 공간을 만들고 초대 QR을 표시한다. 참여 기기는 QR을 촬영한 뒤 역할과
기기 이름을 고른다. 참여자가 초대 QR을 다시 표시하지 않는다.

QR payload는 다음 custom URL이다.

```text
stand://boyiso?v=2&room=<base64url 12-byte room id>&key=<base64url 32-byte room key>
```

- Base64 URL-safe, padding 없음
- `room`은 발견·표시용 임의 식별자다.
- `key`는 256-bit 난수이며 QR 밖에 평문 표시하지 않는다.
- 새 공간을 만들면 room과 key를 모두 교체하므로 과거 QR은 무효다.
- QR과 공유 링크를 받은 사람은 공간에 참여할 수 있으므로 화면에서 비밀 초대임을 알린다.

## 암호화

```text
event key = SHA-256(UTF-8("boyiso-v2|" + roomKey))
routing channel = Base64URL-NoPadding(
  SHA-256(UTF-8("boyiso-route-v2|" + roomId + "|" + roomKey))
)
```

사건 본문은 AES-256-GCM으로 암호화한다. nonce는 매 프레임 새 12-byte 난수이고 인증 tag는
16 byte다. 전송 payload는 `nonce || ciphertext || tag`다. 인증 실패·JSON 실패·다른 버전은
조용히 폐기한다.

기기 로컬에는 room ID/key, 역할, 기기 이름, 영구 임의 source ID를 저장한다. Android는
백업에서 제외하며 iPhone도 iCloud 동기화 대상에 넣지 않는다.

## 사건 JSON

```json
{
  "version": 2,
  "id": "UUID",
  "sourceID": "설치별 UUID",
  "sourceName": "사용자 지정 기기 이름",
  "role": "host | guest",
  "kind": "heartbeat | sound | movement | toktok",
  "sentAtMilliseconds": 0,
  "intensity": 0.0,
  "detail": "big_sound | continuous_sound | greeting",
  "monitoring": true,
  "batteryPercent": 0
}
```

`intensity`, `detail`, `batteryPercent`는 선택 값이다. heartbeat는 5초마다 보낸다. 마지막
heartbeat가 15초를 넘으면 연결 기기 목록에서 제외한다. 모든 경로의 사건 ID를 10분간
기억해 LAN·BLE·인터넷 중복과 메시 고리를 한 번만 처리한다.

## 로컬 메시

- 모든 역할이 `_boyiso._tcp` NSD 서비스를 열고 동시에 발견한다.
- 모든 역할이 BLE GATT peripheral과 central을 동시에 시도한다.
- 받은 사건은 source가 자신이 아니고 처음 보는 ID일 때만 다른 경로로 전달한다.
- LAN은 Base64 암호문 한 줄과 LF를 사용한다.
- BLE는 기존 9-byte big-endian header를 사용해 암호문을 분할한다.
- 연결 수와 마지막 heartbeat는 사용자에게 직접 연결·중계 상태를 설명하는 근거로 쓴다.

OS와 하드웨어가 동시 central/peripheral을 제한하면 가능한 경로만 유지한다. 한 경로 실패를
전체 연결 성공으로 숨기지 않는다.

## 인터넷 중계

이 절은 후속 단계의 호환 경계다. 기본 Android 빌드의 URL은 비어 있어 중계가 동작하지 않고,
근거리 화면에도 인터넷 대기 상태를 표시하지 않는다. 서버 배포와 푸시 연동은 이번 근거리
완료 범위에 포함하지 않는다.

앱 빌드의 `BOYISO_RELAY_URL`은 `wss://.../v1/relay` 형식이다. 연결 후 다음 JSON을 보낸다.

```json
{"type":"join","channel":"<routing channel>","sender":"<source ID>"}
{"type":"event","channel":"<routing channel>","sender":"<source ID>","payload":"<Base64 ciphertext>"}
```

서버는 같은 익명 channel의 다른 온라인 socket에 암호문만 전달한다. 공간 키와 사건 본문을
알지 못한다. 현재 서버 제한은 공간당 32 socket, 10초당 40 frame, frame당 24KiB다.
Android는 20초 WebSocket ping과 1·2·4·8·16·30초 재연결을 사용한다.

인터넷 중계는 Wi-Fi와 셀룰러를 구분하지 않는다. 로컬 메시의 어느 한 기기만 인터넷에
연결돼도 그 기기가 처음 받은 사건을 원거리 경로로 전달할 수 있다.

## 톡톡

연결 중인 모든 역할이 `toktok/greeting` 사건을 보낼 수 있다. 발신 기기는 5초 cooldown을
적용한다. 수신 기기는 같은 사건 ID에 한 번만 반응한다.

- 전경: 고양이·강아지 인사 이미지를 3초 표시하고 번들 WAV와 짧은 2회 진동 재생
- Android 백그라운드: `톡톡` 중요도 알림 채널, 번들 WAV와 같은 진동 패턴 사용
- iPhone 백그라운드: APNs alert push와 번들 custom notification sound 사용
- 무음, 집중 모드, 알림 차단 등 OS 사용자 선택을 우회하지 않음
- 통화·알람 전용 full-screen/critical alert 권한을 사용하지 않음

실시간 WebSocket에 연결되지 않은 iPhone에 톡톡을 전달하려면 provider server가 APNs device
token을 공간과 연결하고 generic alert push를 보내야 한다. push에는 민감한 사건 본문 대신
`톡톡이 왔어요` 같은 고정 문구와 암호화 payload 식별자만 둔다.

## 보안·운영 경계

- 계정은 요구하지 않는다.
- 중계 서버는 원음·녹음·room key를 저장하지 않는다.
- 서버가 볼 수 있는 metadata는 익명 channel, source ID, 접속 시각, 암호문 크기다.
- QR 유출 시 공간을 새로 만들어 전체 키를 회전한다.
- 서버 공개 전 TLS, 다중 인스턴스 pub/sub, rate limit 공유 저장, 장애 관측과 개인정보 고지를
  구성한다.
- APNs·FCM 인증정보, relay URL과 서명 키는 저장소에 커밋하지 않는다.

## 호환성

v1의 8자리 room code와 v2 QR key는 wire compatible하지 않다. v2 구현은 version이 다른
QR과 사건을 거부한다. Android와 iPhone은 이 문서의 v2가 모두 배포되기 전까지 서로 연결
완료로 표시하지 않는다.
