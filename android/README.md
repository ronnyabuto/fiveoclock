# Android companion

Native home-screen widget for Android 13+ (API 33), including the Galaxy S23 Ultra.
The default city-and-time layout targets 4×2 launcher cells. Digital and analogue styles each
adapt to short, compact, and tall bounds, including tall narrow cards. The launcher
determines actual dimensions, which are used to fit the time without clipping.

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

Open the app once after installation. Digital minutes are the default; seconds and an analogue
style are optional. Touch and hold a widget and use the launcher's configuration action to change
that widget's style or seconds. In the app, **Apply to all widgets** updates every existing
widget and the default appearance, replacing individual style/seconds overrides.
Existing explicit seconds preferences are retained on upgrade.

The default tap destination is `https://fiveoclock.vercel.app/`; leave it blank to open settings.
The website applies to all instances. Normal widgets have one action: tap to open the full clock.
An unavailable widget instead says **Tap to retry**. Refresh is also available in app settings.

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

There are no countdown-only alarms at 17:00 or the circular-distance reset. If a dataset
has a single fixed-offset clock, no future event is needed; the native clock keeps ticking.

Exact ties retain the web comparator's preference. A tie and the next millisecond can have
different winners. Alarm delivery is scheduled 50 ms past the calculated event so a tie does not
require a second alarm one millisecond later. Delayed deliveries recompute from the actual current time.

`TextClock` and `AnalogClock` tick in the widget host, avoiding app-managed per-second updates.
Digital time follows the device's 12/24-hour preference. The analogue seconds frequency is
controlled by the system, not the display refresh rate. Each style shows one clock; UTC offset,
countdown, LIVE indicator, and settings toolbar are omitted from the widget face.
The selected city and UTC offset remain available under **Clock details** in the app.

Typography is sized for the launcher's reported bounds, with responsive fallbacks when those
bounds are unavailable. Larger font settings remove optional country text first, shorten the
compact context label, and preserve legible city/time content. Long cities can wrap on tall
layouts and retain their full accessible text. The floating layout has no surrounding card;
text shadows and dial styling remain independent of update scheduling.

One explicit immutable handover PendingIntent serves all instances. `USE_EXACT_ALARM` is declared
for this private API-33+ clock build. `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` schedules the
next handover with an idle-capable, waking alarm. If exact access is unavailable, the fallback
uses `setAndAllowWhileIdle(RTC_WAKEUP, ...)`. Each delivery selects the city for the actual
current time and replaces the pending alarm before rendering widgets, so a rendering failure
cannot break the handover chain. A separate inexact 30-minute recovery alarm
repairs state if a handover delivery is lost. It uses elapsed time and is renewed on each update.
Both alarms are registered explicitly and cancelled when the last widget is
removed; launcher periodic updates stay disabled. Recovery is a backstop, not the normal
handover cadence.

This trades a few short device wakeups per hour for automatic location freshness. Android can
still throttle idle-capable alarms and Samsung can restrict background apps; this does not
guarantee millisecond delivery or bypass force-stop. No foreground service, app-managed wake
lock, network permission, or battery-optimization exemption is used.

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

Instrumentation checks Android timezone parity, launcher configuration results/cancellation,
per-widget preferences, unavailable-state recovery, and RemoteViews rendering across styles,
minimum/intermediate/tall bounds, normal/1.3×/2× font scales, and long city names.

For reproducible visual checks on a dedicated emulator:

```sh
bash scripts/verify-widget-ui.sh emulator-5556
```

This builds and installs the debug APK, runs all device tests in 24-hour mode, reruns rendering
in 12-hour mode, restores the original time setting, and saves native PNG captures and results
under `.local/appearance-evidence/`. This script requires ADB, ripgrep, and tar.
Captures are native views drawn at specified bounds, not
Samsung home-screen screenshots. The script refuses physical-device serials to avoid changing
a personal phone's settings or replacing its signed release with a debug build.

To exercise automatic city handovers with a debug build and a pinned widget on a dedicated
emulator:

```sh
python3 scripts/verify-widget-handover.py emulator-5556 --kill-process --output .local/handover-evidence/awake
python3 scripts/verify-widget-handover.py emulator-5556 --idle --output .local/handover-evidence/idle
```

The script moves the emulator clock just before the scheduled handover and checks that an
alarm changes the city and arms a future event without tapping the widget. `--idle` forces
Doze during delivery; `--kill-process` verifies delivery after the background process exits.
Clock, automatic-time and deep-idle settings are restored afterward.
It requires a debug APK for `run-as` and logs, refuses physical devices, and saves before/after
alarm state and diagnostics. Run these independently: idle-capable alarm quotas use elapsed
real time, so repeated accelerated tests may be throttled even when wall time advances.

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
instances, removal of the last widget, and website/retry taps plus launcher reconfiguration. Observe overnight
battery usage without other test workloads. Do not interpret ticking seconds as proof that
city selection is fresh. Emulator checks cover native rendering and configuration; One UI checks still need the phone; see [validation.md](validation.md) for the recorded local checks.

## Primary references

- [RemoteViews supported views](https://developer.android.com/reference/android/widget/RemoteViews)
- [Alarm delivery and permissions](https://developer.android.com/develop/background-work/services/alarms)
- [AnalogClock widget support](https://developer.android.com/reference/android/widget/AnalogClock)
- [Chronometer timebase and formatting](https://developer.android.com/reference/android/widget/Chronometer)
- [Responsive widget sizing](https://developer.android.com/develop/ui/views/appwidgets/layouts)
- [Samsung app management](https://developer.samsung.com/mobile/app-management.html)
