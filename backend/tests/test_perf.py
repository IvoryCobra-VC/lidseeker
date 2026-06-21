"""P0 perf guards: the shared HTTP client is reused, and /api/requests fetches
Lidarr's queue once for the whole batch instead of once per request row."""
import asyncio

from app import config, db, lidarr, main


def test_shared_client_is_reused():
    a = lidarr._get_client()
    b = lidarr._get_client()
    assert a is b
    asyncio.run(lidarr.aclose())
    # A fresh client is created after close (not a reused, closed one).
    c = lidarr._get_client()
    assert c is not a
    asyncio.run(lidarr.aclose())


def test_album_cache_collapses_duplicate_fetches(monkeypatch):
    calls = {"n": 0}

    class _Resp:
        def raise_for_status(self):
            pass

        def json(self):
            return {"statistics": {"percentOfTracks": 50}}

    class _C:
        async def get(self, *a, **k):
            calls["n"] += 1
            return _Resp()

    import contextlib

    @contextlib.asynccontextmanager
    async def _fake_client():
        yield _C()

    monkeypatch.setattr(lidarr, "_client", _fake_client)
    lidarr._album_cache.clear()
    # Two reads of the same album within the TTL → one upstream GET.
    asyncio.run(lidarr._album_stats(123))
    asyncio.run(lidarr._album_stats(123))
    assert calls["n"] == 1


def test_list_requests_fetches_queue_once(monkeypatch, tmp_path):
    monkeypatch.setattr(config, "DB_PATH", str(tmp_path / "perf.db"))
    db.init()
    # Seed several in-flight requests.
    for i in range(5):
        db.upsert_request(
            type="album", foreign_id=f"fid-{i}", title=f"Album {i}", artist="A",
            image_url=None, lidarr_artist_id=10, lidarr_album_id=100 + i,
            status="downloading", user_id=1,
        )

    queue_calls = {"n": 0}

    async def _fake_queue_index():
        queue_calls["n"] += 1
        return {}

    async def _fake_status(album_id, artist_id):
        return "downloading"

    seen_queue = {"all_passed": True}

    async def _fake_pipeline(album_id, artist_id, artist, status, created_at, queue_index=None):
        if queue_index is None:
            seen_queue["all_passed"] = False
        return {"stage": "downloading"}

    monkeypatch.setattr(lidarr, "queue_index", _fake_queue_index)
    monkeypatch.setattr(lidarr, "request_status", _fake_status)
    monkeypatch.setattr(lidarr, "request_pipeline", _fake_pipeline)

    class _Admin:
        id = 1
        username = "admin"
        role = "admin"
        is_admin = True

    out = asyncio.run(main.list_requests(user=_Admin()))
    assert len(out) == 5
    assert queue_calls["n"] == 1            # one queue fetch for all 5 rows
    assert seen_queue["all_passed"]         # the batch index was passed to every row
