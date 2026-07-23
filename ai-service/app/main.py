from fastapi import FastAPI

app = FastAPI(title="TrustDesk AI Service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
