package dev.jagt.orchestrator.flow.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.master.MasterMode;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.Turn.Holder;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StopHoldTest {

  private static final Arbiter.Profile ACT = new Arbiter.Profile(MasterMode.ACT, Set.of());

  @ParameterizedTest
  @EnumSource(
      value = TaskStop.Trigger.class,
      names = {"STALLED", "SHIPS", "ROUNDS", "PACE", "UNCHANGED_FAILURE"})
  void stopForSuspectedLoopIsTheMastersTurnInAct(TaskStop.Trigger trigger) {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(trigger, 5, "evidence"))
            .build();

    Holder holder =
        Turns.next(
            task.standing().status(),
            task.standing().report(),
            StopHold.of(task, ACT),
            Map.of(),
            ACT);

    assertThat(holder).isEqualTo(Holder.ARBITER);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStop.Trigger.class,
      names = {
        "MISS",
        "ACCEPTANCE_FAILED",
        "PAUSE",
        "CHECKS",
        "INFRA",
        "UNDELIVERED",
        "WAIT",
        "DEPLOY_UNREAD"
      })
  void stopTheHumanSetOrReadEndsIsNeverTheMastersTurn(TaskStop.Trigger trigger) {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(trigger, 5, "evidence"))
            .build();

    assertThat(StopHold.of(task, ACT)).isEqualTo(StopHold.HUMAN);
  }

  @Test
  void stopTheHumanKeptTheLiftOfIsTheirsInAct() {
    Arbiter.Profile kept = new Arbiter.Profile(MasterMode.ACT, Set.of("lift"));
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.ROUNDS, 5, "evidence"))
            .build();

    assertThat(StopHold.of(task, kept)).isEqualTo(StopHold.HUMAN);
  }

  @Test
  void stopIsTheHumansInJudge() {
    Arbiter.Profile judge = new Arbiter.Profile(MasterMode.JUDGE, Set.of());
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.ROUNDS, 5, "evidence"))
            .build();

    assertThat(StopHold.of(task, judge)).isEqualTo(StopHold.HUMAN);
  }

  @Test
  void stopTheMasterWasAskedAboutIsTheHumansOnceAsked() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.ROUNDS, 5, "evidence"))
            .build();
    TaskState asked =
        task.withMaster(
            Master.defaults().withAskedStop(Objects.requireNonNull(task.work().stop()).key()));

    assertThat(StopHold.of(asked, ACT)).isEqualTo(StopHold.HUMAN);
  }

  @Test
  void stopIsTheHumansOnceTheMasterLiftedOneSinceTheirLastLift() {
    TaskState spent =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.ROUNDS, 5, "evidence"))
            .build()
            .withMaster(Master.defaults().withLiftedStops(1));

    assertThat(StopHold.of(spent, ACT)).isEqualTo(StopHold.HUMAN);
  }
}
