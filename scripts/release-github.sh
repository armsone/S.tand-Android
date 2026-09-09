#!/bin/zsh
set -euo pipefail

root_dir=${0:A:h:h}
cd "$root_dir"

version_name=$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' app/build.gradle.kts | head -1)
version_code=$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\)/\1/p' app/build.gradle.kts | head -1)
build_number=$(sed -n 's/^[[:space:]]*buildConfigField("String", "BUILD_NUMBER", "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)

if [[ -z "$version_name" || -z "$version_code" || -z "$build_number" ]]; then
  print -u2 'Android 릴리즈 버전 정보를 읽지 못했습니다.'
  exit 65
fi

./gradlew :app:assembleDebug

apk_path="$root_dir/app/build/outputs/apk/debug/app-debug.apk"
release_dir="$root_dir/artifacts/android"
release_apk="$release_dir/S.tand-Android-${version_name}.apk"
apksigner_path="${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools/36.0.0/apksigner"

if [[ ! -x "$apksigner_path" ]]; then
  print -u2 '기존 Android SDK의 apksigner를 찾지 못했습니다.'
  exit 69
fi

"$apksigner_path" verify --verbose --print-certs "$apk_path"
mkdir -p "$release_dir"
cp "$apk_path" "$release_apk"

tag="android-v${version_name}"
commit=$(git rev-parse HEAD)
notes=$'S.tand '"$version_name"$'\n\nAndroid-Version-Code: '"$version_code"$'\nBuild-Number: '"$build_number"

if gh release view "$tag" --repo armsone/S.tand-Android >/dev/null 2>&1; then
  gh release upload "$tag" "$release_apk" --repo armsone/S.tand-Android --clobber
else
  gh release create "$tag" "$release_apk" \
    --repo armsone/S.tand-Android \
    --target "$commit" \
    --title "S.tand Android ${version_name}" \
    --notes "$notes"
fi

gh release view "$tag" --repo armsone/S.tand-Android --json url,assets,targetCommitish
