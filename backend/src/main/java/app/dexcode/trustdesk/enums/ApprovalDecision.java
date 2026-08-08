package app.dexcode.trustdesk.enums;

import java.util.Locale;

public enum ApprovalDecision {
    approved,
    rejected,
    needs_changes;

    public static ApprovalDecision fromValue(String value) {
        return value == null ? null : ApprovalDecision.valueOf(value.toLowerCase(Locale.ROOT));
    }
}
