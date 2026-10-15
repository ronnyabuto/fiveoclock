# Validation record

## Release 1.1.1: analogue settings fix

Recorded on 2026-10-15, based on `5292318`.

The new ActivityScenario regression reproduces an existing Digital widget override surviving
an app-level Analogue save in 1.1.0. It fails before the fix and passes afterward. App-level
**Apply to all widgets** now clears appearance overrides atomically with saving defaults;
launcher configuration still applies to one widget. The dark green background is unchanged.

- Debug and release unit suites: 8 tests passed each; both lint reports: zero issues.
- API 35 emulator instrumentation: all 7 tests passed, including the new regression,
  existing per-widget/default isolation, launcher save/cancel, timezone parity and rendering.
- Signed APK: versionCode 3, versionName 1.1.1, 86,120 bytes. Signature verified with the same
  certificate as previous releases.
- APK: `.local/artifacts/fivepm-clock-1.1.1.apk`.
- SHA-256: `7dbdaa1e93f1ca8b7634612020e3a9e883fc7093d7672f5ffcf23abd4406b82e`.

Build reproduction uses the signed-build command below. Instrumentation reproduction:

```sh
adb -s emulator-5556 install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w com.fivepm.clock.test/androidx.test.runner.AndroidJUnitRunner
```

Before/after results are retained under `.local/android-tools/analogue-regression-before.txt`
and `analogue-regression-after.txt`; build output is in `release-1.1.1-build.txt` in that directory.
The pinned AOSP launcher widget was configured individually as Digital, then changed to
Analogue through the app-level action; the resulting face is captured in
`.local/appearance-evidence/fix-home.png`. Physical S23 Ultra validation remains separate
from the emulator checks.

## Release 1.1.0: appearance redesign

Recorded on 2026-10-15 for release 1.1.0, branch `feat/widget-appearance`, based on
`042ec8fa15856344ccef87da5fcd172ef5aae723`.

| Check | Result |
| --- | --- |
| Web Vitest suite | 13 tests passed across 4 files |
| Web production build and ESLint | Passed |
| Kotlin debug and release unit suites | 8 tests passed in each variant |
| Recorded-input selection parity | 1,000 / 1,000 cases passed |
| Live desktop-JVM timezone compatibility | 936 / 1,000 complete input tables matched Node |
| Live Android timezone compatibility | 770 / 1,000 complete input tables matched Node |
| Debug and release Android lint | Zero issues |
| API 35 emulator instrumentation, 24-hour mode | 6 tests passed |
| API 35 emulator rendering repeat, 12-hour mode | 2 tests passed |
| Rendering coverage | 288 layout cases across both time formats |
| AOSP launcher smoke | Pinned widget, opened launcher reconfiguration, saved analogue style, observed updated widget |
| Signed minified release APK | Built; 85,836 bytes; versionCode 2 |
| APK signature | Verified, signature scheme v2, RSA 3072; same certificate as 1.0.0 |
| Bundled metadata | 315 ranked zones |
| One UI visual, timing and battery validation | Requires actual S23 Ultra |

Rendering cases cover digital and analogue styles, four minimum layout-family sizes per style,
two additional intermediate/tall sizes, font scales 1×/1.3×/2×, seconds on/off, and ordinary/long
city names. Assertions check bounds, clock formatting, text clipping and RemoteViews actions.
Configuration tests check save/cancel results and per-widget settings. Unavailable-state recovery
is also tested. These checks do not measure launcher frame timing or battery use.

Node and the desktop JVM disagree on rules for `America/Vancouver` and `Europe/Chisinau`
at some fixture dates. The emulator also differs for `America/Asuncion`. All 1,000 selection
cases pass using the recorded offset inputs. Live-runtime winner parity is required for cases
whose complete offset tables match; incompatible tables are reported separately. This is not
a claim of 1,000-case live-runtime parity. The installed widget uses the phone's timezone rules.

Reproduce the builds from the repository root:

```sh
npm run lint
npm test
npm run build
bash scripts/build-android-release.sh --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
bash scripts/verify-widget-ui.sh emulator-5556
```

The last command requires a dedicated API 33+ emulator. It installs debug builds, runs tests in
both time formats, restores the original format, and retains native-view PNG captures and test
output in `.local/appearance-evidence/`. These controlled native-view samples are distinct from
`home-digital.png` and `home-analogue.png`, captured from the actual AOSP emulator launcher.
Neither is a Samsung screenshot. Build output is retained in
`.local/android-tools/release-1.1-build.txt` and `.local/android-tools/final-ui-check.txt`.
JUnit XML and lint reports are under `android/app/build/`.

Signature and manifest verification (adjust the build-tools path for your SDK):

```sh
/usr/lib/android-sdk/build-tools/35.0.0/apksigner verify --verbose --print-certs .local/artifacts/fivepm-clock-1.1.0.apk
/usr/lib/android-sdk/build-tools/35.0.0/aapt2 dump badging .local/artifacts/fivepm-clock-1.1.0.apk
```

The release requests only `USE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED`, targets API 36,
and requires API 33+. It has no network permission. Its default tap destination is
`https://fiveoclock.vercel.app/`.

Release APK SHA-256:

```text
ef040b14cfd5a6fc7af152db73ec06b5abe1c3d76ea6f70e6d5bd945f5cc47e8
```

Signing certificate SHA-256:

```text
9fb2cdf8d9857b07b64b3f6cf36bdd16d60057a8999bf53c86041c2b57e2ce8a
```

The installable copy and checksum are at `.local/artifacts/fivepm-clock-1.1.0.apk` and
`.local/artifacts/fivepm-clock-1.1.0.apk.sha256`. The private signing key and its environment
configuration remain ignored under `.local/`, outside the APK. Preserve them securely to
issue compatible updates.

Physical-device checks still needed: One UI resizing and reconfiguration, handover visible-frame
latency, idle/unlock recovery, reboot, Samsung background policies, and overnight battery use.
See the [device verification instructions](README.md#device-verification).
