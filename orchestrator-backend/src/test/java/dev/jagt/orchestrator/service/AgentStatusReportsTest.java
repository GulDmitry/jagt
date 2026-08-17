package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.UserNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * What an agent reports about itself. The rules here are about NOT lying to the human: a linkless CI_POLLING,
 * an essay where a dashboard line goes, or a second ping for a status they already saw.
 */
class AgentStatusReportsTest {

    private final UserNotifier notifier = mock(UserNotifier.class);

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    private AgentStatusReports reports(StateService state) {
        return new AgentStatusReports(state, notifier);
    }

    @Test
    void storesTheMrLinkFromTheStatusMessageForTheDashboard(@TempDir Path root) {
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

        verify(notifier).notify(org.mockito.ArgumentMatchers.contains("ABC-1"), anyString());
    }

    @Test
    void doesNotNotifyOnRoutineInProgressKeepAlive(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());

        reports(state).report("IN_PROGRESS", "step 2", "ABC-1");

        verifyNoInteractions(notifier);
    }

    @Test
    void rejectsCiPollingStatusWhenMessageCarriesNoMrLink(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        assertThatThrownBy(() -> reports(state).report("CI_POLLING", "branch pushed", "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MR link");
    }

    @Test
    void acceptsCiPollingStatusWhenMessageCarriesTheMrLink(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        reports(state).report("CI_POLLING", "MR: https://gitlab.example/g/p/-/merge_requests/1", "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.CI_POLLING);
    }

    /**
     * Carrying an MR link is not a licence to go back to polling it: a task the human has already taken past
     * review must not be dropped into CI_POLLING by a confused agent, which would re-arm the auto-review poll
     * against a request nobody is waiting on any more.
     */
    @Test
    void refusesToPushATaskThatIsPastReviewBackIntoCiPolling(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED).alias("a1")
                .mrUrl("https://gitlab.example/g/p/-/merge_requests/1").build());

        assertThatThrownBy(() -> reports(state).report("CI_POLLING", "waiting for the pipeline", "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MR link");
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
    void keepsTheWholeMrLinkWhenTheMessageIsTooLongForOneDashboardLine(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        String link = "https://gitlab.example/group/subgroup/team/project/-/merge_requests/1234567";

        reports(state).report("CI_POLLING", "pipeline queued after the push — MR: " + link, "ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().mrUrl()).isEqualTo(link);
    }

    @Test
    void rejectsCiPollingWhenTheMessageMentionsHttpButCarriesNoLink(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());

        assertThatThrownBy(() -> reports(state)
                .report("CI_POLLING", "pushed, see the http docs for the request", "ABC-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MR link");
    }

    @Test
    void markApprovedAdvancesTheStatusAndPingsTheHumanOnce(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").mrUrl("http://mr/1").build());

        reports(state).markApproved("ABC-1");

        assertThat(state.task("ABC-1").orElseThrow().status()).isEqualTo(TaskStatus.APPROVED);
        verify(notifier).notify(eq("jagt · ABC-1"), contains("approved"));
    }

    @Test
    void markApprovedDoesNotRePingWhenAlreadyApproved(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.APPROVED)
                .alias("a1").mrUrl("http://mr/1").build());

        reports(state).markApproved("ABC-1");

        verify(notifier, never()).notify(eq("jagt · ABC-1"), contains("approved"));
    }

    @Test
    void stampsTheWindowStartWhenTheMrIsFirstLinked(@TempDir Path root) {
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
}
