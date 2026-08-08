package app.dexcode.trustdesk.enums;

import java.util.Locale;

public enum TicketCategory {
    refund,
    shipping,
    warranty,
    billing,
    account_security,
    general;

    public static TicketCategory fromValue(String value) {
        return value == null ? null : TicketCategory.valueOf(value.toLowerCase(Locale.ROOT));
    }
}
