#!/usr/bin/env bash
#
# Update the lidseeker backend to the latest published image.
#
# Run this from the repo checkout (next to docker-compose.yml):
#
#     ./deploy.sh
#
# It pulls the newest prebuilt image from GHCR, restarts the app, and checks it
# answers before declaring success.
#
# Notes:
#   - This uses the base (Lidarr-native) compose. If you run the Soularr overlay,
#     pull/up with your own `docker compose -f ... ` command instead.
#   - To run from source rather than the published image, build with:
#       docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build
set -euo pipefail
cd "$(dirname "$0")"

echo "==> 1/2  Pulling the latest image and restarting..."
docker compose pull
docker compose up -d

echo "==> 2/2  Checking the app is healthy..."
ok=""
for _ in $(seq 1 15); do
    code="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5056/api/health || true)"
    if [ "$code" = "200" ]; then
        ok="yes"
        break
    fi
    sleep 2
done

echo
if [ -n "$ok" ]; then
    echo "✅ Done — the latest version is live and healthy."
else
    echo "❌ The app did NOT come up healthy. Recent logs:"
    docker compose logs --tail=25 lidseeker
    exit 1
fi
