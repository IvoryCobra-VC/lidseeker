"""Discover browse ordering, album-search ranking and year sanitising."""

import asyncio
from unittest.mock import patch

from app import db, lidarr


def _album(title, artist, *, rel="2000-01-01", votes=0, secondary=None, atype="Album"):
    return {
        "title": title,
        "foreignAlbumId": f"rg-{title}",
        "artist": {"artistName": artist, "foreignArtistId": f"ar-{artist}"},
        "albumType": atype,
        "secondaryTypes": secondary or [],
        "releaseDate": f"{rel}T00:00:00Z",
        "ratings": {"votes": votes},
        "images": [],
        "genres": [],
    }


def test_year_drops_unknown_placeholder_dates():
    assert lidarr._year("0001-01-01T00:00:00Z") is None
    assert lidarr._year("1997-05-21") == 1997
    assert lidarr._year(None) is None


def test_normalize_album_falls_back_to_cover_art_archive_and_carries_artist():
    n = lidarr.normalize_album(_album("OK Computer", "Radiohead"))
    assert n["imageUrl"] == "https://coverartarchive.org/release-group/rg-OK Computer/front-500"
    assert n["artistForeignId"] == "ar-Radiohead"


def test_album_score_prefers_matching_artist_then_studio_then_popular():
    q = lidarr._norm_text("radiohead")
    other = _album("Radiohead", "Wesley Willis", votes=50)
    live = _album("Live 1995", "Radiohead", secondary=["Live"], votes=99)
    studio = _album("Kid A", "Radiohead", votes=10)
    studio_more = _album("OK Computer", "Radiohead", votes=40)
    ranked = sorted([other, live, studio, studio_more], key=lambda a: lidarr._album_score(a, q))
    assert [a["title"] for a in ranked] == ["OK Computer", "Kid A", "Live 1995", "Radiohead"]


def _run_discover(pool, **kw):
    entries = [(a["releaseDate"], a["genres"], a) for a in pool]

    async def fake_pool():
        return entries

    with (
        patch.object(lidarr, "_discover_pool", fake_pool),
        patch.object(db, "active_request_foreign_ids", lambda: set()),
    ):
        return [a["title"] for a in asyncio.run(lidarr.discover(**kw))]


def test_discover_new_is_newest_first():
    pool = [_album("Old", "A", rel="1991-01-01", votes=90), _album("New", "B", rel="2026-03-01")]
    assert _run_discover(pool) == ["New", "Old"]


def test_discover_decade_browses_by_popularity_not_recency():
    pool = [
        _album("Late 99 obscure", "A", rel="1999-12-01", votes=0),
        _album("Nevermind", "Nirvana", rel="1991-09-24", votes=72),
        _album("Violator", "Depeche Mode", rel="1990-03-19", votes=28),
        _album("Wrong decade", "C", rel="2001-01-01", votes=500),
    ]
    assert _run_discover(pool, decade=1990) == ["Nevermind", "Violator", "Late 99 obscure"]
