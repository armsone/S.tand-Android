#!/bin/zsh
set -euo pipefail

script_dir="${0:A:h}"
project_dir="${script_dir:h}"
serial="${MATCHUP_ANDROID_SERIAL:-${1:-}}"

if [[ -z "${serial}" ]]; then
  echo "usage: MATCHUP_ANDROID_SERIAL=<serial> $0"
  exit 2
fi

sdk_dir="$(sed -n 's/^sdk\.dir=//p' "${project_dir}/local.properties" | tail -n 1)"
adb_bin="${sdk_dir}/platform-tools/adb"
if [[ ! -x "${adb_bin}" ]]; then
  adb_bin="$(command -v adb || true)"
fi
if [[ -z "${adb_bin}" || ! -x "${adb_bin}" ]]; then
  echo "adb를 찾을 수 없습니다. local.properties의 sdk.dir을 확인해 주세요."
  exit 2
fi

artifact_dir="${project_dir}/artifacts/matchup/android/${serial}"
pass_one_dir="${artifact_dir}/pass-1"
pass_two_dir="${artifact_dir}/pass-2"
screen_dir="${artifact_dir}/screenshots"
remote_dir="/sdcard/Android/data/com.armsone.stand/files/matchup-ui-catalog"
test_class="com.armsone.stand.ui.MatchupUiCatalogTest,com.armsone.stand.ui.RecordingsUiCatalogTest"
app_apk="${project_dir}/app/build/outputs/apk/debug/app-debug.apk"
test_apk="${project_dir}/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

rm -rf "${artifact_dir}"
mkdir -p "${pass_one_dir}" "${pass_two_dir}" "${screen_dir}"

original_window_scale="$(${adb_bin} -s "${serial}" shell settings get global window_animation_scale | tr -d '\r')"
original_transition_scale="$(${adb_bin} -s "${serial}" shell settings get global transition_animation_scale | tr -d '\r')"
original_animator_scale="$(${adb_bin} -s "${serial}" shell settings get global animator_duration_scale | tr -d '\r')"
original_accelerometer_rotation="$(${adb_bin} -s "${serial}" shell settings get system accelerometer_rotation | tr -d '\r')"
original_user_rotation="$(${adb_bin} -s "${serial}" shell settings get system user_rotation | tr -d '\r')"

restore_device() {
  "${adb_bin}" -s "${serial}" shell settings put global window_animation_scale "${original_window_scale}" >/dev/null || true
  "${adb_bin}" -s "${serial}" shell settings put global transition_animation_scale "${original_transition_scale}" >/dev/null || true
  "${adb_bin}" -s "${serial}" shell settings put global animator_duration_scale "${original_animator_scale}" >/dev/null || true
  "${adb_bin}" -s "${serial}" shell settings put system accelerometer_rotation "${original_accelerometer_rotation}" >/dev/null || true
  "${adb_bin}" -s "${serial}" shell settings put system user_rotation "${original_user_rotation}" >/dev/null || true
}
trap restore_device EXIT INT TERM

"${adb_bin}" -s "${serial}" get-state >/dev/null
"${adb_bin}" -s "${serial}" shell settings put global window_animation_scale 0
"${adb_bin}" -s "${serial}" shell settings put global transition_animation_scale 0
"${adb_bin}" -s "${serial}" shell settings put global animator_duration_scale 0
"${adb_bin}" -s "${serial}" shell settings put system accelerometer_rotation 0
"${adb_bin}" -s "${serial}" shell settings put system user_rotation 0

capture_pass() {
  local destination="$1"
  "${adb_bin}" -s "${serial}" shell rm -f "${remote_dir}"/'*.png' "${remote_dir}"/'*.meta.json'
  (
    cd "${project_dir}"
    ./gradlew assembleDebug assembleDebugAndroidTest --no-parallel --max-workers=1
  )
  "${adb_bin}" -s "${serial}" install -r -t "${app_apk}" >/dev/null
  "${adb_bin}" -s "${serial}" install -r -t "${test_apk}" >/dev/null
  instrument_output="$("${adb_bin}" -s "${serial}" shell am instrument -w -r \
    -e class "${test_class}" \
    com.armsone.stand.test/androidx.test.runner.AndroidJUnitRunner)"
  echo "${instrument_output}"
  if ! grep -Eq '^OK \([0-9]+ tests?\)$' <<<"${instrument_output}"; then
    echo "Android instrumentation failed"
    exit 1
  fi
  "${adb_bin}" -s "${serial}" pull "${remote_dir}/." "${destination}/" >/dev/null
}

capture_pass "${pass_one_dir}"
capture_pass "${pass_two_dir}"

expected_states=(
  first_launch_permissions home_portrait home_landscape home_editor
  recordings_report_populated recordings_management boyiso_setup settings_top
  settings_midnight_theme clock_font_options settings_lower_sections
  radio_channel_editor radio_delete_confirmation restore_confirmation font_licenses
)

screens='[]'
for state_id in "${expected_states[@]}"; do
  first="${pass_one_dir}/${state_id}.png"
  second="${pass_two_dir}/${state_id}.png"
  first_meta="${pass_one_dir}/${state_id}.meta.json"
  second_meta="${pass_two_dir}/${state_id}.meta.json"
  if [[ ! -f "${first}" || ! -f "${second}" || ! -f "${first_meta}" || ! -f "${second_meta}" ]]; then
    echo "missing capture: ${state_id}"
    exit 1
  fi
  first_sha="$(shasum -a 256 "${first}" | awk '{print $1}')"
  second_sha="$(shasum -a 256 "${second}" | awk '{print $1}')"
  if [[ "${first_sha}" != "${second_sha}" ]]; then
    echo "unstable capture: ${state_id} ${first_sha} != ${second_sha}"
    exit 1
  fi
  if ! cmp -s "${first_meta}" "${second_meta}"; then
    echo "unstable metadata: ${state_id}"
    exit 1
  fi
  cp "${second}" "${screen_dir}/${state_id}.png"
  width="$(sips -g pixelWidth "${second}" | awk '/pixelWidth/ {print $2}')"
  height="$(sips -g pixelHeight "${second}" | awk '/pixelHeight/ {print $2}')"
  orientation="portrait"
  if (( width > height )); then orientation="landscape"; fi
  app_bounds="$(jq -c '.appBoundsPixels' "${second_meta}")"
  os_masks="$(jq -c '.osMasks' "${second_meta}")"
  screens="$(jq \
    --arg id "${state_id}" \
    --arg file "screenshots/${state_id}.png" \
    --arg original "pass-2/${state_id}.png" \
    --arg sha "${second_sha}" \
    --arg orientation "${orientation}" \
    --argjson width "${width}" \
    --argjson height "${height}" \
    --argjson appBounds "${app_bounds}" \
    --argjson osMasks "${os_masks}" \
    '. + [{id:$id,file:$file,originalFile:$original,sha256:$sha,originalSha256:$sha,pixelWidth:$width,pixelHeight:$height,orientation:$orientation,appBoundsPixels:$appBounds,osMasks:$osMasks}]' \
    <<<"${screens}")"
done

revision="$(git -C "${project_dir}" rev-parse HEAD)"
dirty=false
if [[ -n "$(git -C "${project_dir}" status --porcelain)" ]]; then dirty=true; fi
model="$(${adb_bin} -s "${serial}" shell getprop ro.product.model | tr -d '\r')"
os_version="$(${adb_bin} -s "${serial}" shell getprop ro.build.version.release | tr -d '\r')"
sdk_version="$(${adb_bin} -s "${serial}" shell getprop ro.build.version.sdk | tr -d '\r')"
density="$(${adb_bin} -s "${serial}" shell wm density | awk '/Physical density/ {print $3}' | tr -d '\r')"

jq -n \
  --arg fixture "ui_catalog_v2" \
  --arg fixedClock "2026-08-15T07:42:05+09:00" \
  --arg device "${model}" \
  --arg serial "${serial}" \
  --arg os "Android ${os_version}" \
  --arg sdk "${sdk_version}" \
  --arg density "${density}" \
  --arg revision "${revision}" \
  --argjson dirty "${dirty}" \
  --argjson screens "${screens}" \
  '{schemaVersion:2,fixtureId:$fixture,platform:"android",sharedProfile:{locale:"ko-KR",timezone:"Asia/Seoul",theme:"dark",animations:false,fontScale:1.0},fixedClock:$fixedClock,stateThemeOverrides:{settings_top:"orange",settings_midnight_theme:"midnight",clock_font_options:"midnight"},deviceProfile:{device:$device,serial:$serial,os:$os,sdk:$sdk,densityDpi:$density},revision:$revision,dirty:$dirty,repeatCount:2,stable:true,screens:$screens}' \
  > "${artifact_dir}/manifest.json"

trap - EXIT INT TERM
restore_device
echo "Android matchup catalog: ${artifact_dir}"
