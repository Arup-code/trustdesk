from app.adapters.mock_adapter import MockModelAdapter
from app.adapters.model_adapter import ModelAdapter
from app.adapters.openrouter_adapter import OpenRouterAdapter
from app.settings import settings


def get_model_adapter() -> ModelAdapter:
    if settings.ai_model_mode == "openrouter":
        return OpenRouterAdapter()
    return MockModelAdapter()
