package app.dexcode.trustdesk.enums;

import java.util.Locale;

public enum TicketPriority {
    low,
    medium,
    high,
    urgent;

    public static TicketPriority fromValue(String value) {
        return value == null ? null : TicketPriority.valueOf(value.toLowerCase(Locale.ROOT));
    }
}
