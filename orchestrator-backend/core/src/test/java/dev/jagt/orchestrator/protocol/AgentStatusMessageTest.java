package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStatusMessageTest {

    @Test
    void refusesAnOutcomeThatIsNotOneOfTheThreeInsteadOfReadingTheMessageInstead() {
        var said = new AgentStatusMessage("IN_PROGRESS", "still going", "done", null, Map.of());

        assertThat(said.violations(List.of())).extracting(Violation::field).containsExactly("outcome");
    }

    @Test
    void refusesAStatusThisMachineDoesNotHave() {
        var said = new AgentStatusMessage("ALMOST_DONE", "still going", null, null, Map.of());

        assertThat(said.violations(List.of())).extracting(Violation::field).containsExactly("status");
    }

    @Test
    void refusesARoundThatIsOutForReviewWithNoRequestAnywhereInIt() {
        var said = new AgentStatusMessage("CI_POLLING", "shipped it", null, null, Map.of());

        assertThat(said.violations(List.of())).extracting(Violation::expected)
                .allSatisfy(expected -> assertThat(expected).contains("required with CI_POLLING"));
    }

    @Test
    void acceptsARoundWhoseRequestWasWrittenIntoTheMessage() {
        var said = new AgentStatusMessage("CI_POLLING", "review request: https://host/mr/9", null, null,
                Map.of());

        assertThat(said.violations(List.of())).isEmpty();
    }

    @Test
    void refusesBothLinkFormsAtOnceBecauseNeitherWinsWithoutGuessing() {
        var said = new AgentStatusMessage("CI_POLLING", "shipped", null, "https://host/mr/9",
                Map.of("api", "https://host/api/mr/1"));

        assertThat(said.violations(List.of("api"))).extracting(Violation::field)
                .containsExactly("reviewRequests");
    }

    @Test
    void refusesALinkNobodyCanOpen() {
        var said = new AgentStatusMessage("CI_POLLING", "shipped", null, "git@host:proj.git", Map.of());

        assertThat(said.violations(List.of())).extracting(Violation::field)
                .contains("reviewRequestUrl");
    }

    @Test
    void refusesARequestFiledUnderAProjectTheTaskDoesNotHave() {
        var said = new AgentStatusMessage("CI_POLLING", "shipped", null, null,
                Map.of("nope", "https://host/mr/9"));

        assertThat(said.violations(List.of("api", "web"))).extracting(Violation::expected)
                .allSatisfy(expected -> assertThat(expected).contains("not on this task"));
    }

    @Test
    void namesEveryBrokenRuleAtOnceSoASenderFixesThemInOneGo() {
        var said = new AgentStatusMessage("ALMOST_DONE", "shipped", "done", "git@host:proj.git", Map.of());

        assertThat(said.violations(List.of())).hasSize(3);
    }

    @Test
    void readsTheSessionsOwnRepositoryAsTheLinkAHumanFollowsFirst() {
        var said = new AgentStatusMessage("CI_POLLING", "shipped", null, null,
                Map.of("api", "https://host/api/mr/1", "web", "https://host/web/mr/2"));

        assertThat(said.accepted(List.of("web", "api")).orElseThrow().link())
                .isEqualTo("https://host/web/mr/2");
    }

    @Test
    void fallsBackToTheMarkerInTheMessageWhenNoOutcomeFieldWasSent() {
        var said = new AgentStatusMessage("REVIEW_PENDING", "outcome=question: which branch?", null, null,
                Map.of());

        assertThat(said.accepted(List.of()).orElseThrow().claimed()).isEqualTo(AgentReport.QUESTION);
    }

    @Test
    void handsBackTheStatusAndTheHumansHalfOfTheMessageSeparately() {
        var said = new AgentStatusMessage("REVIEW_PENDING", "outcome=no_changes: all handled", "no_changes",
                null, Map.of());

        Reported accepted = said.accepted(List.of()).orElseThrow();
        assertThat(accepted.status()).isEqualTo(TaskStatus.REVIEW_PENDING);
        assertThat(accepted.detail()).isEqualTo("all handled");
    }
}
