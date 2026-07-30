from langchain_openai import OpenAIEmbeddings

from app.settings import settings


class OpenAIEmbeddingAdapter:
    def __init__(self) -> None:
        self._embeddings = OpenAIEmbeddings(
            api_key=settings.openai_api_key,
            model=settings.embedding_model,
        )

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return self._embeddings.embed_documents(texts)

    def embed_query(self, text: str) -> list[float]:
        return self._embeddings.embed_query(text)
