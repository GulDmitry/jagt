package dev.jagt.orchestrator.task;

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

    /** A resumed task inherits a title the pattern already prefixed, and `ship` expands it every round. */
    @Test
    void expandsThePatternIdempotentlyHoweverOftenShipRuns() {
        String once = ReviewRequestTitle.expand("{ticket} {title}", "ABC-42", "Widget layout is off");
        String twice = ReviewRequestTitle.expand("{ticket} {title}", "ABC-42", once);

        assertThat(once).isEqualTo("ABC-42 Widget layout is off");
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void keepsANullTitleNullRatherThanInventingOne() {
        assertThat(ReviewRequestTitle.stripTicketPrefix(null, "ABC-42")).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "'{ticket}: {title}', ABC-42",
            "'{ticket} {title}',  ABC-42",
            "'{ticket} — {title}', ABC-42",
            "'{title} — {ticket}', ABC-42"
    })
    void namesARequestAfterItsTicketAloneWhenTheTaskNeverGotATitle(String pattern, String ticket) {
        assertThat(ReviewRequestTitle.expand(pattern, ticket, null)).isEqualTo("ABC-42");
    }

    @Test
    void neverEditsATitleThatEndsInAPatternSeparator() {
        assertThat(ReviewRequestTitle.expand("{ticket}: {title}", "ABC-42", "Rework endpoint /users/"))
                .isEqualTo("ABC-42: Rework endpoint /users/");
    }
}
