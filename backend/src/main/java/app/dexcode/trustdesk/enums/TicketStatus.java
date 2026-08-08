package app.dexcode.trustdesk.enums;

import java.util.Locale;

public enum TicketStatus {
    open;

    public static TicketStatus fromValue(String value) {
        return value == null ? null : TicketStatus.valueOf(value.toLowerCase(Locale.ROOT));
    }
}
