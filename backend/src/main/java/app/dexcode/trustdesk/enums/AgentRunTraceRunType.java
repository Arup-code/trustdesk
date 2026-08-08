package app.dexcode.trustdesk.enums;

import java.util.Locale;

public enum AgentRunTraceRunType {
    triage,
    draft_reply,
    tool_recommendation,
    eval_case;

    public static AgentRunTraceRunType fromValue(String value) {
        return value == null ? null : AgentRunTraceRunType.valueOf(value.toLowerCase(Locale.ROOT));
    }
}
