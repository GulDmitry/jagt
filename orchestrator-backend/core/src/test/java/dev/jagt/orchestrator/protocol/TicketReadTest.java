package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.task.TicketFacts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketReadTest {

    @Test
    void acceptsAnItemTheTrackerSaysIsNotThere() {
        var facts = new TicketFacts(false, "", "", "", List.of(), "");

        assertThat(TicketRead.violations("ABC-42", facts)).isEmpty();
    }

    @Test
    void refusesAnItemThatExistsWithNothingToNameItBy() {
        var facts = new TicketFacts(true, "", "", "ABC", List.of(), "");

        assertThat(TicketRead.violations("ABC-42", facts)).extracting(Violation::field)
                .containsExactlyInAnyOrder("key", "title", "url");
    }

    @Test
    void refusesAnAnswerAboutAnotherItemThanTheOneAskedFor() {
        var facts = new TicketFacts(true, "ABC-7", "Something else", "ABC", List.of(),
                "https://tracker/ABC-7");

        assertThat(TicketRead.violations("ABC-42", facts)).extracting(Violation::expected)
                .allSatisfy(expected -> assertThat(expected).contains("which was ABC-42"));
    }

    @Test
    void asksNothingAboutTheKeyWhenTheReferenceWasAUrl() {
        var facts = new TicketFacts(true, "ABC-7", "Something", "ABC", List.of(), "https://tracker/ABC-7");

        assertThat(TicketRead.violations("https://tracker/ABC-7", facts)).isEmpty();
    }

    @Test
    void refusesALinkNobodyCanOpen() {
        var facts = new TicketFacts(true, "ABC-42", "Widget", "ABC", List.of(), "tracker://ABC-42");

        assertThat(TicketRead.violations("ABC-42", facts)).extracting(Violation::field).containsExactly("url");
    }
}
