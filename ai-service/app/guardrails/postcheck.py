# ai-service/app/guardrails/postcheck.py
import re

from app.guardrails.result import GuardrailResult

_SECRET_LIKE = re.compile(r"sk-[a-zA-Z0-9]{10,}")
_LEAK_PHRASES = [
    re.compile(r"system\s+prompt", re.IGNORECASE),
    re.compile(r"my\s+instructions\s+are", re.IGNORECASE),
]
_DISALLOWED_TOOL_MENTIONS = [
    re.compile(r"issue_coupon", re.IGNORECASE),
]


def postcheck(draft_body: str) -> GuardrailResult:
    if _SECRET_LIKE.search(draft_body):
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
