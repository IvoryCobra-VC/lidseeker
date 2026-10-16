"""Issue #88: album search must not mark every album of a library artist as
"In library" just because /album/lookup returns a Lidarr id for it."""
import asyncio
import contextlib

import httpx

from app import lidarr

LOOKUP = [
    {"id": 1, "foreignAlbumId": "owned", "title": "Owned"},
    {"id": 2, "foreignAlbumId": "known", "title": "Known, not downloaded"},
    {"foreignAlbumId": "new", "title": "Not in Lidarr"},
]
LIBRARY = [
    {"id": 1, "statistics": {"trackFileCount": 12}},
    {"id": 2, "statistics": {"trackFileCount": 0}},
]


class _Resp:
    def __init__(self, data, status=200):
        self._data, self._status = data, status

    def raise_for_status(self):
        if self._status >= 400:
            req = httpx.Request("GET", "http://lidarr/api/v1/album")
            raise httpx.HTTPStatusError(
                "boom", request=req, response=httpx.Response(self._status, request=req))

    def json(self):
        return self._data


def _search(monkeypatch, album_status=200):
    calls = []

    class _C:
        async def get(self, path, params=None):
            calls.append((path, params))
            if path == "/album/lookup":
                return _Resp([dict(x) for x in LOOKUP])
            assert path == "/album"
            wanted = {v for k, v in params if k == "albumIds"}
            return _Resp([a for a in LIBRARY if a["id"] in wanted], album_status)

    @contextlib.asynccontextmanager
    async def _fake_client():
        yield _C()

    monkeypatch.setattr(lidarr, "_client", _fake_client)
    res = asyncio.run(lidarr.search("x", "album"))
    return {r["foreignId"]: r["inLibrary"] for r in res}, calls


def test_album_search_only_marks_downloaded_albums(monkeypatch):
    got, calls = _search(monkeypatch)
    assert got == {"owned": True, "known": False, "new": False}
    # One batched statistics call, for the known ids only.
    assert calls[1:] == [("/album", [("albumIds", 1), ("albumIds", 2)])]


def test_album_search_stats_failure_leaves_albums_requestable(monkeypatch):
    got, _ = _search(monkeypatch, album_status=500)
    assert got == {"owned": False, "known": False, "new": False}


def test_normalize_album_bare_id_is_not_in_library():
    assert lidarr.normalize_album({"id": 5})["inLibrary"] is False
    assert lidarr.normalize_album(
        {"id": 5, "statistics": {"trackFileCount": 3}})["inLibrary"] is True
