package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.notify.Notifications;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * What an agent reports about itself. The rules here are about NOT lying to the human: a linkless CI_POLLING,
 * an essay where a dashboard line goes, or a second ping for a status they already saw.
 */
class AgentStatusReportsTest {

    private final Notifications notifications = mock(Notifications.class);

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    private AgentStatusReports reports(StateService state) {
        return new AgentStatusReports(state, notifications, new FlowReports(state));
    }

    @Test
    void storesTheRequestLinkTheAgentPutInItsStatusMessage(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        reports(state).report("CI_POLLING", "MR: https://gitlab/x/-/merge_requests/9", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrUrl()).isEqualTo("https://gitlab/x/-/merge_requests/9");
    }

    @Test
    void notifiesHumanWhenAgentFinishesAndHandsBackForReview(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());

        reports(state).report("REVIEW_PENDING", "done", "ABC-1");

        verify(notifications).send(argThat(sent -> "ABC-1".equals(sent.taskId())));
    }

    @Test
    void doesNotNotifyOnRoutineInProgressKeepAlive(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());

        reports(state).report("IN_PROGRESS", "step 2", "ABC-1");

        verifyNoInteractions(notifications);
    }

    @Test
    void notifiesHumanWhenAgentStopsToAskWithoutLeavingTheStatusItWasWorkingIn(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("step 2").build());

        reports(state).report("IN_PROGRESS", "awaiting: which uniqueness rule", "ABC-1");

        verify(notifications).send(argThat(sent -> "needs input".equals(sent.title())));
    }

    @Test
    void doesNotNotifyAgainWhileTheAgentRepeatsTheQuestionItIsStillWaitingOn(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("awaiting: which uniqueness rule").build());

        reports(state).report("IN_PROGRESS", "awaiting: which uniqueness rule", "ABC-1");

        verifyNoInteractions(notifications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"branch pushed", "pushed, see the http docs for the request"})
    void refusesToSayATaskIsWaitingOnChecksWithoutNamingTheRequest(String message, @TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        assertThatThrownBy(() -> reports(state).report("CI_POLLING", message, "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request link");
    }

    @Test
    void letsATaskSayItIsWaitingOnTheChecksOnceItNamesTheRequest(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        reports(state).report("CI_POLLING", "MR: https://gitlab.example/g/p/-/merge_requests/1", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.CI_POLLING);
    }

    /** A request already stored on the task is no substitute: the LINK has to be in the message. */
    @Test
    void refusesTheStatusEvenForATaskThatAlreadyCarriesARequestLink(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a1")
                .mrUrl("https://gitlab.example/g/p/-/merge_requests/1").build());

        assertThatThrownBy(() -> reports(state).report("CI_POLLING", "waiting for the pipeline", "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request link");
    }

    @Test
    void truncatesStatusMessageToOneDashboardLineWhenAgentSendsAnEssay(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());

        reports(state).report("IN_PROGRESS", "root cause\nanalysis ".repeat(20), "ABC-1");

        String stored = state.task("ABC-1").orElseThrow().message();
        assertThat(stored).hasSizeLessThanOrEqualTo(100).doesNotContain("\n").endsWith("...");
    }

    @Test
    void keepsTheWholeRequestLinkWhenTheMessageIsTooLongForOneDashboardLine(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        String link = "https://gitlab.example/group/subgroup/team/project/-/merge_requests/1234567";

        reports(state).report("CI_POLLING", "pipeline queued after the push — MR: " + link, "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrUrl()).isEqualTo(link);
    }

    @Test
    void advancesToApprovedAndTapsTheHumanTheFirstTime(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").mrUrl("http://mr/1").build());

        reports(state).markApproved("ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.APPROVED);
        verify(notifications).send(argThat(sent -> "ABC-1".equals(sent.taskId())
                && sent.body().contains("approved")));
    }

    @Test
    void staysQuietAboutAnApprovalTheHumanHasAlreadySeen(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                .alias("a1").mrUrl("http://mr/1").build());

        reports(state).markApproved("ABC-1");

        verify(notifications, never()).send(any());
    }

    @Test
    void stampsThePollingWindowWhenARequestIsFirstLinked(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());

        reports(state).report("CI_POLLING", "MR: http://mr/1", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrCreatedAt()).isPositive();
    }

    @Test
    void neverResetsTheWindowStartOnLaterRounds(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_FAILED)
                .alias("a1").mrUrl("http://mr/1").mrCreatedAt(12345L).build());

        reports(state).report("CI_POLLING", "MR: http://mr/1", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrCreatedAt()).isEqualTo(12345L);
    }

    @Test
    void tellsTheHumanAboutTheDraftedRepliesWaitingInTheWorktree(@TempDir Path root) throws IOException {
        StateService state = stateIn(root);
        Files.createDirectories(root.resolve("wt"));
        Files.writeString(root.resolve("wt/review_replies.md"), "to thread 1: done\n");
        state.putTask("ABC-1", TaskState.builder("proj", root.resolve("wt").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").build());

        reports(state).report("REVIEW_PENDING", "widget fixed", "ABC-1");

        verify(notifications).send(argThat(sent -> "ABC-1".equals(sent.taskId())
                && sent.body().contains("review_replies.md")));
    }

    @Test
    void doesNotRepeatTheDraftedRepliesWhenTheRoundChangedNothingAndTheAdviceAlreadySaysIt(@TempDir Path root)
            throws IOException {
        StateService state = stateIn(root);
        Files.createDirectories(root.resolve("wt"));
        Files.writeString(root.resolve("wt/review_replies.md"), "to thread 1: already handled\n");
        state.putTask("ABC-1", TaskState.builder("proj", root.resolve("wt").toString(),
                TaskStatus.IN_PROGRESS).alias("a1").build());

        reports(state).report("REVIEW_PENDING", "no changes: every comment already handled", "ABC-1");

        ArgumentCaptor<Notification> ping = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).send(ping.capture());
        assertThat(ping.getValue().body()).containsOnlyOnce("drafted replies");
    }

    /**
     * The guard the door owes: an agent whose message happens to carry a request link must not be able to say
     * CI_POLLING about a task the review has already passed — that takes it backwards and re-arms the poll.
     */
    @Test
    void refusesToPullATaskTheReviewHasPassedBackIntoWaitingOnChecks(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.APPROVED)
                .alias("a1").mrUrl("https://host/mr/1").build());
        AgentStatusReports reports = new AgentStatusReports(state, notifications, new FlowReports(state));

        assertThatThrownBy(() -> reports.report("CI_POLLING", "review request: https://host/mr/1", "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already APPROVED");
        assertThat(state.task("ABC-1")).get().extracting(TaskState::status).isEqualTo(TaskStatus.APPROVED);
    }
}
