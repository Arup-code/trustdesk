import hmac

from fastapi import Header, HTTPException, status

from app.settings import settings


def verify_internal_key(x_internal_key: str | None = Header(default=None)) -> None:
    """FastAPI dependency gating every route this service exposes.

    This service is never meant to be called by anything other than the
    Java Core Service (see docs/superpowers/plans master plan: "Frontend
    talks only to the Java Core Service; it must never call the Python AI
    service directly"). Without this check, anything able to reach the
    ai-service's port could call /internal/triage or /documents/ingest
    directly, bypassing the Java service's JWT auth and, for /documents/
    ingest, letting an attacker poison the knowledge base with content a
    future draft could cite.
    """
    if x_internal_key is None or not hmac.compare_digest(x_internal_key, settings.internal_api_key):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="missing or invalid internal API key")
