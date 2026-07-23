from fastapi import FastAPI

from app.routers import documents, internal

app = FastAPI(title="TrustDesk AI Service")
app.include_router(documents.router)
app.include_router(internal.router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
