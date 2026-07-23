# ai-service/app/guardrails/patterns.py
import re

IDENTITY_BYPASS_PATTERNS = [
    re.compile(r"ignore\s+identity", re.IGNORECASE),
    re.compile(r"skip\s+(the\s+)?(identity|verification)", re.IGNORECASE),
    re.compile(r"don'?t\s+verify", re.IGNORECASE),
    re.compile(r"without\s+verifying", re.IGNORECASE),
    re.compile(r"bypass\s+(identity|verification|security)\s*check", re.IGNORECASE),
]

COUPON_INJECTION_PATTERNS = [
    re.compile(r"issue\s+(a\s+|me\s+a\s+)?(large\s+|big\s+|\d+\s*(inr|rs|rupees)?\s*)?coupon", re.IGNORECASE),
    re.compile(r"hide\s+(this|it)?\s*from\s+(the\s+)?(human|reviewer|manager)", re.IGNORECASE),
    re.compile(r"do\s+not\s+(mention|tell)\s+.*(human|reviewer)", re.IGNORECASE),
    re.compile(r"ignore\s+(all\s+|previous\s+)?instructions", re.IGNORECASE),
    re.compile(r"ignore\s+(all\s+)?(previous\s+)?(the\s+)?polic(y|ies)", re.IGNORECASE),
    re.compile(r"system\s+override", re.IGNORECASE),
    re.compile(r"you\s+are\s+now\s+allowed", re.IGNORECASE),
]

SECRET_DISCLOSURE_PATTERNS = [
    re.compile(r"system\s+prompt", re.IGNORECASE),
    re.compile(r"api\s*key", re.IGNORECASE),
    re.compile(r"reveal\s+.*(prompt|instructions)", re.IGNORECASE),
    re.compile(r"internal\s+notes?", re.IGNORECASE),
    re.compile(r"hidden\s+(prompt|instructions)", re.IGNORECASE),
    re.compile(r"what\s+are\s+your\s+instructions", re.IGNORECASE),
]

PATTERN_GROUPS = {
    "identity_bypass": IDENTITY_BYPASS_PATTERNS,
    "coupon_injection": COUPON_INJECTION_PATTERNS,
    "secret_disclosure": SECRET_DISCLOSURE_PATTERNS,
}
