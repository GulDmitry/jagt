package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TokenUsage;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class UsageTrackerTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path root;

  private final UsageLedger ledger = mock(UsageLedger.class);
  private StateService state;
  private UsageTracker tracker;

  @BeforeEach
  void tracksUsageOverTheTempState() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer,
            event -> {});
    tracker = new UsageTracker(state, ledger);
  }

  @Test
  void splitsTheSessionSpendByWhatEachCallWasFor() {

    tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(25_000, 0, 170, 0.05));
    tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(30_000, 0, 200, 0.06));
    tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(31_000, 0, 210, 0.06));

    var byKind = tracker.sessionByKind();
    assertThat(Objects.requireNonNull(byKind.get(AssistantCallKind.REVIEW_SWEEP)).calls())
        .isEqualTo(2);
    assertThat(Objects.requireNonNull(byKind.get(AssistantCallKind.TICKET_READ)).calls())
        .isEqualTo(1);
    assertThat(byKind).doesNotContainKey(AssistantCallKind.MR_READ);
    assertThat(tracker.session().calls()).isEqualTo(3);
    assertThat(tracker.session().inputTokens()).isEqualTo(86_000);
  }

  @Test
  void booksEveryRecordedCallInTheLedger() {
    TokenUsage usage = TokenUsage.ofCall(25_000, 0, 170, 0.05);

    tracker.record(AssistantCallKind.MR_READ, usage);

    verify(ledger).add(AssistantCallKind.MR_READ, usage);
  }

  @Test
  void chargesTheCallToTheTaskThatTriggeredIt() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());

    tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(25_000, 0, 170, 0.05));
    tracker.chargeTask("ABC-1", TokenUsage.ofCall(25_000, 0, 170, 0.05));

    assertThat(state.task("ABC-1").orElseThrow().usageOrNone().inputTokens()).isEqualTo(25_000);
    assertThat(tracker.session().calls()).isEqualTo(1);
  }

  @Test
  void accumulatesEveryPollOnTheSameTaskInsteadOfOverwritingTheLastOne() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());

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
  void chargesAnAliasToTheTaskItStandsFor() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());

    tracker.chargeTask("a1", TokenUsage.ofCall(1_000, 0, 10, 0.01));

    assertThat(state.task("ABC-1").orElseThrow().usageOrNone().calls()).isEqualTo(1);
  }

  @Test
  void keepsTheSessionTotalForCallThatBelongsToNoTask() {

    tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(24_000, 0, 150, 0.05));
    tracker.chargeTask("", TokenUsage.ofCall(24_000, 0, 150, 0.05));

    assertThat(tracker.session().inputTokens()).isEqualTo(24_000);
    assertThat(state.tasks()).isEmpty();
  }

  @Test
  void ignoresAnEmptyMeasurementSoUnmeteredCallsDoNotInflateTheCallCount() {

    tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.NONE);

    assertThat(tracker.session()).isEqualTo(TokenUsage.NONE);
  }
}
