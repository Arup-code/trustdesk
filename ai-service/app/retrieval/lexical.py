import math
import re

_TOKEN_PATTERN = re.compile(r"[a-z0-9]+")

# Standard English function words. These carry no topical content in any
# corpus, so removing them is safe regardless of how big or small the KB is
# -- unlike the per-corpus dynamic IDF fix (raw_idf below), which only
# catches a word once it happens to be common *in this specific corpus*. A
# small KB can easily contain an ordinary word (e.g. "was") in only one
# document purely by chance of writing style, giving that word a genuinely
# positive, non-floored IDF that BM25 will legitimately treat as a strong,
# distinctive signal for that one document -- even though the word means
# nothing on its own. Verified case: "was" appears in exactly 1 of the 8
# real KB docs (KB-ADVERSARIAL-001, written in past-tense prose), which
# without this filter let an ordinary damaged-item ticket ("...the package
# was delivered...") retrieve and cite the prompt-injection decoy document.
# This list and raw_idf() are complementary, not redundant: this one
# removes words that are *never* meaningful; raw_idf() removes words that
# happen to be near-universal *in this particular KB* (e.g. "policy",
# "support") but are meaningful in general.
_STOPWORDS = frozenset({
    "a", "an", "the", "and", "or", "but", "if", "of", "at", "by", "for",
    "with", "about", "against", "between", "into", "through", "during",
    "before", "after", "above", "below", "to", "from", "up", "down", "in",
    "out", "on", "off", "over", "under", "again", "further", "then",
    "once", "here", "there", "when", "where", "why", "how", "all", "any",
    "both", "each", "few", "more", "most", "other", "some", "such", "no",
    "nor", "not", "only", "own", "same", "so", "than", "too", "very",
    "is", "am", "are", "was", "were", "be", "been", "being", "have",
    "has", "had", "having", "do", "does", "did", "doing", "will", "would",
    "shall", "should", "can", "could", "may", "might", "must", "this",
    "that", "these", "those", "i", "me", "my", "myself", "we", "our",
    "ours", "ourselves", "you", "your", "yours", "yourself", "yourselves",
    "he", "him", "his", "himself", "she", "her", "hers", "herself", "it",
    "its", "itself", "they", "them", "their", "theirs", "themselves",
    "what", "which", "who", "whom", "as", "until", "while",
})


def tokenize(text: str) -> list[str]:
    return [tok for tok in _TOKEN_PATTERN.findall(text.lower()) if tok not in _STOPWORDS]


def raw_idf(doc_freq: int, doc_count: int) -> float:
    """BM25's raw (pre-floor) inverse document frequency for a term with the
    given document frequency across a corpus of doc_count documents.
    Negative when the term appears in more than half the corpus -- i.e. it
    carries no discriminative signal in this corpus. rank_bm25's BM25Okapi
    floors this to a small positive epsilon internally; callers that need
    the true (possibly negative) value -- to zero out near-universal terms,
    or to weight a bag-of-words vector -- use this instead.
    """
    return math.log(doc_count - doc_freq + 0.5) - math.log(doc_freq + 0.5)
