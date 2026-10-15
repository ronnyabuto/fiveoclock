#!/usr/bin/env bash
set -euo pipefail

serial="${1:?Pass an emulator serial, for example emulator-5556}"
[[ "$serial" == emulator-* ]] || { echo 'Use a dedicated emulator: this check temporarily changes its time format.' >&2; exit 1; }
repository_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_dir"
evidence_dir="$repository_dir/.local/appearance-evidence"
mkdir -p "$evidence_dir"
original_format="$(adb -s "$serial" shell settings get system time_12_24 | tr -d '\r')"
restore_format() {
    if [[ "$original_format" == null ]]; then
        adb -s "$serial" shell settings delete system time_12_24 >/dev/null
    else
        adb -s "$serial" shell settings put system time_12_24 "$original_format"
    fi
}
trap restore_format EXIT

npm run android:prepare
(cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon)
adb -s "$serial" install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for format in 24 12; do
    adb -s "$serial" shell settings put system time_12_24 "$format"
    arguments=()
    if [[ "$format" == 12 ]]; then arguments=(-e class com.fivepm.clock.widget.WidgetRenderingTest); fi
    adb -s "$serial" shell am instrument -w "${arguments[@]}" \
        com.fivepm.clock.test/androidx.test.runner.AndroidJUnitRunner > "$evidence_dir/instrumentation-$format.txt"
    cat "$evidence_dir/instrumentation-$format.txt"
    rg -q 'OK \([0-9]+ tests?\)' "$evidence_dir/instrumentation-$format.txt"
done
adb -s "$serial" exec-out run-as com.fivepm.clock tar -C cache/widget-evidence -cf - 24h 12h > "$evidence_dir/native-renderings.tar"
tar -xf "$evidence_dir/native-renderings.tar" -C "$evidence_dir"
echo "Native rendering captures and test results: $evidence_dir"
