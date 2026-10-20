package dev.jagt.orchestrator.adapter.tracker;

import dev.jagt.orchestrator.task.TicketFacts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoTrackerWorkflowTest {

    private final NoTrackerWorkflow workflow = new NoTrackerWorkflow();
    private final TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42")
            .withTrackerStatus("In Progress").withAssignee("jdoe");

    @Test
    void asksForNoItemsBecauseNothingArrivesUnasked() {
        assertThat(workflow.candidateQuery()).isEmpty();
    }

    @Test
    void startsNoWorkOnAnyItem() {
        assertThat(workflow.startsWork(item)).isFalse();
    }

    @Test
    void closesNoWorkOnAnyItem() {
        assertThat(workflow.closesWork(item)).isFalse();
    }

    @Test
    void namesItselfNoneSoTheInstallCanSelectIt() {
        assertThat(workflow.id()).isEqualTo("none");
    }
}
