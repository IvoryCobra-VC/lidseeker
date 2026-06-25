"""A tiny in-process, per-key sliding-window rate limiter.

Single-process uvicorn, so an in-memory store is sufficient — no Redis. Used to
throttle login attempts (brute-force protection) but generic enough for reuse.
"""
import time
from collections import defaultdict, deque

from fastapi import HTTPException, Request

from . import config


class SlidingWindowLimiter:
    def __init__(self, max_events: int, window_seconds: int) -> None:
        self.max_events = max_events
        self.window = window_seconds
        self._hits: dict[str, deque[float]] = defaultdict(deque)

    def _prune(self, key: str) -> deque[float]:
        cutoff = time.monotonic() - self.window
        q = self._hits[key]
        while q and q[0] < cutoff:
            q.popleft()
        return q

    def is_blocked(self, key: str) -> bool:
        """Read-only: True once `key` has hit the cap within the window."""
        return len(self._prune(key)) >= self.max_events

    def register(self, key: str) -> None:
        """Record one event against `key`."""
        self._prune(key).append(time.monotonic())

    def clear(self, key: str) -> None:
        """Reset the counter for `key` (e.g. after a successful login)."""
        self._hits.pop(key, None)

    async def __call__(self, request: Request) -> None:
        """FastAPI dependency: raise 429 and register a hit when over the cap."""
        ip = client_ip(request)
        if self.is_blocked(ip):
            raise HTTPException(429, "Too many requests. Please wait a moment.")
        self.register(ip)


def client_ip(request: Request) -> str:
    """Best-effort client IP. Honours the left-most X-Forwarded-For entry only
    when TRUST_PROXY is set (otherwise the header is attacker-controlled)."""
    if config.TRUST_PROXY:
        fwd = request.headers.get("x-forwarded-for")
        if fwd:
            return fwd.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


login_limiter = SlidingWindowLimiter(
    config.LOGIN_RATE_LIMIT_ATTEMPTS, config.LOGIN_RATE_LIMIT_WINDOW_SECONDS
)
