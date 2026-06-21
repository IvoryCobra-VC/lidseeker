"""P1 auth model: token_version invalidation, DB-fresh identity, password
policy, and admin-only ntfy settings."""
import asyncio

import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app import auth, config, db, main, soularr_cfg
from app.main import _hash
from app.schemas import PasswordChange, UserCreate


def _setup(tmp_path, monkeypatch):
    monkeypatch.setattr(config, "DB_PATH", str(tmp_path / "auth.db"))
    db.init()


def _creds(token: str) -> HTTPAuthorizationCredentials:
    return HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)


class _Admin:
    id = 0
    username = "admin"
    role = "admin"
    is_admin = True


def test_token_version_bump_invalidates_token(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch)
    u = db.create_user("bob", _hash("password123"), "user")
    token = auth.issue_token(u)
    # Fresh token works, and role comes from the DB.
    cu = auth.require_user(_creds(token))
    assert cu.username == "bob" and cu.role == "user"
    # Bumping the version (as a password change does) kills the old token now.
    db.bump_token_version(u["id"])
    with pytest.raises(HTTPException) as ei:
        auth.require_user(_creds(token))
    assert ei.value.status_code == 401


def test_deleted_user_token_rejected(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch)
    u = db.create_user("gone", _hash("password123"), "user")
    token = auth.issue_token(u)
    db.delete_user(u["id"])
    with pytest.raises(HTTPException) as ei:
        auth.require_user(_creds(token))
    assert ei.value.status_code == 401


def test_create_user_rejects_short_password(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch)
    with pytest.raises(HTTPException) as ei:
        asyncio.run(main.create_user(UserCreate(username="x", password="short"), _admin=_Admin()))
    assert ei.value.status_code == 400


def test_change_password_policy_and_token_bump(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch)
    u = db.create_user("carol", _hash("password123"), "user")

    class _Carol:
        id = u["id"]
        username = "carol"
        role = "user"
        is_admin = False

    # Too-short new password is rejected.
    with pytest.raises(HTTPException) as ei:
        asyncio.run(main.change_my_password(
            PasswordChange(currentPassword="password123", newPassword="short"), user=_Carol()))
    assert ei.value.status_code == 400
    # Valid change bumps the token version (other sessions are signed out).
    asyncio.run(main.change_my_password(
        PasswordChange(currentPassword="password123", newPassword="newpassword456"), user=_Carol()))
    assert db.get_user_by_id(u["id"])["token_version"] == 1


def test_ntfy_settings_admin_only(tmp_path, monkeypatch):
    _setup(tmp_path, monkeypatch)
    monkeypatch.setattr(config, "NTFY_TOPIC", "secret-topic")
    monkeypatch.setattr(config, "NTFY_URL", "https://ntfy.sh")
    monkeypatch.setattr(soularr_cfg, "get_quality", lambda: None)

    class _User:
        id = 1
        username = "u"
        role = "user"
        is_admin = False

    admin_view = asyncio.run(main.get_settings(user=_Admin()))
    assert admin_view["ntfyTopic"] == "secret-topic"
    user_view = asyncio.run(main.get_settings(user=_User()))
    assert user_view["ntfyTopic"] is None and user_view["ntfyUrl"] is None
