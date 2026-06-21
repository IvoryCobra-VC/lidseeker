"""P2 hygiene: lifespan startup/shutdown, single version source surfaced at
/api/health, and request ownership enforcement (403) on delete/retry."""
import asyncio

import pytest
from fastapi import HTTPException

from app import __version__, config, db, main


def _setup(tmp_path, monkeypatch):
    monkeypatch.setattr(config, "DB_PATH", str(tmp_path / "p2.db"))
    db.init()


class _User:
    """A non-admin acting user."""
    def __init__(self, uid: int):
        self.id = uid
        self.username = f"u{uid}"
        self.role = "user"
        self.is_admin = False


class _Admin:
    id = 0
    username = "admin"
    role = "admin"
    is_admin = True


def _make_request(owner_id: int, foreign_id: str = "fk1") -> dict:
    return db.upsert_request(
        type="album", foreign_id=foreign_id, title="Some Album", artist="Some Artist",
        image_url=None, lidarr_artist_id=None, lidarr_album_id=None,
        status="pending", user_id=owner_id,
    )


# --- version is single-sourced and surfaced --------------------------------

def test_health_reports_single_sourced_version(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch)
    resp = asyncio.run(main.health())
    import json
    body = json.loads(resp.body)
    assert resp.status_code == 200
    assert body["db"] is True
    assert body["version"] == __version__


def test_app_version_matches_package_version():
    # The FastAPI app must advertise the one __version__, not a stale literal.
    assert main.app.version == __version__


# --- lifespan replaces on_event without leaving the old hooks --------------

def test_lifespan_inits_db_and_closes_client(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch)
    # Don't let the background loops actually poll Lidarr during the test.
    monkeypatch.setattr(main, "_status_loop", lambda: asyncio.sleep(0))
    monkeypatch.setattr(main, "_reconcile_loop", lambda: asyncio.sleep(0))
    closed = {"v": False}

    async def _fake_close():
        closed["v"] = True

    monkeypatch.setattr(main.lidarr, "aclose", _fake_close)

    async def _run():
        async with main.lifespan(main.app):
            assert db.ping() is True   # startup ran db.init()
        assert closed["v"] is True     # shutdown closed the shared client

    asyncio.run(_run())


def test_no_deprecated_on_event_hooks():
    # The lifespan migration should have removed the old startup/shutdown hooks.
    import inspect
    src = inspect.getsource(main)
    assert 'on_event(' not in src


# --- ownership: a non-owner can't delete or retry someone else's request ----

def test_delete_request_rejects_non_owner(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch)
    req = _make_request(owner_id=1)
    with pytest.raises(HTTPException) as ei:
        asyncio.run(main.delete_request(req["id"], user=_User(2)))
    assert ei.value.status_code == 403
    assert db.get_request(req["id"]) is not None   # not deleted


def test_retry_request_rejects_non_owner(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch)
    req = _make_request(owner_id=1, foreign_id="fk2")

    class _BG:
        def add_task(self, *a, **k):
            raise AssertionError("retry should 403 before scheduling work")

    with pytest.raises(HTTPException) as ei:
        asyncio.run(main.retry_request(req["id"], background=_BG(), user=_User(2)))
    assert ei.value.status_code == 403


def test_admin_can_delete_any_request(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch)
    req = _make_request(owner_id=1, foreign_id="fk3")
    # No lidarr_album_id, so no upstream call — the delete is a pure DB op.
    out = asyncio.run(main.delete_request(req["id"], user=_Admin()))
    assert out["ok"] is True
    assert db.get_request(req["id"]) is None
