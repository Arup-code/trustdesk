from app.adapters.embedding_adapter import EmbeddingAdapter
from app.adapters.mock_adapter import MockModelAdapter
from app.adapters.mock_embedding_adapter import MockEmbeddingAdapter
from app.adapters.model_adapter import ModelAdapter
from app.adapters.openai_embedding_adapter import OpenAIEmbeddingAdapter
from app.adapters.openrouter_adapter import OpenRouterAdapter
from app.settings import settings


def get_model_adapter() -> ModelAdapter:
    if settings.ai_model_mode == "openrouter":
        return OpenRouterAdapter()
    return MockModelAdapter()


def get_embedding_adapter() -> EmbeddingAdapter:
    if settings.ai_embedding_mode == "openai":
        return OpenAIEmbeddingAdapter()
    return MockEmbeddingAdapter()
