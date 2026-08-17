from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import app


def token_for(client: TestClient, prefix: str) -> str:
    email = f"{prefix}-{uuid4().hex}@example.com"
    client.post("/auth/register", json={"email": email, "password": "secret123"})
    return client.post("/auth/login", json={"email": email, "password": "secret123"}).json()["access_token"]


def test_user_cannot_update_another_users_todo():
    """Removing the owner filter from the service must expose this security bug."""
    with TestClient(app) as client:
        owner_token = token_for(client, "owner")
        other_token = token_for(client, "other")
        headers = {"Authorization": f"Bearer {owner_token}"}
        created = client.post("/todos", headers=headers, json={"title": "我的私有任务"})
        assert created.status_code == 201
        todo_id = created.json()["id"]
        response = client.patch(
            f"/todos/{todo_id}",
            headers={"Authorization": f"Bearer {other_token}"},
            json={"completed": True},
        )

    assert response.status_code == 404


def test_todo_list_requires_a_bearer_token():
    """Removing the authentication dependency must make a private list public."""
    with TestClient(app) as client:
        response = client.get("/todos")

    assert response.status_code == 401
