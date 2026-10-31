package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.ChecksUnread;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.MasterMode;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskRepo;
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
import java.util.Map;
import java.util.Set;
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
    void aGitChangeLeavesOnlyOnATrustMove(@ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, move) -> {
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            Optional.of(task).filter(TaskState::gitStateChanged)
                    .filter(before -> !(move instanceof TaskMove.GitTrusted))
                    .ifPresent(before -> assertThat(next.gitStateChanged()).isTrue());
            return next;
        }, (left, right) -> right);
    }

    @Property
    void aMergedRequestNeverLetsShipRun(@ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, move) -> {
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            Optional.of(next).filter(moved -> RequestPhase.of(moved) == RequestPhase.MERGED)
                    .ifPresent(moved -> assertThat(FlowRules.allows(moved.status(), TaskAction.SHIP,
                            Facts.projected(moved))).isFalse());
            return next;
        }, (left, right) -> right);
    }

    @Property
    void aNewHeadNeverLeavesAnInfraRetryArmed(@ForAll("tasks") TaskState start,
                                              @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, move) -> {
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            Optional.of(move).filter(seen -> seen instanceof TaskMove.HeadSeen head && head.moved())
                    .ifPresent(seen -> assertThat(InfraPhase.of(next)).isNotEqualTo(InfraPhase.ARMED));
            return next;
        }, (left, right) -> right);
    }

    @Property
    void aMoveTheChartHasNoEdgeForLeavesStatusDeploymentAndStopAlone(@ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, move) -> {
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            Optional.of(TaskChart.effects(Phases.of(task), move))
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
            Optional.of(move).filter(m -> m instanceof TaskMove.Relayed || m instanceof TaskMove.TurnClosed
                            || m instanceof TaskMove.QaReadEnded ended && ended.holds())
                    .ifPresent(m -> assertThat(next.turnOrNone().id()).isEqualTo(id + 1));
            Optional.of(move).filter(m -> m instanceof TaskMove.TurnRestored restored)
                    .ifPresent(m -> assertThat(next.turnOrNone()).isEqualTo(((TaskMove.TurnRestored) m).before()));
            Optional.of(move).filter(m -> !(m instanceof TaskMove.Relayed || m instanceof TaskMove.TurnClosed
                            || m instanceof TaskMove.TurnRestored || m instanceof TaskMove.QaReadEnded ended
                            && ended.holds()))
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

    @Property
    void eachDerivedRegionReadFromTheNewRecordIsTheStateTheTakenEdgeDeclared(@ForAll("tasks") TaskState start,
                                                                           @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, raw) -> {
            TaskMove move = TaskReducer.resolved(task, raw);
            Map<String, String> declared = TaskChart.step(Phases.of(task), move).configuration().toPlain();
            TaskState next = TaskReducer.reduce(task, move, "step", true, 1_000);
            assertThat(Map.of("git", GitPhase.of(next.gitTrust()).name(), "repo",
                    RepoPhase.of(next.repoRequest()).name(), "request", RequestPhase.of(next).name(),
                    "checks", ChecksPhase.of(next).name(), "approval", ApprovalPhase.of(next).name(),
                    "infra", InfraPhase.of(next).name(), "qa", QaPhase.of(next).name()))
                    .isEqualTo(Map.of("git", declared.get("git"), "repo", declared.get("repo"),
                            "request", declared.get("request"), "checks", declared.get("checks"),
                            "approval", declared.get("approval"), "infra", declared.get("infra"),
                            "qa", declared.get("qa")));
            return next;
        }, (left, right) -> right);
    }

    @Property
    void qaHoldsTheTurnOnlyWhileItsReadRuns(@ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
        moves.stream().reduce(start, (task, raw) -> {
            TaskState next = TaskReducer.reduce(task, TaskReducer.resolved(task, raw), "step", true, 1_000);
            Optional.of(next).filter(moved -> !FlowRules.closed(moved.status()) && !moved.stopped())
                    .ifPresent(moved -> assertThat(Turns.next(moved.status(), moved.report(), false,
                            QaPhase.of(moved), Turns.Profile.OFF) == Turn.Holder.QA)
                            .isEqualTo(QaPhase.of(moved) == QaPhase.READING));
            return next;
        }, (left, right) -> right);
    }

    @Property
    void theMasterHoldsATurnOnlyWhereItStandsIn(@ForAll("tasks") TaskState start,
                                                @ForAll("moves") List<TaskMove> moves) {
        Turns.Profile acting = new Turns.Profile(MasterMode.ACT, Set.of());
        moves.stream().reduce(start, (task, raw) -> {
            TaskState next = TaskReducer.reduce(task, TaskReducer.resolved(task, raw), "step", true, 1_000);
            Optional.of(next).filter(moved -> MasterPhase.of(moved) != MasterPhase.STANDS_IN)
                    .ifPresent(moved -> assertThat(Turns.owner(moved, acting)).isNotEqualTo(Turn.Holder.MASTER));
            return next;
        }, (left, right) -> right);
    }

    @Provide
    Arbitrary<TaskState> tasks() {
        Arbitrary<Deployment> deployment = Arbitraries.of(new Deployment(1_000, List.of(
                        Deployment.Target.of("api", "dev").withConflict(Conflict.of(Deployment.Cause.MERGE, 1_000))), 0, null),
                new Deployment(1_000, List.of(Deployment.Target.of("api", "dev").withMerge("abc")), 1, "d1"))
                .injectNull(0.4);
        Arbitrary<TaskStop> stop = Arbitraries.of(TaskStop.Trigger.class).map(trigger ->
                new TaskStop(trigger, 1_000, "evidence")).injectNull(0.5);
        Arbitrary<Turn> turn = Arbitraries.of("", "d").map(digest -> new Turn("ship", 4, digest, 1_000, null, null));
        Arbitrary<GitTrust> trust = Arbitraries.of(GitTrust.of(Map.of("a", "1")),
                GitTrust.of(Map.of("a", "1")).withChanged("a changed")).injectNull(0.3);
        Arbitrary<List<TaskRepo>> repos = Arbitraries.of(List.of(TaskRepo.of("api", "/wt")),
                List.of(new TaskRepo("api", "/wt", null, "https://host.example/1", true, null)),
                List.of(new TaskRepo("api", "/wt", null, "https://host.example/1", null, null)));
        Arbitrary<List<InfraRetry>> retries = Arbitraries.of(List.of(),
                List.of(InfraRetry.armed(9_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", 1, 3)),
                List.of(new InfraRetry(8_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true,
                        3, 3)));
        return Combinators.combine(Arbitraries.of(TaskStatus.class), deployment, stop, Arbitraries.of(PlanGate.class),
                        Arbitraries.of(AgentReport.class), turn, trust, repos)
                .as((status, recorded, standing, gate, report, held, git, held2) ->
                        TaskState.builder("api", "/wt", status).deployment(recorded).stop(standing).planGate(gate)
                                .report(report).turn(held).gitTrust(git).repos(held2).build())
                .flatMap(task -> retries.map(held -> task.toBuilder().infraRetries(held).build()));
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
                Arbitraries.of("accept", "decline", "again").map(step -> new TaskMove.Relayed(step, "d1", "a")),
                flag.map(TaskMove.QaReadEnded::new),
                Arbitraries.of(new MasterNote.Left("deploy x"), new MasterNote.Took(), new MasterNote.Unanswered("u"),
                        new MasterNote.Sealed("s", 4)).map(note -> new TaskMove.MasterNoted(note, MasterPhase.LEFT)),
                Arbitraries.just(new TaskMove.QaPassed("api@abc", false)),
                Arbitraries.just(new TaskMove.QaRoundSent("d1", "- row", false)),
                Arbitraries.just(new TaskMove.TurnClosed()),
                Arbitraries.just(new TaskMove.GitTrusted(GitTrust.of(Map.of("b", "2")))),
                Arbitraries.just(new TaskMove.GitChanged("b changed")),
                Arbitraries.just(new TaskMove.RepoAsked("registry")),
                Arbitraries.just(new TaskMove.RepoUnasked()),
                Arbitraries.of("t1", "t2").map(tip -> new TaskMove.HeadSeen(tip, true, false, InfraPhase.NONE)),
                Arbitraries.of("t1", "t2").map(tip -> new TaskMove.ChecksRead("failed", tip, false)),
                Arbitraries.just(new TaskMove.InfraArmed(InfraRetry.armed(9_000, "https://host.invalid/p/1", "unit-tests",
                        "runner_system_failure", 1, 3))),
                flag.map(all -> new TaskMove.InfraCleared(all, InfraPhase.NONE)),
                Arbitraries.just(new TaskMove.InfraSent(new InfraRetry(9_500, "https://host.invalid/p/1", "unit-tests",
                        "runner_system_failure", true, 1, 3))),
                Arbitraries.just(new TaskMove.ChecksUnread(new ChecksUnread(500, "no listing"))),
                flag.map(TaskMove.ApprovalRead::new),
                flag.map(owed -> new TaskMove.RequestLinked(Map.of("api", "https://host.example/2"),
                        "https://host.example/2", owed, false, false)),
                flag.map(merged -> new TaskMove.MergedByGit("api", merged, false)),
                Arbitraries.just(new TaskMove.MergedByHost("api", false)),
                Arbitraries.just(new TaskMove.RepoAttached(TaskRepo.of("registry", "/wt-registry"))),
                Arbitraries.just(new TaskMove.GitRepoJoined(GitTrust.of(Map.of("c", "3")))),
                Arbitraries.of("", "d").map(digest -> new TaskMove.TurnRestored(
                        new Turn("ship", 2, digest, 500, null, null))));
        return move.list().ofMaxSize(50);
    }
}
