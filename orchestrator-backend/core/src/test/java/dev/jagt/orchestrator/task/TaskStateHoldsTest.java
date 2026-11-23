package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.stop.RefusalKind;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TaskStateHoldsTest {

  @Test
  void countsEveryRefusalPerKindAcrossRefusals() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

    TaskState refused =
        task.refused(RefusalKind.STATUS_REPORT)
            .refused(RefusalKind.STATUS_REPORT)
            .refused(RefusalKind.GIT_STATE);

    assertThat(refused.gate().refusals())
        .containsEntry(RefusalKind.STATUS_REPORT, 2)
        .containsEntry(RefusalKind.GIT_STATE, 1);
  }

  @Test
  void addsMeteredUsageToWhatWasMeteredBefore() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withUsageAdded(TokenUsage.ofCall(10, 0, 5, 0))
            .withUsageAdded(TokenUsage.ofCall(20, 0, 5, 0));

    assertThat(task.gate().usageOrNone().calls()).isEqualTo(2);
    assertThat(task.gate().usageOrNone().inputTokens()).isEqualTo(30);
  }

  @Test
  void readsFileWrittenBeforeThePlanGateExistedAsNothingToWaitFor() {
    TaskState legacy =
        new JsonMapper()
            .readValue(
                """
                {"project":"demo","worktreePath":"/wt","status":"NEW","lastActiveTimestamp":5,
                 "mrCreatedAt":0,"lastPolledAt":0}\
                """,
                TaskState.class);

    assertThat(legacy.gate().planGate()).isEqualTo(PlanGate.OPEN);
  }

  @Test
  void keepsTheBaseBranchAndThePlanGateItWasGiven() {
    TaskState task = TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).build();

    TaskState given = task.withBaseBranch("release").withPlanGate(PlanGate.REQUIRED);

    assertThat(given.ticket().baseBranchOr("main")).isEqualTo("release");
    assertThat(given.gate().planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @Test
  void keepsTheSessionsSwitchJudgementsThroughStateJsonAndLetsTheLastWordStand() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .judged(Map.of("visualize", "no — config-only change", "autoplan", "no — one file"))
            .judged(Map.of("visualize", "yes — a screen changed"));
    JsonMapper mapper = new JsonMapper();

    TaskState reread = mapper.readValue(mapper.writeValueAsString(task), TaskState.class);

    assertThat(reread.gate().switches())
        .containsOnly(
            Map.entry("visualize", "yes — a screen changed"),
            Map.entry("autoplan", "no — one file"));
  }
}
