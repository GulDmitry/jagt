package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.AssistantCallKind;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.model.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardRendererTest {

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    @Test
    void showsTheTicketUrlAsADetailLineAboveTheMrLink(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").title("title").mrUrl("https://gitlab/x/-/merge_requests/9").ticketUrl("https://jira/browse/ABC-1").build());

        String out = new DashboardRenderer(new TaskViews(state), new UsageTracker(state)).render();

        assertThat(out).contains("└ https://jira/browse/ABC-1");
        assertThat(out.indexOf("https://jira/browse/ABC-1"))
                .isLessThan(out.indexOf("https://gitlab/x/-/merge_requests/9"));
    }

    @Test
    void ordersRowsByLastActiveDescendingUnderTheSortedActiveColumn(@TempDir Path root) {
        StateService state = stateIn(root);
        long older = 1_700_000_000_000L;
        long newer = older + 3_600_000;
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW)
                .lastActiveTimestamp(older).alias("a1").title("older task").build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.NEW)
                .lastActiveTimestamp(newer).alias("a2").title("newer task").build());

        String out = new DashboardRenderer(new TaskViews(state), new UsageTracker(state)).render();

        assertThat(out).contains("ACTIVE ▼");
        assertThat(out.indexOf("ABC-2")).isLessThan(out.indexOf("ABC-1"));
        assertThat(out).contains(DashboardRenderer.stamp(older), DashboardRenderer.stamp(newer));
    }

    @Test
    void showsWhatJagtHasSpentOnATaskAndForTheWholeSession(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                .alias("a1").title("title").build());
        UsageTracker tracker = new UsageTracker(state);
        tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(63_500, 0, 500, 0.12));
        tracker.chargeTask("ABC-1", TokenUsage.ofCall(63_500, 0, 500, 0.12));

        String out = new DashboardRenderer(new TaskViews(state), tracker).render();

        assertThat(out).contains("TOKENS");
        assertThat(out).contains("64k");                      // the task's own column
        assertThat(out).contains("spend 1 call / 64k tok");
    }

    @Test
    void leavesTheTokenColumnEmptyForATaskThatHasCostNothing(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        String out = new DashboardRenderer(new TaskViews(state), new UsageTracker(state)).render();

        assertThat(out).doesNotContain("spend");
        assertThat(out.lines().filter(l -> l.startsWith("a1")).findFirst().orElseThrow())
                .contains(" - ");
    }

    @Test
    void omitsTheTicketLineWhenNoUrlWasRead(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
                .alias("a1").title("title").build());

        assertThat(new DashboardRenderer(new TaskViews(state), new UsageTracker(state)).render()).doesNotContain("└");
    }
}
