package dev.jagt.orchestrator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRequestTitleTest {

    @ParameterizedTest
    @CsvSource({
            "ABC-42 Widget layout is off, ABC-42, Widget layout is off",
            "ABC-42: tidy imports,        ABC-42, tidy imports",
            "Widget layout is off,        ABC-42, Widget layout is off",
            "ABC-42,                      ABC-42, ''"
    })
    void stripsALeadingTicketSoTheTitleNeverDoublesIt(String stored, String ticket, String expected) {
        assertThat(ReviewRequestTitle.stripTicketPrefix(stored, ticket)).isEqualTo(expected);
    }

    @Test
    void expandsThePatternIdempotentlyHoweverOftenShipRuns() {
        // A resumed task inherits a title the pattern already prefixed; expanding it again must not produce
        // "ABC-42 ABC-42 …".
        String once = ReviewRequestTitle.expand("{ticket} {title}", "ABC-42", "Widget layout is off");
        String twice = ReviewRequestTitle.expand("{ticket} {title}", "ABC-42", once);

        assertThat(once).isEqualTo("ABC-42 Widget layout is off");
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void keepsANullTitleNullRatherThanInventingOne() {
        assertThat(ReviewRequestTitle.stripTicketPrefix(null, "ABC-42")).isNull();
    }
}
