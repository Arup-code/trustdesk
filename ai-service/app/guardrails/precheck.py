# ai-service/app/guardrails/precheck.py
from app.guardrails.patterns import PATTERN_GROUPS
from app.guardrails.result import GuardrailResult


def precheck(text: str) -> GuardrailResult:
    for category, patterns in PATTERN_GROUPS.items():
        for pattern in patterns:
            if pattern.search(text):
                return GuardrailResult(
                    flagged=True, category=category, reason=f"matched pattern: {pattern.pattern}")
    return GuardrailResult(flagged=False)
