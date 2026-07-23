# ai-service/app/guardrails/postcheck.py
import re

from app.guardrails.result import GuardrailResult

_SECRET_LIKE_PATTERNS = [
    re.compile(r"sk-[a-zA-Z0-9]{10,}"),
    re.compile(r"AKIA[0-9A-Z]{12,}"),
    re.compile(r"ghp_[a-zA-Z0-9]{20,}"),
]
_LEAK_PHRASES = [
    re.compile(r"system\s+prompt", re.IGNORECASE),
    re.compile(r"my\s+instructions\s+are", re.IGNORECASE),
    re.compile(r"api\s*key", re.IGNORECASE),
    re.compile(r"internal\s+notes?", re.IGNORECASE),
    re.compile(r"hidden\s+(prompt|instructions)", re.IGNORECASE),
]
_DISALLOWED_TOOL_MENTIONS = [
    re.compile(r"issue_coupon", re.IGNORECASE),
]


def postcheck(draft_body: str) -> GuardrailResult:
    for pattern in _SECRET_LIKE_PATTERNS:
        if pattern.search(draft_body):
            return GuardrailResult(flagged=True, category="output_leak", reason="possible secret leaked in draft")
    for pattern in _LEAK_PHRASES:
        if pattern.search(draft_body):
            return GuardrailResult(
                flagged=True, category="output_leak", reason="draft appears to disclose internal instructions")
    for pattern in _DISALLOWED_TOOL_MENTIONS:
        if pattern.search(draft_body):
            return GuardrailResult(
                flagged=True, category="output_leak", reason="draft references a disallowed tool action")
    return GuardrailResult(flagged=False)
