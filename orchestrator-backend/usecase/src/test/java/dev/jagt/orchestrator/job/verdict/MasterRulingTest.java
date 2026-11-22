package dev.jagt.orchestrator.job.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class MasterRulingTest {

  private final MasterVerdicts verdicts = mock(MasterVerdicts.class);
  private final MasterPlanVerdicts plans = mock(MasterPlanVerdicts.class);
  private final MasterRuling ruling = new MasterRuling(verdicts, plans);
  private final ArbiterConfig config = ArbiterConfig.defaults();

  @Test
  void theReadyVerdictOnPendingPlanIsLeftToThePlanVerdicts() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).build();
    MasterReview.Verdict ready =
        new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, "");
    when(plans.holds("ABC-1", task, ready, config)).thenReturn(true);

    boolean moved = ruling.act("ABC-1", task, ready, config);

    assertThat(moved).isTrue();
    verify(verdicts, never()).act("ABC-1", task, ready, config);
  }

  @Test
  void theVerdictThatIsNotReadyIsTheRoundsEvenWherePlanWaits() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).build();
    MasterReview.Verdict notReady =
        new MasterReview.Verdict(
            MasterReview.Kind.NOT_READY, List.of(), List.of(), List.of(), 1, "");
    when(verdicts.act("ABC-1", task, notReady, config)).thenReturn(true);

    boolean moved = ruling.act("ABC-1", task, notReady, config);

    assertThat(moved).isTrue();
    verify(plans, never()).holds("ABC-1", task, notReady, config);
  }
}
