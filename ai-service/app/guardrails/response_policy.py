from dataclasses import dataclass


@dataclass(frozen=True)
class GuardrailResponsePolicy:
    category: str
    priority: str
    doc_id: str


# Static, pre-vetted response per guardrail category. Only used on the
# guardrail-flagged path in triage_graph.py / draft_graph.py, which by design
# never runs the (potentially adversarial) ticket text through the
# classification or generation LLM -- so these values must be fixed constants
# derived from the *category label itself*, not anything the model or the
# ticket text produces.
_POLICIES = {
    "identity_bypass": GuardrailResponsePolicy("account_security", "high", "KB-ACCOUNT-001"),
    "secret_disclosure": GuardrailResponsePolicy("account_security", "high", "KB-SECURITY-001"),
    # A coupon/discount social-engineering attempt is fraud/abuse, not an account
    # compromise -- it doesn't touch identity or secrets, so it's triaged as a
    # general escalation rather than account_security.
    "coupon_injection": GuardrailResponsePolicy("general", "medium", "KB-SECURITY-001"),
}

_DEFAULT_POLICY = GuardrailResponsePolicy("account_security", "high", "KB-SECURITY-001")


def guardrail_response_policy(guardrail_category: str | None) -> GuardrailResponsePolicy:
    """Look up the fixed category/priority/citation for a flagged guardrail category.

    Falls back to the account_security/high/KB-SECURITY-001 default for any
    guardrail category not in the table above, so a future pattern group added
    to app.guardrails.patterns without a matching policy entry still degrades
    to a safe, sensible response instead of a KeyError.
    """
    return _POLICIES.get(guardrail_category, _DEFAULT_POLICY)
