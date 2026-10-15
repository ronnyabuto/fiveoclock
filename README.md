# 5PM Clock

A React web clock and an offline native Android home-screen widget showing the city closest to 5 PM.

## Web app

Requires Node 24+ and npm.

```sh
npm ci
npm run dev
```

Checks: `npm test`, `npm run lint`, and `npm run build`.

## Android widget

The Android companion lives in this repository under [`android/`](android/README.md).
It uses Kotlin and native widget clocks; no JavaScript runtime or server is needed for the widget.
Tapping opens <https://fiveoclock.vercel.app/> in your browser.

```sh
npm run android:prepare
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

See the [Android build and installation guide](android/README.md) for SDK setup, release signing,
installation, device tests, scheduling behavior, and timezone-data compatibility.
