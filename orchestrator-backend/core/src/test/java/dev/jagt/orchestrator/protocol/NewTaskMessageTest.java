package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewTaskMessageTest {

    @Test
    void refusesALaunchWithNothingToNameTheBranchOrTheRepositoryBy() {
        var said = new NewTaskMessage(null, null, List.of(), null, null, null, null, null, null);

        assertThat(said.violations(MessageContext.NONE)).extracting(Violation::field)
                .containsExactlyInAnyOrder("taskId", "projectKey");
    }

    @Test
    void refusesABranchStrategyThisMachineDoesNotHave() {
        var said = new NewTaskMessage("ABC-42", "api", List.of(), null, null, null, null, "clobber", null);

        assertThat(said.violations(MessageContext.NONE)).extracting(Violation::field)
                .containsExactly("branchStrategy");
    }

    @Test
    void refusesATicketLinkNobodyCanOpen() {
        var said = new NewTaskMessage("ABC-42", "api", List.of(), null, null, "jira://ABC-42", null, null, null);

        assertThat(said.violations(MessageContext.NONE)).extracting(Violation::field)
                .containsExactly("ticketUrl");
    }

    @Test
    void refusesTheSessionsOwnRepositoryRepeatedAmongTheFurtherOnes() {
        var said = new NewTaskMessage("ABC-42", "api", List.of("api", "web"), null, null, null, null, null,
                null);

        assertThat(said.violations(MessageContext.NONE)).extracting(Violation::field)
                .containsExactly("alsoProjects");
    }

    @Test
    void acceptsTheOrdinaryLaunchWithNothingButATaskAndAProject() {
        var said = new NewTaskMessage("ABC-42", "api", List.of(), null, null, null, null, null, null);

        assertThat(said.violations(MessageContext.NONE)).isEmpty();
    }
}
