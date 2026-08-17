import base64
import hashlib
import hmac
import os
from datetime import datetime, timedelta, timezone

import jwt

from app.core.config import settings


def hash_password(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 310_000)
    return f"{base64.b64encode(salt).decode()}${base64.b64encode(digest).decode()}"


def verify_password(password: str, stored: str) -> bool:
    salt_text, digest_text = stored.split("$", maxsplit=1)
    expected = base64.b64decode(digest_text)
    actual = hashlib.pbkdf2_hmac("sha256", password.encode(), base64.b64decode(salt_text), 310_000)
    return hmac.compare_digest(actual, expected)


def create_access_token(user_id: int) -> str:
    expires_at = datetime.now(timezone.utc) + timedelta(minutes=settings.access_token_minutes)
    return jwt.encode({"sub": str(user_id), "exp": expires_at}, settings.secret_key, algorithm="HS256")


def read_user_id(token: str) -> int:
    payload = jwt.decode(token, settings.secret_key, algorithms=["HS256"])
    return int(payload["sub"])
