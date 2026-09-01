package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UsageTrackerTest {

    private static StateService stateIn(Path root) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
    }

    @Test
    void splitsTheSessionSpendByWhatEachCallWasFor(@TempDir Path root) {
        UsageTracker tracker = new UsageTracker(stateIn(root));

        tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(25_000, 0, 170, 0.05));
        tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(30_000, 0, 200, 0.06));
        tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(31_000, 0, 210, 0.06));

        var byKind = tracker.sessionByKind();
        assertThat(byKind.get(AssistantCallKind.REVIEW_SWEEP).calls()).isEqualTo(2);
        assertThat(byKind.get(AssistantCallKind.TICKET_READ).calls()).isEqualTo(1);
        assertThat(byKind).doesNotContainKey(AssistantCallKind.MR_READ);
        assertThat(tracker.session().calls()).isEqualTo(3);
        assertThat(tracker.session().inputTokens()).isEqualTo(86_000);
    }

    @Test
    void chargesTheCallToTheTaskThatTriggeredIt(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        UsageTracker tracker = new UsageTracker(state);

        tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(25_000, 0, 170, 0.05));
        tracker.chargeTask("ABC-1", TokenUsage.ofCall(25_000, 0, 170, 0.05));

        assertThat(state.task("ABC-1").orElseThrow().usageOrNone().inputTokens()).isEqualTo(25_000);
        assertThat(tracker.session().calls()).isEqualTo(1);
    }

    @Test
    void accumulatesEveryPollOnTheSameTaskInsteadOfOverwritingTheLastOne(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        UsageTracker tracker = new UsageTracker(state);

        tracker.chargeTask("ABC-1", TokenUsage.ofCall(25_000, 0, 100, 0.05));
        tracker.chargeTask("ABC-1", TokenUsage.ofCall(26_000, 500, 120, 0.06));

        TokenUsage usage = state.task("ABC-1").orElseThrow().usageOrNone();
        assertThat(usage.calls()).isEqualTo(2);
        assertThat(usage.inputTokens()).isEqualTo(51_000);
        assertThat(usage.cachedInputTokens()).isEqualTo(500);
        assertThat(usage.outputTokens()).isEqualTo(220);
        assertThat(usage.costUsd()).isCloseTo(0.11, within(1e-9));
    }

    @Test
    void chargesAnAliasToTheTaskItStandsFor(@TempDir Path root) {
        StateService state = stateIn(root);
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
        UsageTracker tracker = new UsageTracker(state);

        tracker.chargeTask("a1", TokenUsage.ofCall(1_000, 0, 10, 0.01));

        assertThat(state.task("ABC-1").orElseThrow().usageOrNone().calls()).isEqualTo(1);
    }

    @Test
    void keepsTheSessionTotalForACallThatBelongsToNoTask(@TempDir Path root) {
        StateService state = stateIn(root);
        UsageTracker tracker = new UsageTracker(state);

        tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(24_000, 0, 150, 0.05));
        tracker.chargeTask(null, TokenUsage.ofCall(24_000, 0, 150, 0.05));

        assertThat(tracker.session().inputTokens()).isEqualTo(24_000);
        assertThat(state.tasks()).isEmpty();
    }

    @Test
    void ignoresAnEmptyMeasurementSoUnmeteredCallsDoNotInflateTheCallCount(@TempDir Path root) {
        UsageTracker tracker = new UsageTracker(stateIn(root));

        tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.NONE);

        assertThat(tracker.session()).isEqualTo(TokenUsage.NONE);
    }
}
