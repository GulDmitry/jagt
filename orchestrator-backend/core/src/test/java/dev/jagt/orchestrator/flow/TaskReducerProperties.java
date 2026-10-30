package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Turn;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TaskReducerProperties {

    @Property
    void aDeployConflictNeverTravelsWithAStatusChange(@ForAll("tasks") TaskState start,
                                                @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, move) -> {
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            Optional.of(next).filter(moved -> moved.status() != task.status())
                    .filter(moved -> task.status() == TaskStatus.DEPLOY_CONFLICT
                            || Optional.ofNullable(task.deployment()).flatMap(Deployment::conflictedTarget).isEmpty())
                    .ifPresent(moved -> assertThat(Optional.ofNullable(moved.deployment())
                            .flatMap(Deployment::conflictedTarget)).isEmpty());
            return next;
        }, (left, right) -> right);
    }

    @Property
    void aStopEndsOnlyOnALiftOrOnItsOwnEnd(@ForAll("tasks") TaskState start,
                                           @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, move) -> {
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            Optional.ofNullable(task.stop())
                    .filter(stop -> !(move instanceof TaskMove.Lifted)
                            && !(move instanceof TaskMove.StopEnded ended && ended.ends() == stop.trigger().ends()))
                    .ifPresent(stop -> assertThat(next.stop()).isEqualTo(stop));
            return next;
        }, (left, right) -> right);
    }

    @Property
    void aRequiredPlanGateOpensOnlyOnTheApproval(@ForAll("tasks") TaskState start,
                                                 @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, move) -> {
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            Optional.of(task).filter(before -> before.planGate() == PlanGate.REQUIRED)
                    .filter(before -> !(move instanceof TaskMove.PlanApproved))
                    .ifPresent(before -> assertThat(next.planGate()).isEqualTo(PlanGate.REQUIRED));
            return next;
        }, (left, right) -> right);
    }

    @Property
    void aQuestionStandsUntilTheSessionSpeaksOrAVerdictOrADeployReadMovesTheStatus(
            @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, move) -> {
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            Optional.of(task).filter(before -> before.report().asks())
                    .filter(before -> !(move instanceof TaskMove.Said || move instanceof TaskMove.Stamped
                            || move instanceof TaskMove.Verified))
                    .filter(before -> !(move instanceof TaskMove.DeployRead && next.status() != before.status()))
                    .ifPresent(before -> assertThat(next.report()).isEqualTo(before.report()));
            return next;
        }, (left, right) -> right);
    }

    @Property
    void aMoveTheChartHasNoEdgeForLeavesStatusDeploymentAndStopAlone(@ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, move) -> {
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            Optional.of(TaskChart.effects(task.status(), StopKind.of(task.stop()), task.planGate(), task.report(),
                    TurnKind.of(task.turnOrNone()), DeployPhase.of(task.deployment()), SessionPhase.of(task.session()), move))
                    .filter(List::isEmpty).ifPresent(none -> {
                        assertThat(next.status()).isEqualTo(task.status());
                        assertThat(next.deployment()).isEqualTo(task.deployment());
                        assertThat(next.stop()).isEqualTo(task.stop());
                    });
            return next;
        }, (left, right) -> right);
    }

    @Property
    void aTurnCountsExactlyOneMoreOnEveryRelayAndCloseAndOtherwiseNotAtAll(@ForAll("tasks") TaskState start,
                                                                          @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, move) -> {
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            long id = task.turnOrNone().id();
            Optional.of(move).filter(m -> m instanceof TaskMove.Relayed || m instanceof TaskMove.TurnClosed)
                    .ifPresent(m -> assertThat(next.turnOrNone().id()).isEqualTo(id + 1));
            Optional.of(move).filter(m -> m instanceof TaskMove.TurnRestored restored)
                    .ifPresent(m -> assertThat(next.turnOrNone()).isEqualTo(((TaskMove.TurnRestored) m).before()));
            Optional.of(move).filter(m -> !(m instanceof TaskMove.Relayed || m instanceof TaskMove.TurnClosed
                            || m instanceof TaskMove.TurnRestored))
                    .ifPresent(m -> assertThat(next.turnOrNone().id()).isEqualTo(id));
            return next;
        }, (left, right) -> right);
    }

    @Property
    void theSameMovesFromTheSameTaskGiveTheSameTask(@ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
        TaskState first = moves.stream().reduce(start,
                (task, move) -> TaskReducer.reduce(task, move, "step", true, 1_000), (left, right) -> right);
        TaskState second = moves.stream().reduce(start,
                (task, move) -> TaskReducer.reduce(task, move, "step", true, 1_000), (left, right) -> right);

        assertThat(second).isEqualTo(first);
    }

    @Property
    void aDoneTaskStaysDone(@ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, move) -> {
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            Optional.of(task).filter(before -> before.status() == TaskStatus.DONE).ifPresent(before ->
                    assertThat(next.status()).isEqualTo(TaskStatus.DONE));
            return next;
        }, (left, right) -> right);
    }

    @Provide
    Arbitrary<TaskState> tasks() {
        Arbitrary<Deployment> deployment = Arbitraries.just(new Deployment(1_000, List.of(
                Deployment.Target.of("api", "dev").withConflict(Conflict.of(Deployment.Cause.MERGE, 1_000))), 0, null))
                .injectNull(0.5);
        Arbitrary<TaskStop> stop = Arbitraries.of(TaskStop.Trigger.class).map(trigger ->
                new TaskStop(trigger, 1_000, "evidence")).injectNull(0.5);
        Arbitrary<Turn> turn = Arbitraries.of("", "d").map(digest -> new Turn("ship", 4, digest, 1_000, null, null));
        return Combinators.combine(Arbitraries.of(TaskStatus.class), deployment, stop, Arbitraries.of(PlanGate.class),
                        Arbitraries.of(AgentReport.class), turn)
                .as((status, recorded, standing, gate, report, held) -> TaskState.builder("api", "/wt", status)
                        .deployment(recorded).stop(standing).planGate(gate).report(report).turn(held).build());
    }

    @Provide
    Arbitrary<List<TaskMove>> moves() {
        Arbitrary<TaskStatus> status = Arbitraries.of(TaskStatus.class);
        Arbitrary<Boolean> flag = Arbitraries.of(true, false);
        Arbitrary<TaskStop> stop = Arbitraries.of(TaskStop.Trigger.class).map(trigger ->
                new TaskStop(trigger, 1_000, "evidence"));
        Arbitrary<TaskMove> move = Arbitraries.oneOf(
                Arbitraries.of(TaskAction.class).flatMap(action -> Arbitraries.of(Outcome.Kind.class)
                        .map(outcome -> new TaskMove.Ran(action, outcome))),
                status.flatMap(to -> flag.map(owed -> new TaskMove.Reported(to, owed))),
                status.map(TaskMove.Read::new),
                flag.map(TaskMove.DeployRead::new),
                flag.map(TaskMove.Verified::new),
                stop.map(TaskMove.StopSet::new),
                Arbitraries.just(new TaskMove.Lifted()),
                Arbitraries.of(TaskStop.Trigger.Ends.class).map(TaskMove.StopEnded::new),
                flag.map(TaskMove.PlanAsked::new),
                Arbitraries.just(new TaskMove.PlanApproved()),
                Arbitraries.just(new TaskMove.NoPlanDeclared()),
                Arbitraries.just(new TaskMove.Autoplanned()),
                Arbitraries.of(AgentReport.class).map(TaskMove.Said::new),
                Arbitraries.of(AgentReport.class).map(TaskMove.Stamped::new),
                Arbitraries.just(new TaskMove.Relayed("ship", "d", "a")),
                Arbitraries.just(new TaskMove.TurnClosed()),
                Arbitraries.of("", "d").map(digest -> new TaskMove.TurnRestored(
                        new Turn("ship", 2, digest, 500, null, null))));
        return move.list().ofMaxSize(50);
    }
}
