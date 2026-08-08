from pydantic import BaseModel


class DocumentIn(BaseModel):
    doc_id: str
    title: str
    content: str
    source_path: str | None = None


class IngestRequest(BaseModel):
    documents: list[DocumentIn]


class IngestResponse(BaseModel):
    ingested: int
    document_ids: list[str]


class SearchResult(BaseModel):
    doc_id: str
    title: str
    snippet: str
    score: float


class SearchResponse(BaseModel):
    query: str
    results: list[SearchResult]
