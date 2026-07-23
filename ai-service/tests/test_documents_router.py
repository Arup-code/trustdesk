from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


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
