package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValuesTest {

    @Test
    void reportsAFailedReadWithItsSentenceAndWhatItCost() {
        TokenUsage spent = TokenUsage.ofCall(10, 0, 5, 0.01);

        Answer<String> answer = Answer.failed(spent, "timed out");

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.usage()).isEqualTo(spent);
        assertThat(answer.failure()).isEqualTo("timed out");
    }

    @Test
    void costsNothingForAReadThatNeverHappened() {
        Answer<String> answer = Answer.unavailable();

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.usage()).isEqualTo(TokenUsage.NONE);
        assertThat(answer.failure()).isEmpty();
    }

    @Test
    void shapesTheFactsWhileCarryingTheCostAndTheFailureThrough() {
        TokenUsage spent = TokenUsage.ofCall(10, 0, 5, 0.01);

        Answer<Integer> shaped = new Answer<>(Optional.of("abc"), spent, "note").map(String::length);

        assertThat(shaped.facts()).contains(3);
        assertThat(shaped.usage()).isEqualTo(spent);
        assertThat(shaped.failure()).isEqualTo("note");
    }

    @Test
    void routesAnAgentNotificationOnItsTopicWithTheTaskItIsAbout() {
        Notification told = Notification.fromAgent("ABC-1", "question", "which branch?");

        assertThat(told).isEqualTo(new Notification(Notification.Topic.AGENT, Notification.Event.YOUR_MOVE, "ABC-1", null, "question", "which branch?"));
    }

    @Test
    void routesTheOtherNotificationsOnTheirOwnTopicsAndNamesATaskOnlyForTheWatchdog() {
        assertThat(Notification.watchdog("ABC-1", "quiet", "no report").topic()).isEqualTo(Notification.Topic.WATCHDOG);
        assertThat(Notification.watchdog("ABC-1", "quiet", "no report").taskId()).isEqualTo("ABC-1");
        assertThat(Notification.housekeeping("tidy", "x").topic()).isEqualTo(Notification.Topic.HOUSEKEEPING);
        assertThat(Notification.install("act", "x").topic()).isEqualTo(Notification.Topic.INSTALL);
        assertThat(Notification.usage("window", "x").topic()).isEqualTo(Notification.Topic.USAGE);
        assertThat(Notification.install("act", "x").taskId()).isNull();
    }

    @Test
    void raisesAnOpenedRequestAndALandedDeployAsTheirOwnEventsOfATask() {
        assertThat(Notification.requestOpened("ABC-1", "!7")).isEqualTo(
                new Notification(Notification.Topic.AGENT, Notification.Event.REQUEST_OPENED, "ABC-1", null, "request opened", "!7"));
        assertThat(Notification.deployed("ABC-1", "deployed")).isEqualTo(
                new Notification(Notification.Topic.AGENT, Notification.Event.DEPLOYED, "ABC-1", null, "deployed", "deployed"));
    }

    @Test
    void namesAnEventByTheIdAConfigurationListsIt() {
        assertThat(Notification.Event.REQUEST_OPENED.id()).isEqualTo("request-opened");
        assertThat(Notification.Event.ofId(Notification.Event.DEPLOYED.id())).contains(Notification.Event.DEPLOYED);
    }

    @Test
    void wantsEveryNotificationUnlessAChannelSaysOtherwise() {
        Notifier everything = new Notifier() {
            @Override
            public String id() {
                return "all";
            }

            @Override
            public void deliver(Notification notification) {
            }
        };

        assertThat(everything.takes(Notification.usage("window", "x"))).isTrue();
    }

    @Test
    void offersASandboxOnlyWhereAWritableSetWasGiven() {
        Sandbox on = Sandbox.on(List.of(Path.of("/tmp/wt")), List.of("git"));

        assertThat(on.enabled()).isTrue();
        assertThat(on.writable()).containsExactly(Path.of("/tmp/wt"));
        assertThat(on.excludedCommands()).containsExactly("git");
        assertThat(Sandbox.off().enabled()).isFalse();
    }

    @Test
    void provisionsAWorktreeWithNoHintsUntilItIsGivenSome() {
        AgentWorktree bare = AgentWorktree.of(Path.of("/wt"), Path.of("/root"));

        AgentWorktree hinted = bare.withAllowedTools(List.of("Read")).withSandbox(Sandbox.on(List.of(), List.of()));
        assertThat(bare.allowedTools()).isEmpty();
        assertThat(bare.sandbox().enabled()).isFalse();
        assertThat(hinted.path()).isEqualTo(Path.of("/wt"));
        assertThat(hinted.orchestratorRoot()).isEqualTo(Path.of("/root"));
        assertThat(hinted.allowedTools()).containsExactly("Read");
        assertThat(hinted.sandbox().enabled()).isTrue();
    }

    @Test
    void readsMissingHintListsAsEmpty() {
        AgentWorktree worktree = new AgentWorktree(Path.of("/wt"), Path.of("/root"), null, Sandbox.off());
        assertThat(worktree.allowedTools()).isEmpty();
    }

    @Test
    void handsBackTheSameResultWhenTheCommandSucceeded() {
        Processes.Result result = new Processes.Result(0, "out", "");

        assertThat(result.expectSuccess("build")).isSameAs(result);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"2,partial,boom,build failed (exit 2): boom",
            "1,only out,'  ',build failed (exit 1): only out"})
    void namesTheActionAndWhatTheFailedCommandActuallySaid(int exit, String stdout, String stderr, String message) {
        assertThatThrownBy(() -> new Processes.Result(exit, stdout, stderr).expectSuccess("build"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(message);
    }

    @ParameterizedTest
    @CsvSource({"blocking,true", "wrong,true", "unproven,true", "unguarded,false", "noise,false"})
    void stopsARoundOnlyForTheFirstThreeSeverities(String severity, boolean stops) {
        assertThat(new RoundReviewer.Finding("A.java", "issue", "pattern", severity, "", "").stops()).isEqualTo(stops);
    }

    @Test
    void readsAFailedJudgementAsNothingButItsFailure() {
        RoundReviewer.Judgement failed = RoundReviewer.Judgement.failed("no answer");

        assertThat(failed.failure()).isEqualTo("no answer");
        assertThat(failed.findings()).isEmpty();
        assertThat(failed.premises()).isEmpty();
        assertThat(failed.verdict()).isEmpty();
    }

    @Test
    void spentNothingKeepsWhereTheNextReadStarts() {
        SessionLog.Spent nothing = SessionLog.Spent.nothing(120L);

        assertThat(nothing.usage()).isEqualTo(TokenUsage.NONE);
        assertThat(nothing.upTo()).isEqualTo(120L);
    }

    @Test
    void readsATicketWithNoCorrectionsUnlessTheCallerHadSome() {
        List<List<String>> asked = new java.util.ArrayList<>();
        TrackerAssistant tracker = new TrackerAssistant() {
            @Override
            public Answer<dev.jagt.orchestrator.task.TicketFacts> readTicket(String ticketRef,
                                                                             List<String> corrections) {
                asked.add(corrections);
                return Answer.unavailable();
            }

            @Override
            public Answer<String> readTicketText(String ticketRef) {
                return Answer.unavailable();
            }

            @Override
            public Answer<List<String>> findCandidates(String query) {
                return Answer.unavailable();
            }
        };

        tracker.readTicket("ABC-42");

        assertThat(asked).containsExactly(List.of());
    }
}
