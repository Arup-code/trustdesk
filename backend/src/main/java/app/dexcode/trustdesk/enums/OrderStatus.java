package app.dexcode.trustdesk.enums;

import java.util.Locale;

public enum OrderStatus {
    delivered,
    in_transit;

    public static OrderStatus fromValue(String value) {
        return value == null ? null : OrderStatus.valueOf(value.toLowerCase(Locale.ROOT));
    }
}
