"""Push notifications via ntfy.

Self-hosted ntfy is a good fit for a homelab: the backend POSTs a message to a
topic, and the phone subscribes to that topic in the ntfy app. No Firebase/FCM.
"""
import logging

import httpx

from . import config

log = logging.getLogger("lidseeker")


def enabled() -> bool:
    return bool(config.NTFY_URL and config.NTFY_TOPIC)


async def publish(title: str, message: str, tag: str = "tada") -> None:
    if not enabled():
        return
    url = f"{config.NTFY_URL}/{config.NTFY_TOPIC}"
    # HTTP headers must be Latin-1 — an emoji in the Title (e.g. "🎵") raises
    # UnicodeEncodeError and, uncaught, kills the notify loop. Keep the header
    # ASCII-safe; the emoji can live in the UTF-8 body instead.
    safe_title = title.encode("ascii", "ignore").decode("ascii").strip() or "lidseeker"
    try:
        async with httpx.AsyncClient(timeout=10.0) as c:
            await c.post(
                url,
                content=message.encode("utf-8"),
                headers={
                    "Title": safe_title,
                    "Tags": tag,
                    "Priority": "default",
                },
            )
    except Exception as e:  # noqa: BLE001 — notifications must never crash the caller
        log.warning("ntfy publish failed: %s", e)
