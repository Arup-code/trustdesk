from app.adapters import get_embedding_adapter, get_model_adapter
from app.adapters.mock_adapter import MockModelAdapter
from app.adapters.mock_embedding_adapter import MockEmbeddingAdapter


def test_get_model_adapter_defaults_to_mock():
    assert isinstance(get_model_adapter(), MockModelAdapter)


def test_get_embedding_adapter_defaults_to_mock():
    assert isinstance(get_embedding_adapter(), MockEmbeddingAdapter)
