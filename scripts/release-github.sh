#!/bin/zsh
set -euo pipefail

root_dir=${0:A:h:h}
cd "$root_dir"

reuse_existing_apk=0
for arg in "$@"; do
  case "$arg" in
    --reuse-existing-apk) reuse_existing_apk=1 ;;
    *) print -u2 "알 수 없는 옵션: $arg"; exit 64 ;;
  esac
done

node_binary="${NODE_BINARY:-/opt/homebrew/opt/node@26/bin/node}"
expected_cert_sha256="${EXPECTED_CERT_SHA256:-837bd274f558659a3aec9bd31308b8ac01916c386230f946f5f7347d7f6f9b0f}"
repo_slug='armsone/S.tand-Android'

version_name=$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' app/build.gradle.kts | head -1)
version_code=$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\)/\1/p' app/build.gradle.kts | head -1)
# Kotlin 문자열 이스케이프(\"숫자\")를 벗겨 숫자만 읽는다.
build_number=$(sed -n 's/^[[:space:]]*buildConfigField("String", "BUILD_NUMBER", "\\"\([0-9]*\)\\"").*/\1/p' app/build.gradle.kts | head -1)

if [[ -z "$version_name" || -z "$version_code" || -z "$build_number" ]]; then
  print -u2 'Android 릴리즈 버전 정보를 읽지 못했습니다.'
  exit 65
fi

if [[ ! "$build_number" =~ '^[0-9]{12}$' ]]; then
  print -u2 "BUILD_NUMBER는 12자리 숫자여야 합니다: $build_number"
  exit 65
fi

tag="android-v${version_name}"

# 이미 있는 릴리스는 덮어쓰지 않는다. 게시본이면 변경 없이 종료하고, 초안이면 중단한다.
if existing_draft=$(gh release view "$tag" --repo "$repo_slug" --json isDraft --jq .isDraft 2>&1); then
  if [[ "$existing_draft" == false ]]; then
    print "이미 게시된 릴리스 ${tag}가 있어 변경 없이 종료합니다."
    gh release view "$tag" --repo "$repo_slug" --json url,assets,targetCommitish
    exit 0
  fi
  print -u2 "초안 릴리스 ${tag}가 이미 있어 중단합니다. 확인 후 수동으로 처리하세요."
  exit 73
elif [[ "$existing_draft" != *"not found"* ]]; then
  print -u2 "릴리스 ${tag} 조회에 실패했습니다: $existing_draft"
  exit 74
fi

notes_file="${STAND_RELEASE_NOTES_FILE:-}"
if [[ -z "$notes_file" ]]; then
  notes_file=$(mktemp -t stand-release-notes)
  trap 'rm -f -- "$notes_file"' EXIT
  print -r -- $'S.tand '"$version_name"$'\n\nAndroid-Version-Code: '"$version_code"$'\nBuild-Number: '"$build_number" > "$notes_file"
fi

if [[ ! -f "$notes_file" ]] \
  || ! grep -Fqx -- "Android-Version-Code: $version_code" "$notes_file" \
  || ! grep -Fqx -- "Build-Number: $build_number" "$notes_file"; then
  print -u2 '릴리스 노트가 없거나 Android-Version-Code/Build-Number 줄이 현재 버전과 다릅니다.'
  exit 75
fi

if (( reuse_existing_apk )); then
  print -r -- '--reuse-existing-apk: Gradle 빌드를 건너뛰고 기존 최종 APK를 검증합니다.'
else
  ./gradlew :app:assembleRelease
fi

apk_path="$root_dir/app/build/outputs/apk/release/app-release.apk"
release_dir="$root_dir/artifacts/android"
release_apk="$release_dir/S.tand-Android-${version_name}.apk"
apksigner_path="${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools/37.0.0/apksigner"
aapt2_path="${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools/37.0.0/aapt2"

if [[ ! -x "$apksigner_path" || ! -x "$aapt2_path" ]]; then
  print -u2 '기존 Android SDK의 apksigner 또는 aapt2를 찾지 못했습니다.'
  exit 69
fi

if [[ ! -f "$apk_path" ]]; then
  print -u2 '서명된 release APK를 찾지 못했습니다.'
  exit 66
fi

# 디버그 가능 APK는 배포하지 않는다. 확인할 수 없어도 중단한다.
badging=$("$aapt2_path" dump badging "$apk_path")
if [[ "$badging" == *application-debuggable* ]]; then
  print -u2 'debuggable APK는 배포할 수 없습니다.'
  exit 70
fi

if [[ "$badging" != *"package: name='com.armsone.stand' versionCode='${version_code}' versionName='${version_name}'"* ]]; then
  print -u2 'APK의 패키지/versionCode/versionName이 소스 설정과 다릅니다.'
  exit 71
fi

signer_output=$("$apksigner_path" verify --verbose --print-certs "$apk_path")
print -r -- "$signer_output"

if (( reuse_existing_apk )); then
  actual_cert=$(print -r -- "$signer_output" | sed -n 's/^.*certificate SHA-256 digest: //p' | sort -u)
  if [[ "${(L)actual_cert}" != "${(L)expected_cert_sha256}" ]]; then
    print -u2 "APK 서명 인증서 SHA-256이 기대값과 다릅니다: $actual_cert"
    exit 72
  fi
  # aapt는 BuildConfig 값을 노출하지 않으므로 dex 문자열에 빌드 번호 바이트가 있는지만 확인한다.
  # 이는 의미상 전체 검증이 아니라 같은 소스에서 빌드되었다는 보조 증거다.
  if ! python3 -I -c 'import sys, zipfile
marker = sys.argv[2].encode("ascii")
with zipfile.ZipFile(sys.argv[1]) as z:
    dex = [n for n in z.namelist() if n.startswith("classes") and n.endswith(".dex")]
    sys.exit(0 if any(marker in z.read(n) for n in dex) else 1)' "$apk_path" "$build_number"; then
    print -u2 "APK의 classes*.dex에서 BUILD_NUMBER ${build_number} 문자열을 찾지 못했습니다."
    exit 76
  fi
fi

mkdir -p "$release_dir"
if [[ -e "$release_apk" ]]; then
  cmp -s -- "$apk_path" "$release_apk" || { print -u2 '같은 이름의 다른 APK가 있어 덮어쓰지 않고 중단합니다.'; exit 73; }
else
  cp "$apk_path" "$release_apk"
fi

metadata_path="$release_dir/release.json"
metadata_helper="${HANSTREE_RELEASE_METADATA_HELPER:-$root_dir/../NasFinder.com/build/release-metadata.mjs}"
"$node_binary" "$metadata_helper" --version "$version_name" --build "$build_number" --version-code "$version_code" \
  --artifact "S.tand-Android=$release_apk" --output "$metadata_path"

commit=$(git rev-parse HEAD)

gh release create "$tag" "$release_apk" "$metadata_path" \
  --repo "$repo_slug" \
  --target "$commit" \
  --title "S.tand Android ${version_name}" \
  --notes-file "$notes_file" \
  --draft
draft_json=$(gh release view "$tag" --repo "$repo_slug" --json isDraft,tagName,targetCommitish,assets)
print -r -- "$draft_json" | python3 -I -c 'import json, os, sys
release = json.load(sys.stdin)
tag, commit, apk, metadata = sys.argv[1:]
expected = {os.path.basename(p): os.path.getsize(p) for p in (apk, metadata)}
assert release["isDraft"] is True and release["tagName"] == tag and release["targetCommitish"] == commit, "Draft identity mismatch"
assert len(release["assets"]) == 2 and {a["name"]: int(a["size"]) for a in release["assets"]} == expected, "Draft assets mismatch"
' "$tag" "$commit" "$release_apk" "$metadata_path"
gh release edit "$tag" --repo "$repo_slug" --draft=false --latest

gh release view "$tag" --repo "$repo_slug" --json url,assets,targetCommitish
