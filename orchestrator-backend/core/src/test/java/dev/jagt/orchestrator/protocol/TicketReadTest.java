package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.task.TicketFacts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketReadTest {

    @Test
    void acceptsAnItemTheTrackerSaysIsNotThere() {
        var facts = TicketFacts.defaults();

        assertThat(TicketRead.violations("ABC-42", facts)).isEmpty();
    }

    @Test
    void refusesAnItemThatExistsWithNothingToNameItBy() {
        var facts = TicketFacts.defaults().withExists(true).withTrackerStatus("In Progress");

        assertThat(TicketRead.violations("ABC-42", facts)).extracting(Violation::field)
                .containsExactlyInAnyOrder("key", "title", "url");
    }

    @Test
    void refusesAnItemWhoseWorkflowStatusCameBackEmpty() {
        var facts = TicketFacts.defaults().withExists(true).withKey("ABC-42").withTitle("Widget")
                .withUrl("https://tracker/ABC-42");

        assertThat(TicketRead.violations("ABC-42", facts)).extracting(Violation::field)
                .containsExactly("trackerStatus");
    }

    @Test
    void refusesAnAnswerAboutAnotherItemThanTheOneAskedFor() {
        var facts = TicketFacts.defaults().withExists(true).withKey("ABC-7").withTitle("Something else")
                .withUrl("https://tracker/ABC-7").withTrackerStatus("In Progress");

        assertThat(TicketRead.violations("ABC-42", facts)).extracting(Violation::expected)
                .allSatisfy(expected -> assertThat(expected).contains("which was ABC-42"));
    }

    @Test
    void asksNothingAboutTheKeyWhenTheReferenceWasAUrl() {
        var facts = TicketFacts.defaults().withExists(true).withKey("ABC-7").withTitle("Something")
                .withUrl("https://tracker/ABC-7").withTrackerStatus("In Progress");

        assertThat(TicketRead.violations("https://tracker/ABC-7", facts)).isEmpty();
    }

    @Test
    void refusesALinkNobodyCanOpen() {
        var facts = TicketFacts.defaults().withExists(true).withKey("ABC-42").withTitle("Widget")
                .withUrl("tracker://ABC-42").withTrackerStatus("In Progress");

        assertThat(TicketRead.violations("ABC-42", facts)).extracting(Violation::field).containsExactly("url");
    }
}
