from fastapi import FastAPI

from app.routers import documents

app = FastAPI(title="TrustDesk AI Service")
app.include_router(documents.router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
