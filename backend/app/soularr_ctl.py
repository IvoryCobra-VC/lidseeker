"""Force an immediate Soularr run.

Soularr is a headless script with no trigger API — it runs its search cycle once
on container start, then sleeps SCRIPT_INTERVAL (300s). So "search now" = restart
the container.

The restart goes through a locked-down nginx proxy (the `docker-proxy` service)
that exposes ONLY `POST /containers/soularr/restart`. This app never sees the raw
Docker socket, so a compromise here can't create/exec/kill anything.
"""
import httpx

from . import config


async def trigger_run() -> None:
    url = f"{config.DOCKER_PROXY_URL}/containers/{config.SOULARR_CONTAINER}/restart"
    async with httpx.AsyncClient(timeout=30.0) as c:
        r = await c.post(url)
        # Docker returns 204 on success (304 if already restarting).
        if r.status_code not in (204, 304):
            raise RuntimeError(f"Restart failed ({r.status_code}): {r.text[:200]}")
