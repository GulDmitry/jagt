package dev.jagt.orchestrator.command;

import static dev.jagt.orchestrator.command.TestFinishedTaskBuilder.finishedTask;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.agent.SubscriptionUsage;
import dev.jagt.orchestrator.service.FinishedTasks;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.UsageLedger;
import dev.jagt.orchestrator.service.UsageLimits;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.usage.AgentSpend;
import dev.jagt.orchestrator.task.usage.AssistantCallKind;
import dev.jagt.orchestrator.task.usage.CostPerAcceptedTask;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UsageStatsRendererTest {

  private static final long NOW = 1_000_000_000L;

  @TempDir Path root;
  private final FinishedTasks finished = mock(FinishedTasks.class);
  private StateService state;
  private UsageTracker tracker;
  private UsageStatsRenderer renderer;

  @BeforeEach
  void rendererReadsTheTrackerAndNoLimitsWereRead() {
    state = stateService().withRoot(root).build();
    tracker = new UsageTracker(state, mock(UsageLedger.class));
    renderer =
        new UsageStatsRenderer(
            tracker,
            new UsageLimits(Optional.empty()),
            finished,
            Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
  }

  @Test
  void statsEndsWithTheLastSevenDaysCostPerAcceptedTaskAsLowerBoundWhereSpendIsUnknown() {
    var shipped =
        finishedTask()
            .withFinishedAt(NOW)
            .withSpend(new FinishedTask.Spend(0, 4.0, null, null))
            .build();
    var unread =
        finishedTask().withId("ABC-2").withFinishedAt(NOW - 1).withStatus(TaskStatus.NEW).build();
    when(finished.all()).thenReturn(List.of(shipped, unread));

    String out = renderer.render(state.tasks());

    assertThat(out)
        .contains("cost per accepted task, last 7 days: ≥ 4.00 USD (1 shipped of 2 finished)\n");
  }

  @Test
  void saysUnknownWhereNothingShippedInTheLastSevenDays() {
    assertThat(UsageStatsRenderer.costPerAccepted(new CostPerAcceptedTask(0, 3, 2.0, false)))
        .isEqualTo("cost per accepted task, last 7 days: unknown (0 shipped of 3 finished)\n");
  }

  @Test
  void doesNotMarkLowerBoundWhereEverySpendIsKnown() {
    assertThat(UsageStatsRenderer.costPerAccepted(new CostPerAcceptedTask(2, 3, 9.0, false)))
        .isEqualTo("cost per accepted task, last 7 days: 4.50 USD (2 shipped of 3 finished)\n");
  }

  @Test
  void showsWhichKindOfCallTheSpendWentOnBiggestFirst() {
    tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(25_000, 0, 170, 0.05));
    tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(900_000, 0, 5_000, 1.80));
    String out = renderer.render(state.tasks());

    assertThat(out).contains("BY CALL");
    assertThat(out.indexOf("review sweep")).isLessThan(out.indexOf("ticket read"));
    assertThat(out).doesNotContain("merge-request read");
  }

  @Test
  void ranksTheTasksByWhatTheyConsumed() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
    state.putTask(
        "ABC-2", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a2").build());
    tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(25_000, 0, 100, 0.05));
    tracker.chargeTask("ABC-1", TokenUsage.ofCall(25_000, 0, 100, 0.05));
    tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(900_000, 0, 5_000, 1.80));
    tracker.chargeTask("ABC-2", TokenUsage.ofCall(900_000, 0, 5_000, 1.80));
    String out = renderer.render(state.tasks());

    assertThat(out.indexOf("ABC-2")).isLessThan(out.indexOf("ABC-1"));
    assertThat(out).contains("average per call");
  }

  @Test
  void keepsRetiredTasksSpendInTheSessionTotal() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
    tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(500_000, 0, 1_000, 1.0));
    tracker.chargeTask("ABC-1", TokenUsage.ofCall(500_000, 0, 1_000, 1.0));
    state.removeTask("ABC-1");
    String out = renderer.render(state.tasks());

    assertThat(out).contains("(nothing spent on the current tasks)");
    assertThat(out.lines().filter(l -> l.startsWith("this session")).findFirst().orElseThrow())
        .contains("501k");
  }

  @Test
  void showsNothingSpentBeforeTheFirstCall() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).alias("a1").build());
    String out = renderer.render(state.tasks());

    assertThat(out).contains("(nothing spent on the current tasks)");
    assertThat(out).doesNotContain("average per call");
  }

  @Test
  void showsEachWindowWithItsUseAndResetAndThePaidSpend() {
    var snapshot =
        new SubscriptionUsage.Snapshot(
            List.of(
                new SubscriptionUsage.Window(
                    "5h", 82, Optional.of(Instant.parse("2026-10-10T16:00:00Z")))),
            Optional.of(
                new SubscriptionUsage.Paid(
                    Optional.of(new BigDecimal("12.5")),
                    Optional.of(new BigDecimal("500")),
                    "USD",
                    Optional.empty())));

    String out =
        UsageStatsRenderer.limits(
            Optional.of(
                new UsageLimits.Reading(
                    new SubscriptionUsage.Read(snapshot), Instant.parse("2026-10-10T14:02:00Z"))));

    assertThat(out)
        .contains("subscription limits, read Sat 14:02")
        .containsPattern("5h +82%  Sat 16:00")
        .containsPattern("paid usage +12.50 of 500.00 USD");
  }

  @Test
  void saysCouldNotReadAndWhyWhenTheReadFailed() {
    String out =
        UsageStatsRenderer.limits(
            Optional.of(
                new UsageLimits.Reading(
                    new SubscriptionUsage.Unreadable("http 500"),
                    Instant.parse("2026-10-10T14:02:00Z"))));

    assertThat(out).isEqualTo("subscription limits, read Sat 14:02: could not read, http 500\n\n");
  }

  @Test
  void showsNoLimitsSectionBeforeAnythingWasRead() {
    String out = UsageStatsRenderer.limits(Optional.empty());

    assertThat(out).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(AssistantCallKind.class)
  void keepsEveryCallKindLabelInsideItsColumn(AssistantCallKind kind) {
    assertThat(kind.label().length()).isLessThanOrEqualTo(UsageStatsRenderer.LABEL_W);
  }

  @Test
  void showsColdStartsPerKindAndQuestionMarkWhereNoCacheFieldWasRead() {
    tracker.record(
        AssistantCallKind.TICKET_READ, TokenUsage.ofCall(25_000, 0, 170, 0.05).asStart());
    tracker.record(
        AssistantCallKind.TICKET_READ, TokenUsage.ofCall(900, 24_000, 170, 0.02).asStart());
    tracker.record(
        AssistantCallKind.TICKET_READ, TokenUsage.ofCall(900, 24_000, 170, 0.02).asStart());
    tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(900_000, 0, 5_000, 1.80));
    String out = renderer.render(state.tasks());

    assertThat(out.lines().filter(l -> l.startsWith("ticket read")).findFirst().orElseThrow())
        .endsWith("1/3");
    assertThat(out.lines().filter(l -> l.startsWith("review sweep")).findFirst().orElseThrow())
        .endsWith("?");
  }

  @Test
  void listsTheAgentSessionsBiggestFirstAndTotalsThem() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .build()
            .withAgentSpend(new AgentSpend(TokenUsage.ofCall(25_000, 0, 100, 0.05), Map.of())));
    state.putTask(
        "ABC-2",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a2")
            .build()
            .withAgentSpend(new AgentSpend(TokenUsage.ofCall(900_000, 0, 5_000, 1.80), Map.of())));
    state.putTask(
        "ABC-3", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a3").build());
    String out = renderer.render(state.tasks());

    assertThat(out).contains("AGENT SESSION", "agents");
    assertThat(out.indexOf("ABC-2")).isLessThan(out.indexOf("ABC-1"));
    assertThat(out).doesNotContain("ABC-3");
  }

  @Test
  void showsNoAgentSessionsWhereNoAgentSpentAnything() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
    String out = renderer.render(state.tasks());

    assertThat(out).doesNotContain("AGENT SESSION");
  }

  @Test
  void averagesTheSessionsTokensOverItsCalls() {
    tracker.record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(4_000, 0, 1_000, 0.05));
    tracker.record(AssistantCallKind.REVIEW_SWEEP, TokenUsage.ofCall(4_000, 0, 1_000, 0.05));
    String out = renderer.render(state.tasks());

    assertThat(out).contains("average per call: 5.0k tokens");
  }

  @Test
  void takesTheMedianCostOfDeployedTasksAndLeavesOutOneWithAnUnreadSpend() {
    List<FinishedTask> finished =
        List.of(
            finishedTask()
                .withId("ABC-1")
                .withSpend(new FinishedTask.Spend(0, 1.0, null, null))
                .build(),
            finishedTask()
                .withId("ABC-2")
                .withSpend(new FinishedTask.Spend(0, 4.0, null, null))
                .build(),
            finishedTask().withId("ABC-3").build(),
            finishedTask()
                .withId("ABC-4")
                .withSpend(new FinishedTask.Spend(0, 90.0, null, null))
                .withStatus(TaskStatus.CI_POLLING)
                .build());

    assertThat(UsageStatsRenderer.costPerDeployed(finished))
        .isEqualTo(
            "median cost per deployed task: 2.50 USD (2 of 3 deployed with a known spend)\n");
  }

  @Test
  void saysUnknownWhenNoDeployedTaskHasKnownSpend() {
    List<FinishedTask> finished = List.of(finishedTask().build());

    assertThat(UsageStatsRenderer.costPerDeployed(finished))
        .isEqualTo(
            "median cost per deployed task: unknown (1 deployed, none with a known spend)\n");
  }

  @Test
  void marksTheMedianCostAsLowerBoundWhenDeployedTaskHadAnUnpricedCall() {
    List<FinishedTask> finished =
        List.of(
            finishedTask()
                .withSpend(
                    new FinishedTask.Spend(
                        0,
                        2.0,
                        null,
                        TokenUsage.ofCall(10, 0, 10, 2.0)
                            .plus(TokenUsage.ofUnpricedCall(10, 0, 10))))
                .build());

    assertThat(UsageStatsRenderer.costPerDeployed(finished))
        .isEqualTo(
            "median cost per deployed task: ≥ 2.00 USD (1 of 1 deployed with a known spend)\n");
  }

  @Test
  void marksTasksUsdAsLowerBoundWhenItHadAnUnpricedCall() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).alias("a1").build());
    tracker.chargeTask(
        "ABC-1",
        TokenUsage.ofCall(25_000, 0, 100, 0.05).plus(TokenUsage.ofUnpricedCall(10, 0, 10)));

    String out = renderer.render(state.tasks());

    assertThat(out.lines().filter(l -> l.startsWith("ABC-1")).findFirst().orElseThrow())
        .contains("≥0.05");
  }
}
