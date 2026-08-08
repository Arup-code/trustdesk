# ai-service/app/guardrails/patterns.py
import re

IDENTITY_BYPASS_PATTERNS = [
    re.compile(r"ignore\s+identity", re.IGNORECASE),
    re.compile(r"disregard\s+(identity|verification)", re.IGNORECASE),
    re.compile(r"skip\s+(the\s+)?(identity|verification)", re.IGNORECASE),
    re.compile(r"don'?t\s+verify", re.IGNORECASE),
    re.compile(r"no\s+need\s+to\s+verify", re.IGNORECASE),
    re.compile(r"without\s+verifying", re.IGNORECASE),
    re.compile(r"bypass\s+(identity|verification|security)\s*check", re.IGNORECASE),
]

COUPON_INJECTION_PATTERNS = [
    re.compile(
        r"(issue|grant|give)\s+(a\s+|me\s+a\s+)?(large\s+|big\s+|\d+\s*(inr|rs|rupees)?\s*)?"
        r"(coupon|voucher|discount(\s+code)?|store\s+credit)",
        re.IGNORECASE,
    ),
    re.compile(r"hide\s+(this|it)?\s*from\s+(the\s+)?(human|reviewer|manager)", re.IGNORECASE),
    re.compile(r"(do\s+not|don'?t)\s+(mention|tell)\s+.*(human|reviewer|agent|anyone)", re.IGNORECASE),
    re.compile(r"(just\s+)?between\s+(us|you\s+and\s+me)", re.IGNORECASE),
    re.compile(r"system\s+override", re.IGNORECASE),
    re.compile(
        r"(you\s+are\s+now|you'?re)\s+(allowed|authorized|permitted)\s+to\s+(grant|issue|give|create)",
        re.IGNORECASE,
    ),
]

SECRET_DISCLOSURE_PATTERNS = [
    re.compile(r"system\s+prompt", re.IGNORECASE),
    re.compile(r"(reveal|show|print|share|give\s+me|tell\s+me)\s+.{0,30}(api\s*key)", re.IGNORECASE),
    re.compile(r"reveal\s+.*(prompt|instructions)", re.IGNORECASE),
    re.compile(r"(reveal|show|print|share|give\s+me|tell\s+me)\s+.{0,30}internal\s+notes?", re.IGNORECASE),
    re.compile(r"hidden\s+(prompt|instructions)", re.IGNORECASE),
    re.compile(r"what\s+(is|are)\s+your\s+(system\s+)?(prompt|instructions)", re.IGNORECASE),
    re.compile(r"(tell|show|reveal|print|share|what'?s)\s+.{0,20}your\s+(prompt|instructions)", re.IGNORECASE),
]

PATTERN_GROUPS = {
    "identity_bypass": IDENTITY_BYPASS_PATTERNS,
    "coupon_injection": COUPON_INJECTION_PATTERNS,
    "secret_disclosure": SECRET_DISCLOSURE_PATTERNS,
}
