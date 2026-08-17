from fastapi.testclient import TestClient

from app.main import app


def test_registered_user_can_log_in_and_receive_bearer_token():
    """Removing token issuance would make this login contract fail."""
    with TestClient(app) as client:
        email = "ada@example.com"
        client.post("/auth/register", json={"email": email, "password": "secret123"})
        response = client.post("/auth/login", json={"email": email, "password": "secret123"})

    assert response.status_code == 200
    body = response.json()
    assert body["token_type"] == "bearer"
    assert isinstance(body["access_token"], str)
    assert body["access_token"]
