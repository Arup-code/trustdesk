import math

from app.retrieval.lexical import raw_idf, tokenize


class MockEmbeddingAdapter:
    """Deterministic, offline embedding adapter for tests/dev. Builds a
    bag-of-words vector space over whatever corpus embed_documents() last
    saw, weighting each term by the same zero-floored raw IDF formula
    KBIndex's BM25 branch uses for near-universal terms (see
    app.retrieval.lexical.raw_idf) -- so this mock and the BM25 branch
    agree on which terms carry any signal at all, keeping the hybrid
    search's "off-topic query returns nothing" guarantee intact under test.
    embed_query() reuses the vocabulary/weights from the most recent
    embed_documents() call, mirroring how BM25 itself needs corpus-wide
    statistics to score a query.
    """

    def __init__(self) -> None:
        self._vocabulary: dict[str, int] = {}
        self._weights: list[float] = []

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        token_lists = [tokenize(text) for text in texts]
        doc_count = len(token_lists)
        document_frequency: dict[str, int] = {}
        for tokens in token_lists:
            for term in set(tokens):
                document_frequency[term] = document_frequency.get(term, 0) + 1

        terms = sorted(document_frequency)
        self._vocabulary = {term: idx for idx, term in enumerate(terms)}
        self._weights = [max(0.0, raw_idf(document_frequency[term], doc_count)) for term in terms]

        return [self._vectorize(tokens) for tokens in token_lists]

    def embed_query(self, text: str) -> list[float]:
        return self._vectorize(tokenize(text))

    def _vectorize(self, tokens: list[str]) -> list[float]:
        vector = [0.0] * len(self._vocabulary)
        for term in tokens:
            idx = self._vocabulary.get(term)
            if idx is None:
                continue
            vector[idx] += self._weights[idx]
        norm = math.sqrt(sum(v * v for v in vector))
        if norm == 0:
            return vector
        return [v / norm for v in vector]
