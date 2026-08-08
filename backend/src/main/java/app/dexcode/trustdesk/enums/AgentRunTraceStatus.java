package app.dexcode.trustdesk.enums;

import java.util.Locale;

public enum AgentRunTraceStatus {
    completed,
    failed,
    running,
    pending;

    public static AgentRunTraceStatus fromValue(String value) {
        return value == null ? null : AgentRunTraceStatus.valueOf(value.toLowerCase(Locale.ROOT));
    }
}
