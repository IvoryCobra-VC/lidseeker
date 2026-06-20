# lidseeker

A self-hosted **music request app for [Lidarr](https://lidarr.audio/)** — the "seerr" for
music. Search for artists and albums, tap **Request**, and lidseeker adds them to Lidarr and
tracks each request from search → download → import → available, with push notifications when
something's ready.

Two parts:

- **`backend/`** — a small FastAPI service that sits in front of Lidarr. It handles auth,
  search, request orchestration, and live status. Your Lidarr API key never leaves the server.
- **`android/`** — a Kotlin / Jetpack Compose app that talks only to the backend. The server
  URL is entered at runtime, so one build works for anyone.

```
┌──────────┐      ┌─────────────┐      ┌────────┐      ┌──────────────────┐
│ Android  │ ───► │  lidseeker   │ ───► │ Lidarr │ ───► │  download client  │
│   app    │ HTTP │   backend   │ HTTP │        │      │ (SABnzbd/qBit/…)  │
└──────────┘      └─────────────┘      └────────┘      └──────────────────┘
```

## Requirements

- A running **Lidarr** instance with at least one **download client** configured (any of
  SABnzbd, qBittorrent, NZBGet, Deluge, Transmission, …).
- Docker + Docker Compose for the backend.
- Android Studio or the Android SDK + JDK 17 to build the app (or grab a release APK).

## Download backends

lidseeker works with **any** Lidarr download client out of the box — it reads download progress
straight from Lidarr's own queue ("Lidarr-native" mode). No extra setup.

There's also an **optional Soularr + slskd (Soulseek) adapter**. When enabled it adds live
Soulseek transfer progress, a FLAC/MP3 quality toggle, and a "search now" that re-runs Soularr's
cycle. It's auto-detected when you provide an slskd API key and mount Soularr's config (see
`backend/docker-compose.soularr.yml`). Controls that only apply to that adapter (e.g. the quality
toggle) are hidden in the app when it's off.

| Capability | Lidarr-native | Soularr + slskd |
|---|---|---|
| Request → monitor → import → available | ✅ | ✅ |
| Live download progress | ✅ (Lidarr queue) | ✅ (Soulseek transfers, earlier visibility) |
| Give-up after N searches → "Failed" | ✅ | ✅ |
| FLAC/MP3 quality toggle | — | ✅ |
| "Search now" | ✅ (Lidarr AlbumSearch) | ✅ (restart Soularr) |
| Push notifications (ntfy) | ✅ | ✅ |

## Quick start

```bash
# 1. Backend — pulls the prebuilt image from GHCR (no build needed)
cd backend
cp .env.example .env          # set LIDARR_URL/KEY, APP_PASS_HASH, JWT_SECRET
docker compose up -d
curl localhost:5056/api/health   # {"status":"ok"}

# 2. App — build a debug APK and sideload it
cd ../android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

The backend image is published at `ghcr.io/pauledwardodea-afk/lidseeker` (multi-arch,
amd64 + arm64). To build it from source instead of pulling:
`docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build`.

On first launch, enter your backend's URL (e.g. `http://192.168.1.10:5056` on the LAN, or your
reverse-proxied HTTPS address) plus the username/password from the backend's `.env`.

See **`backend/README.md`** and **`android/README.md`** for details.

## License

[MIT](LICENSE). lidseeker is an independent project. It integrates with Lidarr, and optionally
with Soularr and slskd, **only over their HTTP APIs (and, for the optional Soularr adapter, by
reading/writing Soularr's own config files)** — it contains no code from those projects, which
are separately licensed (Lidarr: GPL-3.0; Soularr, slskd: GPL-3.0).
