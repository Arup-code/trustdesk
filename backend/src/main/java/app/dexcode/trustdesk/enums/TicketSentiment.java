package app.dexcode.trustdesk.enums;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum TicketSentiment {
    neutral,
    frustrated,
    angry,
    negative,
    positive,
    confused,
    concerned,
    satisfied;

    private static final Logger log = LoggerFactory.getLogger(TicketSentiment.class);

    public static TicketSentiment fromValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return TicketSentiment.valueOf(value.toLowerCase(Locale.ROOT).trim());
        } catch (IllegalArgumentException e) {
            // Unlike category/priority, the AI service's sentiment classification is
            // free-text ("one word") rather than constrained to an explicit enum in the
            // prompt, so the model can and does return words outside this list (it has
            // already sent "concerned" and "negative" before either was added here).
            // Falling back to neutral instead of throwing keeps triage from failing
            // outright over a cosmetic field no downstream logic branches on.
            log.warn("Unrecognized ticket sentiment value \"{}\"; defaulting to neutral", value);
            return neutral;
        }
    }
}
