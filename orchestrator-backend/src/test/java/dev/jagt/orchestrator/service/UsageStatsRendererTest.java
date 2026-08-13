package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.model.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UsageStatsRendererTest {

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(new OrchestratorProperties(
                root.toString(), null, root.resolve("state.json").toString(),
                null, null, null, null, null, null, null, false, null)));
    }

    @Test
    void ranksTheTasksByWhatTheyConsumed(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        state.putTask("ABC-2", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a2").build());
        UsageTracker tracker = new UsageTracker(state);
        tracker.record(TokenUsage.ofCall(25_000, 0, 100, 0.05));
        tracker.chargeTask("ABC-1", TokenUsage.ofCall(25_000, 0, 100, 0.05));
        tracker.record(TokenUsage.ofCall(900_000, 0, 5_000, 1.80));
        tracker.chargeTask("ABC-2", TokenUsage.ofCall(900_000, 0, 5_000, 1.80));

        String out = new UsageStatsRenderer(state, tracker).render();

        assertThat(out.indexOf("ABC-2")).isLessThan(out.indexOf("ABC-1"));
        assertThat(out).contains("average per call");
    }

    @Test
    void keepsARetiredTasksSpendInTheSessionTotal(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        UsageTracker tracker = new UsageTracker(state);
        tracker.record(TokenUsage.ofCall(500_000, 0, 1_000, 1.0));
        tracker.chargeTask("ABC-1", TokenUsage.ofCall(500_000, 0, 1_000, 1.0));
        state.removeTask("ABC-1");

        String out = new UsageStatsRenderer(state, tracker).render();

        assertThat(out).contains("(nothing spent on the current tasks)");
        assertThat(out.lines().filter(l -> l.startsWith("this session")).findFirst().orElseThrow())
                .contains("501k");
    }

    @Test
    void showsNothingSpentBeforeTheFirstCall(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());

        String out = new UsageStatsRenderer(state, new UsageTracker(state)).render();

        assertThat(out).contains("(nothing spent on the current tasks)");
        assertThat(out).doesNotContain("average per call");
    }
}
