package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.model.AssistantCallKind;
import dev.jagt.orchestrator.model.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

        // "42 calls, 1.8M tokens" does not say where to optimise; this split does, and the answer is the top
        // line — the poll that repeats, not the read that happens once.
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
    /**
     * A label longer than its column shifts every number on that row — the table's whole job. Asserted over
     * the ENUM rather than over one rendered row, so adding a call kind cannot break the layout unnoticed.
     */
    @Test
    void keepsEveryCallKindLabelInsideItsColumn() {
        for (dev.jagt.orchestrator.model.AssistantCallKind kind
                : dev.jagt.orchestrator.model.AssistantCallKind.values()) {
            assertThat(kind.label().length()).as(kind.name() + " label")
                    .isLessThanOrEqualTo(UsageStatsRenderer.LABEL_W);
        }
    }
}
