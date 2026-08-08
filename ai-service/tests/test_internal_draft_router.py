from fastapi.testclient import TestClient

from app.main import app
from app.settings import settings

client = TestClient(app, headers={"X-Internal-Key": settings.internal_api_key})


def test_post_internal_draft_for_grounded_ticket():
    response = client.post("/internal/draft", json={
        "ticket_id": "tkt_9001",
        "subject": "Received damaged earbuds",
        "body": "My BlueBuds Air arrived with the left earbud cracked. Can I get a replacement?",
        "category": "refund",
        "customer": {"tier": "gold"},
        "order": {"status": "delivered"},
    })
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "generated"
    assert "KB-REFUND-001" in body["citations"]
    assert any(a["tool_name"] == "create_replacement_order" for a in body["recommended_actions"])


def test_post_internal_draft_rejects_missing_internal_key():
    unauthenticated_client = TestClient(app)
    response = unauthenticated_client.post("/internal/draft", json={
        "ticket_id": "tkt_9001", "subject": "x", "body": "y",
    })
    assert response.status_code == 401
