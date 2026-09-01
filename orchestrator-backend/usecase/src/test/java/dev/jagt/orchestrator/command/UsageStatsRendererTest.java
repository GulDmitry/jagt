package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UsageStatsRendererTest {

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    @Test
    void showsWhichKindOfCallTheSpendWentOnBiggestFirst(@TempDir Path root) {
        StateService state = stateIn(root);
        UsageTracker tracker = new UsageTracker(state);
        tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(25_000, 0, 170, 0.05));
        tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(900_000, 0, 5_000, 1.80));

        String out = new UsageStatsRenderer(tracker).render(state.tasks());

        assertThat(out).contains("BY CALL");
        assertThat(out.indexOf("review sweep")).isLessThan(out.indexOf("ticket read"));
        assertThat(out).doesNotContain("merge-request read");
    }

    @Test
    void ranksTheTasksByWhatTheyConsumed(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a2").build());
        UsageTracker tracker = new UsageTracker(state);
        tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(25_000, 0, 100, 0.05));
        tracker.chargeTask("ABC-1", TokenUsage.ofCall(25_000, 0, 100, 0.05));
        tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(900_000, 0, 5_000, 1.80));
        tracker.chargeTask("ABC-2", TokenUsage.ofCall(900_000, 0, 5_000, 1.80));

        String out = new UsageStatsRenderer(tracker).render(state.tasks());

        assertThat(out.indexOf("ABC-2")).isLessThan(out.indexOf("ABC-1"));
        assertThat(out).contains("average per call");
    }

    @Test
    void keepsARetiredTasksSpendInTheSessionTotal(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        UsageTracker tracker = new UsageTracker(state);
        tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(500_000, 0, 1_000, 1.0));
        tracker.chargeTask("ABC-1", TokenUsage.ofCall(500_000, 0, 1_000, 1.0));
        state.removeTask("ABC-1");

        String out = new UsageStatsRenderer(tracker).render(state.tasks());

        assertThat(out).contains("(nothing spent on the current tasks)");
        assertThat(out.lines().filter(l -> l.startsWith("this session")).findFirst().orElseThrow())
                .contains("501k");
    }

    @Test
    void showsNothingSpentBeforeTheFirstCall(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        String out = new UsageStatsRenderer(new UsageTracker(state)).render(state.tasks());

        assertThat(out).contains("(nothing spent on the current tasks)");
        assertThat(out).doesNotContain("average per call");
    }

    @ParameterizedTest
    @EnumSource(AssistantCallKind.class)
    void keepsEveryCallKindLabelInsideItsColumn(AssistantCallKind kind) {
        assertThat(kind.label().length()).isLessThanOrEqualTo(UsageStatsRenderer.LABEL_W);
    }
}
