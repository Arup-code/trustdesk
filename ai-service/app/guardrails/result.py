# ai-service/app/guardrails/result.py
from dataclasses import dataclass


@dataclass
class GuardrailResult:
    flagged: bool
    category: str | None = None
    reason: str | None = None
