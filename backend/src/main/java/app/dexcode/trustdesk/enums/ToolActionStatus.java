package app.dexcode.trustdesk.enums;

import java.util.Locale;

public enum ToolActionStatus {
    requested,
    approval_required,
    approved,
    rejected,
    executed,
    failed,
    cancelled;

    public static ToolActionStatus fromValue(String value) {
        return value == null ? null : ToolActionStatus.valueOf(value.toLowerCase(Locale.ROOT));
    }
}
