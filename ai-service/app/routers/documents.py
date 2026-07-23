from fastapi import APIRouter, Depends

from app.retrieval.kb_index import KBIndex
from app.schemas.documents import IngestRequest, IngestResponse, SearchResponse
from app.security import verify_internal_key
from app.settings import settings

router = APIRouter(dependencies=[Depends(verify_internal_key)])
kb_index = KBIndex()
kb_index.load_directory(f"{settings.data_dir}/knowledge_base")


@router.post("/documents/ingest", response_model=IngestResponse)
def ingest_documents(request: IngestRequest) -> IngestResponse:
    ids = kb_index.ingest(request.documents)
    return IngestResponse(ingested=len(ids), document_ids=ids)


@router.get("/documents/search", response_model=SearchResponse)
def search_documents(q: str, k: int = 5) -> SearchResponse:
    results = kb_index.search(q, k=k)
    return SearchResponse(query=q, results=results)
