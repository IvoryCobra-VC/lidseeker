# lidseeker — Android app

A music request app for [Lidarr](https://lidarr.audio/) — the "seerr" for music. Search artists
and albums, tap **Request**, and lidseeker adds them to Lidarr (your download client takes it from
there). Track each request's status (Pending → Downloading → Available) on the **My Requests**
tab, with an expandable pipeline view and push notifications when an album is ready.

This app talks **only** to the lidseeker backend (see [`../backend`](../backend)), never to Lidarr
directly, so the Lidarr API key never leaves the server. The backend URL is entered at runtime,
so a single build works against any server.

## Build

Requires Android Studio (2024.1+) or the Android SDK + JDK 17.

```bash
# from this directory, with ANDROID_HOME / sdk.dir configured:
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and Run.

### Release APK

```bash
./gradlew assembleRelease     # unsigned — sign with your own keystore to install
```

## Install

Sideload the APK (`adb install app-debug.apk`, or copy it to the phone and open it).

## First run

1. **Server URL** — your backend's address, e.g. `https://music.example.com` (a reverse-proxied
   HTTPS hostname) or `http://192.168.1.10:5056` on the LAN (cleartext is allowed).
2. **Username / Password** — the credentials from the backend's `.env` (`APP_USER` and the
   password whose bcrypt hash is in `APP_PASS_HASH`).

## Tech

Kotlin · Jetpack Compose (Material 3) · Navigation Compose · Retrofit + OkHttp +
kotlinx.serialization · Coil · DataStore. Manual DI via `LidseekerApp`. Single-user auth (JWT).
The app adapts to the backend's capabilities — e.g. the FLAC/MP3 quality toggle only appears when
the backend runs the optional Soularr adapter. Note: artist artwork is often unavailable from
Lidarr metadata, so artists show a placeholder; album art is shown when available.
