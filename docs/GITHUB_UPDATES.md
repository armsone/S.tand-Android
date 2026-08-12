# GitHub 원터치 업데이트 배포

S.tand Android는 Google Play 대신 GitHub Release의 APK를 확인한다. 단순한 `git push`는 APK를
배포하지 않는다. 새 후보를 검증한 뒤 아래 규칙으로 Release를 공개해야 기존 사용자에게 업데이트가
표시된다.

## 앱의 확인 규칙

- API: `https://api.github.com/repos/armsone/S.tand-Android/releases/latest`
- 태그: `android-v{versionCode}` (예: `android-v28`)
- APK 이름: `S.tand-Android-v{versionCode}.apk`
- draft와 prerelease는 무시한다.
- 현재 versionCode보다 큰 버전만 표시한다.
- 저장소 HTTPS 주소, 파일 크기, package name `com.armsone.stand`, APK versionCode와 현재 설치본의
  서명이 모두 맞아야 설치 화면을 연다.

## 배포 절차

1. `assembleDebug`, `testDebugUnitTest`, `lintDebug`와 지정 실기기 검증을 마친다.
2. APK가 현재 설치본과 같은 개발용 인증서로 서명됐는지 `apksigner verify --print-certs`로 확인한다.
3. versionCode가 `N`이면 검증한 APK를 `S.tand-Android-vN.apk`로 복사한다.
4. `gh release create android-vN S.tand-Android-vN.apk --title "S.tand Android vN" --notes ...`로 공개한다.
5. GitHub API에서 tag, asset name, size와 다운로드 주소를 다시 읽어 확인한다.
6. 낮은 versionCode의 실제 설치본에서 업데이트 안내, 다운로드, 알 수 없는 앱 설치 권한과 Android
   설치 화면을 확인한다. 앱 삭제나 데이터 초기화는 하지 않는다.

Android 10도 APK를 검사할 수 있도록 다운로드 중인 임시 파일명은 `.partial.apk`로 끝나야 한다.
`.apk.part`는 최신 Android에서는 열리지만 Android 10 PackageManager가 APK로 인식하지 않는다.
또한 Samsung Android 10은 archive의 `GET_SIGNING_CERTIFICATES` 결과를 비워 반환하므로, minSdk 26
전체에서 제공되는 `GET_SIGNATURES`로 설치본과 archive의 단일 인증서 전체 일치를 확인한다.

## 서명 보관

현재 업데이트 계보는 이 Mac의 Android debug keystore를 사용한다. keystore 파일 자체나 비밀번호를
저장소, Release, 문서에 넣지 않는다. 이 파일을 잃거나 다른 debug keystore로 서명하면 기존 설치본을
업데이트할 수 없다. Google Play 정식 배포로 전환할 때는 별도의 서명 이전 계획을 먼저 세운다.
