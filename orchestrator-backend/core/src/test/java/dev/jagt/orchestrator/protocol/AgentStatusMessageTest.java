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
    void refusesAStatusOnlyJagtSetsNamingOnlyTheReportableOnes() {
        var said = AgentStatusMessage.of("DEPLOYED", "landed");

        assertThat(said.violations(MessageContext.NONE)).singleElement()
                .satisfies(violation -> assertThat(violation.expected())
                        .isEqualTo("one of [PLAN_PENDING, IN_PROGRESS, REVIEW_PENDING, SHIPPING, CI_POLLING, CI_FAILED]"));
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

    @Test
    void refusesEveryRequestUnderAProjectTheTaskDoesNotHaveByNamingIt() {
        var said = AgentStatusMessage.of("CI_POLLING", "shipped")
                .withReviewRequests(Map.of("nope", "https://host/mr/9"));

        assertThat(said.violations(new MessageContext(List.of("api", "web")))).singleElement()
                .satisfies(violation -> assertThat(violation.field()).isEqualTo("reviewRequests"))
                .satisfies(violation -> assertThat(violation.expected())
                        .isEqualTo("project 'nope' is not on this task; its projects are [api, web]"));
    }

    @Test
    void acceptsARequestFiledUnderAProjectOfTheTask() {
        var said = AgentStatusMessage.of("CI_POLLING", "shipped")
                .withReviewRequests(Map.of("web", "https://host/mr/9"));

        assertThat(said.violations(new MessageContext(List.of("api", "web")))).isEmpty();
    }

    @Test
    void leavesTheProjectsAloneWhenTheTaskIsNotKnown() {
        var said = AgentStatusMessage.of("CI_POLLING", "shipped")
                .withReviewRequests(Map.of("nope", "https://host/mr/9"));

        assertThat(said.violations(MessageContext.NONE)).isEmpty();
    }

    @Test
    void readsTheLinkFromTheFieldTheMapAndTheMessageInThatOrder() {
        var field = AgentStatusMessage.of("CI_POLLING", "see https://host/other").withReviewRequestUrl("  https://host/mr/1 ");
        var message = AgentStatusMessage.of("CI_POLLING", "request https://host/mr/2 opened");
        var neither = AgentStatusMessage.of("REVIEW_PENDING", "all handled");

        assertThat(field.accepted(MessageContext.NONE).orElseThrow().link()).isEqualTo("https://host/mr/1");
        assertThat(message.accepted(MessageContext.NONE).orElseThrow().link()).isEqualTo("https://host/mr/2");
        assertThat(neither.accepted(MessageContext.NONE).orElseThrow().link()).isNull();
    }

    @Test
    void readsTheOnlyRequestAsTheLinkWhenTheSessionsOwnRepositoryHasNone() {
        var said = AgentStatusMessage.of("CI_POLLING", "shipped")
                .withReviewRequests(Map.of("web", "https://host/web/mr/2"));

        assertThat(said.accepted(new MessageContext(List.of("api", "web"))).orElseThrow().link())
                .isEqualTo("https://host/web/mr/2");
    }

    @Test
    void readsTheOnlyRequestAsTheLinkWhenTheTaskIsNotKnown() {
        var said = AgentStatusMessage.of("CI_POLLING", "shipped")
                .withReviewRequests(Map.of("web", "https://host/web/mr/2"));

        assertThat(said.accepted(MessageContext.NONE).orElseThrow().link()).isEqualTo("https://host/web/mr/2");
    }

    @Test
    void keepsEveryRequestTheReportNamedForTheRoundToRecord() {
        var said = AgentStatusMessage.of("CI_POLLING", "shipped")
                .withReviewRequests(Map.of("api", "https://host/api/mr/1", "web", "https://host/web/mr/2"));

        assertThat(said.accepted(new MessageContext(List.of("api", "web"))).orElseThrow().requests())
                .containsOnlyKeys("api", "web");
    }

    @Test
    void takesWhatTheOutcomeFieldClaimsOverWhatTheMessageOpensWith() {
        var question = AgentStatusMessage.of("REVIEW_PENDING", "which one?").withOutcome(" Question ");
        var none = AgentStatusMessage.of("REVIEW_PENDING", "all handled").withOutcome("no-changes");
        var progress = AgentStatusMessage.of("REVIEW_PENDING", "outcome=question: x").withOutcome("progress");

        assertThat(question.accepted(MessageContext.NONE).orElseThrow().claimed()).isEqualTo(AgentReport.QUESTION);
        assertThat(none.accepted(MessageContext.NONE).orElseThrow().claimed()).isEqualTo(AgentReport.NO_CHANGES);
        assertThat(progress.accepted(MessageContext.NONE).orElseThrow().claimed()).isEqualTo(AgentReport.PLAIN);
    }

    @Test
    void acceptsNothingThatBrokeARule() {
        assertThat(AgentStatusMessage.of("ALMOST_DONE", "x").accepted(MessageContext.NONE)).isEmpty();
    }
}
