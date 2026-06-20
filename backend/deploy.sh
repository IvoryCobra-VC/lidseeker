#!/usr/bin/env bash
#
# One-command deploy for the lidseeker backend.
#
# Run this from the repo checkout whenever you want to pull the latest changes
# and put them live:
#
#     ./deploy.sh
#
# It does everything in order and stops if any step fails, so you never end up
# half-updated:
#   1. grabs the latest code from your git remote
#   2. rebuilds and restarts the app
#   3. checks the app is actually answering before declaring success
#
# Note: this runs the base (Lidarr-native) compose. If you use the Soularr
# overlay, deploy with your own compose -f ... command instead.
set -euo pipefail
cd "$(dirname "$0")"

echo "==> 1/3  Getting the latest code from your git remote..."
git pull --ff-only

echo "==> 2/3  Rebuilding and restarting the app..."
docker compose up -d --build lidseeker

echo "==> 3/3  Checking the app is healthy..."
# Give it a few seconds to come up, then poll the health endpoint.
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
    echo "   Now at: $(git rev-parse --short HEAD)"
else
    echo "❌ The app did NOT come up healthy. Recent logs:"
    docker compose logs --tail=25 lidseeker
    echo
    echo "Nothing else changed automatically. You can roll back with:"
    echo "    git reset --hard HEAD~1 && ./deploy.sh"
    exit 1
fi
