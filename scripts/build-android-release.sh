#!/usr/bin/env bash
set -euo pipefail

repository_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_dir"

if [[ -z "${FIVEPM_KEYSTORE:-}" && -f .local/android-signing.env ]]; then
    source .local/android-signing.env
fi
: "${FIVEPM_KEYSTORE:?Set FIVEPM_KEYSTORE or provide .local/android-signing.env}"
: "${FIVEPM_STORE_PASSWORD:?Set FIVEPM_STORE_PASSWORD}"
: "${FIVEPM_KEY_ALIAS:?Set FIVEPM_KEY_ALIAS}"
: "${FIVEPM_KEY_PASSWORD:?Set FIVEPM_KEY_PASSWORD}"

npm run android:prepare
cd android
./gradlew testReleaseUnitTest lintRelease assembleRelease "$@"
