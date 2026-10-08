package dev.jagt.orchestrator.protocol;

import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStatusMessageTest {

    @Test
    void refusesAnOutcomeThatIsNotOneOfTheThreeInsteadOfReadingTheMessageInstead() {
        var said = AgentStatusMessage.of("IN_PROGRESS", "still going").withOutcome("done");

        assertThat(said.violations(MessageContext.NONE)).extracting(Violation::field).containsExactly("outcome");
    }

    @Test
    void refusesAStatusThisMachineDoesNotHave() {
        var said = AgentStatusMessage.of("ALMOST_DONE", "still going");

        assertThat(said.violations(MessageContext.NONE)).extracting(Violation::field).containsExactly("status");
    }

    @Test
    void refusesARoundThatIsOutForReviewWithNoRequestAnywhereInIt() {
        var said = AgentStatusMessage.of("CI_POLLING", "shipped it");

        assertThat(said.violations(MessageContext.NONE)).extracting(Violation::expected)
                .allSatisfy(expected -> assertThat(expected).contains("required with CI_POLLING"));
    }

    @Test
    void acceptsARoundWhoseRequestWasWrittenIntoTheMessage() {
        var said = AgentStatusMessage.of("CI_POLLING", "review request: https://host/mr/9");

        assertThat(said.violations(MessageContext.NONE)).isEmpty();
    }

    @Test
    void refusesBothLinkFormsAtOnceBecauseNeitherWinsWithoutGuessing() {
        var said = AgentStatusMessage.of("CI_POLLING", "shipped").withReviewRequestUrl("https://host/mr/9")
                .withReviewRequests(Map.of("api", "https://host/api/mr/1"));

        assertThat(said.violations(new MessageContext(List.of("api")))).extracting(Violation::field)
                .containsExactly("reviewRequests");
    }

    @Test
    void refusesALinkNobodyCanOpen() {
        var said = AgentStatusMessage.of("CI_POLLING", "shipped").withReviewRequestUrl("git@host:proj.git");

        assertThat(said.violations(MessageContext.NONE)).extracting(Violation::field)
                .contains("reviewRequestUrl");
    }

    @Test
    void refusesARequestFiledUnderAProjectTheTaskDoesNotHave() {
        var said = AgentStatusMessage.of("CI_POLLING", "shipped")
                .withReviewRequests(Map.of("nope", "https://host/mr/9"));

        assertThat(said.violations(new MessageContext(List.of("api", "web")))).extracting(Violation::expected)
                .allSatisfy(expected -> assertThat(expected).contains("not on this task"));
    }

    @Test
    void namesEveryBrokenRuleAtOnceSoASenderFixesThemInOneGo() {
        var said = new AgentStatusMessage("ALMOST_DONE", "shipped", "done", "git@host:proj.git", Map.of());

        assertThat(said.violations(MessageContext.NONE)).hasSize(3);
    }

    @Test
    void readsTheSessionsOwnRepositoryAsTheLinkAHumanFollowsFirst() {
        var said = AgentStatusMessage.of("CI_POLLING", "shipped")
                .withReviewRequests(Map.of("api", "https://host/api/mr/1", "web", "https://host/web/mr/2"));

        assertThat(said.accepted(new MessageContext(List.of("web", "api"))).orElseThrow().link())
                .isEqualTo("https://host/web/mr/2");
    }

    @Test
    void fallsBackToTheMarkerInTheMessageWhenNoOutcomeFieldWasSent() {
        var said = AgentStatusMessage.of("REVIEW_PENDING", "outcome=question: which branch?");

        assertThat(said.accepted(MessageContext.NONE).orElseThrow().claimed()).isEqualTo(AgentReport.QUESTION);
    }

    @Test
    void handsBackTheStatusAndTheHumansHalfOfTheMessageSeparately() {
        var said = AgentStatusMessage.of("REVIEW_PENDING", "outcome=no_changes: all handled").withOutcome("no_changes");

        Reported accepted = said.accepted(MessageContext.NONE).orElseThrow();
        assertThat(accepted.status()).isEqualTo(TaskStatus.REVIEW_PENDING);
        assertThat(accepted.detail()).isEqualTo("all handled");
    }
}
