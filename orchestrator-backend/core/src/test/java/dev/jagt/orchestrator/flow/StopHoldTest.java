package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Master;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn.Holder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StopHoldTest {

    private static final Arbiter.Profile ACT = new Arbiter.Profile(MasterMode.ACT, Set.of());

    private static TaskState stopped(TaskStop.Trigger trigger) {
        return TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).stop(new TaskStop(trigger, 5, "evidence")).build();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStop.Trigger.class, names = {"STALLED", "SHIPS", "ROUNDS", "PACE", "UNCHANGED_FAILURE"})
    void aStopForASuspectedLoopIsTheMastersTurnInAct(TaskStop.Trigger trigger) {
        TaskState task = stopped(trigger);

        Holder holder = Turns.next(task.status(), task.report(), StopHold.of(task, ACT), Map.of(), ACT);

        assertThat(holder).isEqualTo(Holder.ARBITER);
    }

    @ParameterizedTest
    @EnumSource(value = TaskStop.Trigger.class, names = {"MISS", "QA_FAILED", "PAUSE", "CHECKS", "INFRA", "UNDELIVERED", "WAIT", "DEPLOY_UNREAD"})
    void aStopTheHumanSetOrAReadEndsIsNeverTheMastersTurn(TaskStop.Trigger trigger) {
        TaskState task = stopped(trigger);

        assertThat(StopHold.of(task, ACT)).isEqualTo(StopHold.HUMAN);
    }

    @Test
    void aStopTheHumanKeptTheLiftOfIsTheirsInAct() {
        Arbiter.Profile kept = new Arbiter.Profile(MasterMode.ACT, Set.of(MasterRight.LIFT));

        assertThat(StopHold.of(stopped(TaskStop.Trigger.ROUNDS), kept)).isEqualTo(StopHold.HUMAN);
    }

    @Test
    void aStopIsTheHumansInJudge() {
        Arbiter.Profile judge = new Arbiter.Profile(MasterMode.JUDGE, Set.of());

        assertThat(StopHold.of(stopped(TaskStop.Trigger.ROUNDS), judge)).isEqualTo(StopHold.HUMAN);
    }

    @Test
    void aStopTheMasterWasAskedAboutIsTheHumansOnceAsked() {
        TaskState task = stopped(TaskStop.Trigger.ROUNDS);
        TaskState asked = task.withMaster(Master.defaults().withAskedStop(task.stop().key()));

        assertThat(StopHold.of(asked, ACT)).isEqualTo(StopHold.HUMAN);
    }

    @Test
    void aStopIsTheHumansOnceTheMasterLiftedOneSinceTheirLastLift() {
        TaskState spent = stopped(TaskStop.Trigger.ROUNDS).withMaster(Master.defaults().withLiftedStops(1));

        assertThat(StopHold.of(spent, ACT)).isEqualTo(StopHold.HUMAN);
    }
}
