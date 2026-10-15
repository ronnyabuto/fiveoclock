# Validation record

Recorded on 2026-10-15 for branch `feat/android-widget`, based on `83d80a18843e5b0029e982c04e9776e3f55c4f38`.

| Check | Result |
| --- | --- |
| Web Vitest suite | 13 tests passed across 4 files |
| Web production build | Passed |
| ESLint | Passed |
| Kotlin debug unit suite | 8 tests passed |
| Kotlin release unit suite | 8 tests passed |
| Recorded-input selection parity | 1,000 / 1,000 cases passed in both variants |
| Live desktop-JVM timezone compatibility | 936 / 1,000 complete input tables matched Node |
| Debug Android lint | Zero issues |
| Release Android lint | Zero issues |
| Debug APK | Built |
| Android instrumentation APK | Built, not executed |
| Signed minified release APK | Built; 68,067 bytes |
| APK signature | Verified, APK signature scheme v2, RSA 3072 |
| Bundled metadata | 315 ranked zones |
| Release packaging | No parity fixtures, keystore, or signing environment file in APK |
| Connected Android device | None (`adb devices -l` returned an empty device list) |
| One UI visual, timing and battery validation | Pending actual S23 Ultra |

Node and the desktop JVM disagree on rules for `America/Vancouver` and `Europe/Chisinau`
at some fixture dates. All selection cases pass when provided the same offset inputs.
The 64 incompatible live-runtime cases are explicitly reported, not claimed as runtime parity.
Android instrumentation reports the phone's compatibility independently.

Final Android command (from repository root):

```sh
bash scripts/build-android-release.sh --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Web commands:

```sh
npm run lint
npm test
npm run build
```

Signature and manifest verification:

```sh
apksigner verify --verbose --print-certs android/app/build/outputs/apk/release/app-release.apk
aapt2 dump badging android/app/build/outputs/apk/release/app-release.apk
```

The release requests only `USE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED`, targets API 36,
and requires API 33+. It has no network permission. Its tap destination is
`https://fiveoclock.vercel.app/`, verified to serve HTTP 200 (the user's HTTP URL redirects there).

Release APK SHA-256:

```text
34fd795b7cf2077131dbbaa972a3b78fd093d00147df61e9585c538aefe39f26
```

Signing certificate SHA-256:

```text
9fb2cdf8d9857b07b64b3f6cf36bdd16d60057a8999bf53c86041c2b57e2ce8a
```

An installable copy is saved at `.local/artifacts/fivepm-clock-1.0.0.apk` with its checksum.
Local XML test/lint evidence is retained beside it. The original APK is at
`android/app/build/outputs/apk/release/app-release.apk`.

The private signing key is `.local/signing/fivepm-release.p12`; its environment configuration
is `.local/android-signing.env`. Both are Git-ignored with file permissions 600.
Back these up securely to preserve the ability to issue compatible updates. They are not
part of the APK and must not be uploaded with it.

Instrumentation, launcher rendering, handover visible-frame latency, idle/unlock recovery,
Samsung background policies, reboot, font scaling, and battery measurements have not been
verified on a device. The [device verification instructions](README.md#device-verification)
explain the remaining checks. No emulator was installed during this task.
