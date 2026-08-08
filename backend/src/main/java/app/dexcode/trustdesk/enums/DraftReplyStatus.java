package app.dexcode.trustdesk.enums;

import java.util.Locale;

public enum DraftReplyStatus {
    generated,
    edited,
    approved,
    rejected,
    sent,
    escalated;

    public static DraftReplyStatus fromValue(String value) {
        return value == null ? null : DraftReplyStatus.valueOf(value.toLowerCase(Locale.ROOT));
    }
}
