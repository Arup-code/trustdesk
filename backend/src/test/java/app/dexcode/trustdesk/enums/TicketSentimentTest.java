package app.dexcode.trustdesk.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TicketSentimentTest {

    @Test
    void fromValueParsesKnownValueCaseInsensitively() {
        assertEquals(TicketSentiment.frustrated, TicketSentiment.fromValue("FRUSTRATED"));
        assertEquals(TicketSentiment.negative, TicketSentiment.fromValue("negative"));
    }

    @Test
    void fromValueReturnsNullForNull() {
        assertNull(TicketSentiment.fromValue(null));
    }

    @Test
    void fromValueDefaultsToNeutralForUnrecognizedWord() {
        // Regression test: the AI service's sentiment classification is free-text
        // ("one word") rather than a closed set, so it can return words this enum
        // doesn't define (it already has, twice: "concerned" and "negative", before
        // either was added as a constant). An unrecognized value must not throw and
        // fail the whole triage request -- it should degrade to a safe default.
        assertEquals(TicketSentiment.neutral, TicketSentiment.fromValue("bewildered"));
    }
}
