# lidseeker — backend

A small FastAPI service: the "seerr" for Lidarr. The Android app talks to this; this talks to
Lidarr. It handles auth, search (proxying Lidarr's metadata lookup), request orchestration, and
a request history with live pipeline status. Your Lidarr API key never leaves the server.

## How a request works

Lidarr is artist-centric, so requesting an **album** means:

1. Look the album up by its MusicBrainz id to find its artist.
2. If the artist isn't in the library, add it with **no albums monitored**
   (`addOptions.monitor = "none"`).
3. Monitor just the requested album, make sure the artist itself is monitored, and fire an
   `AlbumSearch` so Lidarr hands it to your download client.
4. Track progress to completion.

Requesting an **artist** adds the whole discography, monitored + searched.

Status on `/api/requests` comes from Lidarr's `statistics.percentOfTracks`
(`0 → pending`, `0<x<100 → downloading`, `100 → available`), with a richer 5-stage pipeline
(requested → searching → downloading → importing → available) for the app's expandable view. A
request that finds no source after `SEARCH_GIVE_UP_ATTEMPTS` search cycles is marked **failed**.

## Run (Lidarr-native — works with any download client)

```bash
cp .env.example .env       # set LIDARR_URL + LIDARR_API_KEY, APP_PASS_HASH, JWT_SECRET
# generate a password hash:
docker run --rm ghcr.io/ivorycobra-vc/lidseeker python -c "import bcrypt; print(bcrypt.hashpw(b'YOURPASS', bcrypt.gensalt()).decode())"

docker compose up -d        # pulls ghcr.io/ivorycobra-vc/lidseeker
curl localhost:5056/api/health      # {"status":"ok"}
```

Runs on **port 5056** with host networking (so it reaches Lidarr at `localhost:8686`). The
SQLite request store lives in `./data`. Set `PUID`/`PGID`/`TZ` in your environment to match your
host user if needed.

The image is prebuilt and published to GHCR (multi-arch: amd64 + arm64). `./deploy.sh` pulls the
latest and restarts. To run from source instead of pulling:

```bash
docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build
```

## Optional: Soularr + slskd (Soulseek) adapter

If you fulfil downloads via [Soularr](https://github.com/mrusse/soularr) +
[slskd](https://github.com/slskd/slskd), enable the adapter for live Soulseek progress, a
FLAC/MP3 quality toggle, and a container-restart "search now":

```bash
# set SLSKD_API_KEY and the SOULARR_* vars in .env, and SOULARR_DIR to your Soularr config dir
docker compose -f docker-compose.yml -f docker-compose.soularr.yml up -d --build
```

The overlay mounts Soularr's config (for the quality toggle + retry/denylist) and adds a
locked-down nginx proxy that lets lidseeker restart **only** the `soularr` container — the raw
Docker socket is never exposed to the app. The adapter auto-enables when `SLSKD_API_KEY` is set
and Soularr's config path is readable; force it with `SOULARR_ENABLED=true|false`.

## Expose remotely

lidseeker has no TLS of its own — put it behind a reverse proxy (Caddy, nginx, Traefik, a
Cloudflare Tunnel, …) that forwards your public hostname to `localhost:5056`. That public URL is
what you enter as the **Server URL** in the app. For LAN-only use, the app accepts a cleartext
`http://<host>:5056`.

## Endpoints

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/login` | `{username,password}` → `{token}` |
| GET | `/api/search?term=&type=artist\|album\|track` | normalized results |
| GET | `/api/artist/{foreignId}/albums` | albums for an artist |
| GET | `/api/album/{foreignId}/tracks` | tracklist |
| POST | `/api/request` | `{type,foreignId}` — orchestrates + records |
| GET | `/api/requests` | history with live pipeline status |
| DELETE | `/api/requests/{id}` | remove + unmonitor |
| POST | `/api/requests/{id}/retry` | un-stick a failed/stuck request |
| GET | `/api/discover`, `/api/discover/categories` | unowned releases by your library artists |
| GET/PUT | `/api/settings` | quality (Soularr only) + ntfy info |
| GET | `/api/services` | "Open in" links (from `SERVICE_LINKS`) |
| POST | `/api/search-now` | force a search (alias: `/api/soularr/run`) |

All except login require `Authorization: Bearer <token>`.

## Config (`.env`)

**Required:** `LIDARR_URL`, `LIDARR_API_KEY`, `ROOT_FOLDER_PATH` (`/music`),
`QUALITY_PROFILE_ID` (1=Any), `METADATA_PROFILE_ID` (1=Standard), `TRIGGER_ALBUM_SEARCH`,
`APP_USER`, `APP_PASS_HASH`, `JWT_SECRET`, `JWT_TTL_HOURS`, `PORT`, `DB_PATH`.

**Optional — Soularr/slskd adapter:** `SOULARR_ENABLED` (auto), `SLSKD_URL`, `SLSKD_API_KEY`,
`SOULARR_CONTAINER`, `DOCKER_PROXY_URL`, `SOULARR_CONFIG_PATH`, `SOULARR_DENYLIST_PATH`.

**Optional — other:** `NTFY_URL`, `NTFY_TOPIC` (push notifications), `SERVICE_LINKS`
("Open in" chips), `MUSICBRAINZ_CONTACT` (point the MusicBrainz User-Agent at your fork),
`NOTIFY_POLL_SECONDS`, `RECONCILE_POLL_SECONDS`, `SEARCH_GIVE_UP_ATTEMPTS`,
`SEARCH_ATTEMPT_INTERVAL_SECONDS`.

See `.env.example` for the full annotated list.
