from fastapi.testclient import TestClient

from app.main import app
from app.settings import settings

client = TestClient(app, headers={"X-Internal-Key": settings.internal_api_key})


def test_ingest_and_search_roundtrip():
    response = client.post("/documents/ingest", json={
        "documents": [{
            "doc_id": "KB-ROUNDTRIP-001",
            "title": "Roundtrip Test Doc",
            "content": "This is a roundtrip test document about damaged earbuds replacement.",
            "source_path": "test.md",
        }]
    })
    assert response.status_code == 200
    assert response.json() == {"ingested": 1, "document_ids": ["KB-ROUNDTRIP-001"]}

    search_response = client.get("/documents/search", params={"q": "damaged earbuds"})
    assert search_response.status_code == 200
    body = search_response.json()
    assert body["query"] == "damaged earbuds"
    assert any(r["doc_id"] == "KB-ROUNDTRIP-001" for r in body["results"])


def test_search_over_real_kb_finds_refund_doc():
    response = client.get("/documents/search", params={"q": "damaged item replacement window"})
    assert response.status_code == 200
    doc_ids = [r["doc_id"] for r in response.json()["results"]]
    assert "KB-REFUND-001" in doc_ids


def test_ingest_rejects_missing_internal_key():
    unauthenticated_client = TestClient(app)
    response = unauthenticated_client.post("/documents/ingest", json={"documents": []})
    assert response.status_code == 401


def test_search_rejects_wrong_internal_key():
    wrong_key_client = TestClient(app, headers={"X-Internal-Key": "not-the-real-key"})
    response = wrong_key_client.get("/documents/search", params={"q": "anything"})
    assert response.status_code == 401
