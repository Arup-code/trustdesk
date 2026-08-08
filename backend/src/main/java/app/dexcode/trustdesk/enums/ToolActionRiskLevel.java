package app.dexcode.trustdesk.enums;

import java.util.Locale;

public enum ToolActionRiskLevel {
    low,
    medium,
    high;

    public static ToolActionRiskLevel fromValue(String value) {
        return value == null ? null : ToolActionRiskLevel.valueOf(value.toLowerCase(Locale.ROOT));
    }
}
