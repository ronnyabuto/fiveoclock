# Android companion

Native home-screen widget for Android 13+ (API 33), including the Galaxy S23 Ultra.
The wide layout targets 4×2 launcher cells; resizing down selects a compact digital layout.
The launcher determines the actual cell dimensions.

## Build

Install Node 24+, JDK 17 or 21, Android SDK platform 36, and build-tools 35.0.0.
Set `ANDROID_HOME` to the SDK directory, or put `sdk.dir=/absolute/sdk/path` in
`android/local.properties` (ignored by Git). The checked-in Gradle wrapper pins Gradle 8.14
and verifies its distribution checksum. AGP 8.11.1 and Kotlin 2.2.20 are pinned.

From the repository root:

```sh
npm ci
npm run android:prepare
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.
Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
Open **5PM Clock**, then choose **Add widget to home screen**. You can also long-press
the home screen and choose **Widgets → 5PM Clock**.

Open the app once after installation. Settings change seconds and the website for all widget instances.
The default website is `https://fiveoclock.vercel.app/`; leave it blank to open settings on tap.
The widget's **LIVE** button refreshes selection, and its gear opens settings.

## Signed releases

Keep signing material outside Git. Set these environment variables:

- `FIVEPM_KEYSTORE`: absolute path to the release keystore.
- `FIVEPM_STORE_PASSWORD`: keystore password.
- `FIVEPM_KEY_ALIAS`: signing-key alias.
- `FIVEPM_KEY_PASSWORD`: key password.

For a new signing identity, create a password-protected keystore interactively (do not replace
an existing key used for installed releases):

```sh
keytool -genkeypair -alias fivepm -keyalg RSA -keysize 3072 -validity 10000 \
  -storetype PKCS12 -keystore /your/secure/path/fivepm-release.p12
```

Alternatively, store `export` assignments in `.local/android-signing.env` at the repository root,
with permissions `600`. `.local/`, `.jks`, and `.keystore` files are ignored. Do not share
the environment file or keystore with the APK. Back up both securely: future updates must
use the same signing key.

```sh
bash scripts/build-android-release.sh
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

The release script regenerates data, runs release unit tests and lint, and builds a signed,
minified APK. Direct `assembleRelease` also requires signing configuration; it does not silently
produce an unsigned release. Debug and release builds have the same app ID but different keys;
if switching between them, uninstall the previous build first (this removes its widgets/settings).

## Selection and scheduling

`src/domain/selection.ts` owns city naming and the complete web selection comparator. The exporter
produces ranked metadata and 1,000 reference cases, including quarter-hour offsets, exact ties,
midnight wrap, and 2026 DST instants. Generated files are checked in. A source digest makes
Gradle reject stale data after changes to the domain, exporter, or npm lockfile.

`TimeDomain` uses Android's `java.time` rules. Its next event is the earliest of:

1. A winning-city change at a circular midpoint between local-17:00 UTC phases.
2. An offset transition in any candidate timezone, including competitors.
3. A timer-direction change at 17:00 (or the circular-distance reset for sparse datasets).

Exact ties retain the web comparator's preference. A tie and the next millisecond can have
different winners. Delayed deliveries recompute from the actual current time.

`TextClock` and `AnalogClock` tick in the widget host, avoiding app-managed per-second updates.
The analogue seconds frequency is controlled by the system, not the display refresh rate.
The distance `Chronometer` uses elapsed realtime and shows `MM:SS before/after 5`; it does not
reproduce the web pill's `Xm Ys` format. Disabling seconds affects both clocks, not the timer.

One explicit immutable alarm PendingIntent serves all instances. `USE_EXACT_ALARM` is declared
for this private API-33+ clock build. When available, `setExact(RTC, ...)` schedules the next event
without waking a sleeping device. The fallback uses an inexact alarm. Neither mode guarantees
an immediate first home-screen frame after sleep. No WorkManager, polling service, wake lock,
network permission, or battery-optimization exemption is used.

Boot, wall-clock/timezone changes, package replacement, widget creation/resizing, and interactions
recalculate state. Removing the last widget cancels the alarm. Android force-stop can suspend
background delivery until the app is opened again. Store distribution requires reviewing
exact-alarm eligibility separately.

## Timezone database differences

Node/ICU, the desktop JVM, and Android can ship different IANA rule versions. Fixtures record the
actual offset inputs as well as expected outputs. The shared parity suite:

- Requires all 1,000 selection cases to match using those recorded offsets.
- Requires live-runtime winner parity for every case whose full offset table matches.
- Reports incompatible inputs and affected zones explicitly; they are not counted as live parity.
- Rejects unsupported bundled zone IDs rather than silently skipping them in verification.

This separates selection defects from timezone-rule updates. The installed widget always uses
the phone's current rules. Run instrumentation to establish compatibility on the actual phone.

## Device verification

With USB debugging enabled and your phone authorized:

```sh
adb devices -l
cd android
./gradlew connectedDebugAndroidTest
```

Instrumentation checks Android timezone parity and applies RemoteViews actions for both layouts,
seconds settings, before/at/after 17:00, and unavailable data. It does not prove One UI scheduling.

For a debug build, capture scheduler events:

```sh
adb logcat -v epoch -s FivePmClock:I
adb shell dumpsys alarm > alarm-state.txt
```

Logs include requested/received timestamps, lateness, next event, and selected city. Settings
shows the last alarm delay on release builds too. Receiver/update-submission times are not
proof of the launcher's visible-frame timing: capture a screen recording around handovers.

On the S23 Ultra, record visible handover delay, recovery after overnight idle, battery saver,
Samsung sleeping-app modes, reboot, manual clock changes, resizing, large fonts, multiple
instances, removal of the last widget, and website/refresh/settings taps. Observe overnight
battery usage without other test workloads. Do not interpret ticking seconds as proof that
city selection is fresh. ADB instrumentation and One UI checks remain pending until a device
is connected; see [validation.md](validation.md) for the recorded local checks.

## Primary references

- [RemoteViews supported views](https://developer.android.com/reference/android/widget/RemoteViews)
- [Alarm delivery and permissions](https://developer.android.com/develop/background-work/services/alarms)
- [AnalogClock widget support](https://developer.android.com/reference/android/widget/AnalogClock)
- [Chronometer timebase and formatting](https://developer.android.com/reference/android/widget/Chronometer)
- [Responsive widget sizing](https://developer.android.com/develop/ui/views/appwidgets/layouts)
- [Samsung app management](https://developer.samsung.com/mobile/app-management.html)
