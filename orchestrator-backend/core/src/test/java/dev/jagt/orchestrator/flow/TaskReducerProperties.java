package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.deploy.ApprovalPhase;
import dev.jagt.orchestrator.flow.deploy.ChecksPhase;
import dev.jagt.orchestrator.flow.deploy.InfraPhase;
import dev.jagt.orchestrator.flow.repo.GitPhase;
import dev.jagt.orchestrator.flow.repo.RepoPhase;
import dev.jagt.orchestrator.flow.repo.RequestPhase;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.jspecify.annotations.Nullable;

class TaskReducerProperties {

  private static final TaskChart chart = new TaskChart(Composition.builtIn());
  private static final FlowRules rules = new FlowRules(chart);

  private static final TaskReducer reducer = new TaskReducer(chart);

  @Property
  void deployConflictNeverTravelsWithStatusChange(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, move) -> {
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      Optional.of(next)
                          .filter(moved -> moved.standing().status() != task.standing().status())
                          .filter(moved ->
                              task.standing().status() == TaskStatus.DEPLOY_CONFLICT
                                  || Optional.ofNullable(task.work().deployment())
                                      .flatMap(Deployment::conflictedTarget)
                                      .isEmpty())
                          .ifPresent(moved ->
                              assertThat(
                                      Optional.ofNullable(moved.work().deployment())
                                          .flatMap(Deployment::conflictedTarget))
                                  .isEmpty());
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void stopEndsOnlyOnLiftOrOnItsOwnEnd(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, move) -> {
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      Optional.ofNullable(task.work().stop())
                          .filter(stop ->
                              !(move instanceof TaskMove.Lifted)
                                  && !(move instanceof TaskMove.StopEnded ended
                                      && ended.ends() == stop.trigger().ends()))
                          .ifPresent(stop -> assertThat(next.work().stop()).isEqualTo(stop));
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void requiredPlanGateOpensOnlyOnTheApproval(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, move) -> {
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      Optional.of(task)
                          .filter(before -> before.gate().planGate() == PlanGate.REQUIRED)
                          .filter(before -> !(move instanceof TaskMove.PlanApproved))
                          .ifPresent(before ->
                              assertThat(next.gate().planGate()).isEqualTo(PlanGate.REQUIRED));
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void questionStandsUntilTheSessionSpeaksOrVerdictOrDeployReadMovesTheStatus(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, move) -> {
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      Optional.of(task)
                          .filter(before -> before.standing().report().asks())
                          .filter(before ->
                              !(move instanceof TaskMove.Said
                                  || move instanceof TaskMove.Stamped
                                  || move instanceof TaskMove.Verified))
                          .filter(before ->
                              !(move instanceof TaskMove.DeployRead
                                  && next.standing().status() != before.standing().status()))
                          .ifPresent(before ->
                              assertThat(next.standing().report())
                                  .isEqualTo(before.standing().report()));
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void gitChangeLeavesOnlyOnTrustMove(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, move) -> {
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      Optional.of(task)
                          .filter(taskState -> taskState.gate().gitStateChanged())
                          .filter(before -> !(move instanceof TaskMove.GitTrusted))
                          .ifPresent(before -> assertThat(next.gate().gitStateChanged()).isTrue());
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void mergedRequestNeverLetsShipRun(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, move) -> {
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      Optional.of(next)
                          .filter(moved -> RequestPhase.of(moved) == RequestPhase.MERGED)
                          .ifPresent(moved ->
                              assertThat(
                                      rules.allows(
                                          moved.standing().status(),
                                          TaskAction.SHIP,
                                          Facts.projected(moved)))
                                  .isFalse());
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void newHeadNeverLeavesAnInfraRetryArmed(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, move) -> {
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      Optional.of(move)
                          .filter(seen -> seen instanceof TaskMove.HeadSeen head && head.moved())
                          .ifPresent(seen ->
                              assertThat(InfraPhase.of(next)).isNotEqualTo(InfraPhase.ARMED));
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void moveTheChartHasNoEdgeForLeavesStatusDeploymentAndStopAlone(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, move) -> {
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      Optional.of(chart.effects(Phases.of(task), move))
                          .filter(List::isEmpty)
                          .ifPresent(none -> {
                            assertThat(next.standing().status())
                                .isEqualTo(task.standing().status());
                            assertThat(next.work().deployment())
                                .isEqualTo(task.work().deployment());
                            assertThat(next.work().stop()).isEqualTo(task.work().stop());
                          });
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void turnCountsExactlyOneMoreOnEveryRelayAndCloseAndOtherwiseNotAtAll(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, move) -> {
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      long id = task.work().turnOrNone().id();
                      Optional.of(move)
                          .filter(m ->
                              m instanceof TaskMove.Relayed
                                  || m instanceof TaskMove.TurnClosed
                                  || (m instanceof TaskMove.Checked.ReadEnded ended
                                      && ended.holds()))
                          .ifPresent(
                              m -> assertThat(next.work().turnOrNone().id()).isEqualTo(id + 1));
                      Optional.of(move)
                          .filter(m -> m instanceof TaskMove.TurnRestored _)
                          .ifPresent(m ->
                              assertThat(next.work().turnOrNone())
                                  .isEqualTo(((TaskMove.TurnRestored) m).before()));
                      Optional.of(move)
                          .filter(m ->
                              !(m instanceof TaskMove.Relayed
                                  || m instanceof TaskMove.TurnClosed
                                  || m instanceof TaskMove.TurnRestored
                                  || (m instanceof TaskMove.Checked.ReadEnded ended
                                      && ended.holds())))
                          .ifPresent(m -> assertThat(next.work().turnOrNone().id()).isEqualTo(id));
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void theSameMovesFromTheSameTaskGiveTheSameTask(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    TaskState first =
        moves.stream()
            .reduce(
                start,
                (task, move) -> reducer.reduce(task, move, "step", true, 1_000),
                (left, right) -> right);
    TaskState second =
        moves.stream()
            .reduce(
                start,
                (task, move) -> reducer.reduce(task, move, "step", true, 1_000),
                (left, right) -> right);

    assertThat(second).isEqualTo(first);
  }

  @Property
  void doneTaskStaysDone(@ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, move) -> {
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      Optional.of(task)
                          .filter(before -> before.standing().status() == TaskStatus.DONE)
                          .ifPresent(before ->
                              assertThat(next.standing().status()).isEqualTo(TaskStatus.DONE));
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void eachDerivedRegionReadFromTheNewRecordIsTheStateTheTakenEdgeDeclared(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, raw) -> {
                      TaskMove move = reducer.resolved(task, raw);
                      Map<String, String> declared =
                          chart.step(Phases.of(task), move).configuration().toPlain();
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      assertThat(
                              Map.of(
                                  "git",
                                  GitPhase.of(next.gate().gitTrust()).name(),
                                  "repo",
                                  RepoPhase.of(next.work().repoRequest()).name(),
                                  "request",
                                  RequestPhase.of(next).name(),
                                  "checks",
                                  ChecksPhase.of(next).name(),
                                  "approval",
                                  ApprovalPhase.of(next).name(),
                                  "infra",
                                  InfraPhase.of(next).name(),
                                  "acceptance",
                                  CheckerPhase.of(next, Checkers.ACCEPTANCE).name()))
                          .isEqualTo(
                              Map.of(
                                  "git",
                                  declared.get("git"),
                                  "repo",
                                  declared.get("repo"),
                                  "request",
                                  declared.get("request"),
                                  "checks",
                                  declared.get("checks"),
                                  "approval",
                                  declared.get("approval"),
                                  "infra",
                                  declared.get("infra"),
                                  "acceptance",
                                  declared.get("acceptance")));
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void acceptanceHoldsTheTurnOnlyWhileItsReadRuns(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, raw) -> {
                      TaskState next =
                          reducer.reduce(task, reducer.resolved(task, raw), "step", true, 1_000);
                      Optional.of(next)
                          .filter(moved ->
                              !FlowRules.closed(moved.standing().status())
                                  && !moved.work().stopped())
                          .ifPresent(moved ->
                              assertThat(
                                      Turns.next(
                                              moved.standing().status(),
                                              moved.standing().report(),
                                              false,
                                              new Checkers(new TaskChart(Composition.builtIn()))
                                                  .of(moved),
                                              Arbiter.Profile.OFF)
                                          .equals(new Turn.Holder.Role("acceptance")))
                                  .isEqualTo(
                                      CheckerPhase.of(moved, Checkers.ACCEPTANCE)
                                          == CheckerPhase.READING));
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void theMasterReadsTheRoundOnlyWhileItsVerdictStands(
      @ForAll("tasks") TaskState start, @ForAll("moves") List<TaskMove> moves) {
    assertThat(
            moves.stream()
                .reduce(
                    start,
                    (task, raw) -> {
                      TaskMove move = reducer.resolved(task, raw);
                      TaskState next = reducer.reduce(task, move, "step", true, 1_000);
                      Optional.of(next)
                          .filter(moved ->
                              MasterPhase.of(moved) != MasterPhase.LEFT
                                  && FlowRules.readByTheArbiter(moved.standing().status()))
                          .ifPresent(moved -> {
                            TaskState read =
                                reducer.arbiterSealed(
                                    moved, "fingerprint", moved.work().turnOrNone().id());
                            assertThat(MasterPhase.of(read)).isEqualTo(MasterPhase.READ);
                            assertThat(MasterPhase.of(reducer.arbiterVerdictGone(read)))
                                .isEqualTo(MasterPhase.STANDS_IN);
                          });
                      return next;
                    },
                    (left, right) -> right))
        .isNotNull();
  }

  @Property
  void noMoveSentWithoutTimeYieldsAnEffectThatReadsOne(
      @ForAll("tasks") TaskState task,
      @ForAll("timelessDoors") Function<TaskState, TaskState> door) {
    assertThatCode(() -> door.apply(task)).doesNotThrowAnyException();
  }

  @Provide
  Arbitrary<Function<TaskState, TaskState>> timelessDoors() {
    TaskRepo extra = TaskRepo.of("registry", "/wt-registry");
    return Arbitraries.of(AgentReport.class)
        .flatMap(report ->
            Arbitraries.of(
                task -> reducer.planAsked(task, true),
                task -> reducer.planAsked(task, false),
                reducer::planApproved,
                reducer::noPlanDeclared,
                reducer::autoplanned,
                task -> reducer.said(task, report),
                task -> reducer.stamped(task, report),
                task -> reducer.arbiterLeft(task, "deploy x"),
                reducer::arbiterTook,
                task -> reducer.arbiterUnanswered(task, "d1"),
                task -> reducer.arbiterAskedStop(task, "key"),
                task -> reducer.arbiterLiftedStops(task, 2),
                task -> reducer.arbiterSealed(task, "s", 4),
                reducer::arbiterVerdictGone,
                task -> reducer.checkerPassed(task, "acceptance", "api@abc"),
                task -> reducer.checkerRoundSent(task, "acceptance", "d1", "- row"),
                task ->
                    reducer.turnRestored(task, new Turn("ship", 2, "d", 500, Map.of(), List.of())),
                reducer::sessionStartedOver,
                task -> reducer.gitTrusted(task, GitTrust.of(Map.of("b", "2"))),
                task -> reducer.gitChanged(task, "b changed"),
                task -> reducer.gitRepoJoined(task, GitTrust.of(Map.of("c", "3"))),
                task -> reducer.repoAsked(task, "registry"),
                task -> reducer.repoAsked(task, null),
                task -> reducer.repoAttached(task, extra),
                task -> reducer.placed(task, List.of(extra), "origin/main"),
                task -> reducer.mergedByGit(task, "api", true),
                task -> reducer.mergedByGit(task, "api", false),
                task -> reducer.mergedByHost(task, "api"),
                task -> reducer.headSeen(task, "t1"),
                task -> reducer.checksRead(task, "failed", "t1"),
                task -> reducer.checksUnread(task, new ChecksUnread(500, "no listing")),
                task -> reducer.approvalRead(task, true),
                task ->
                    reducer.infraArmed(
                        task,
                        InfraRetry.armed(
                            9_000, "shop", "https://host.invalid/p/1", "unit-tests", "x", 1, 3)),
                task -> reducer.infraCleared(task, true),
                task -> reducer.infraCleared(task, false)));
  }

  @Provide
  Arbitrary<TaskState> tasks() {
    Arbitrary<@Nullable Deployment> deployment =
        Arbitraries.of(
                new Deployment(
                    1_000,
                    List.of(
                        Deployment.Target.of("api", "dev")
                            .withConflict(Conflict.of(Deployment.Cause.MERGE, 1_000))),
                    0,
                    null),
                new Deployment(
                    1_000, List.of(Deployment.Target.of("api", "dev").withMerge("abc")), 1, "d1"))
            .injectNull(0.4);
    Arbitrary<@Nullable TaskStop> stop =
        Arbitraries.of(TaskStop.Trigger.class)
            .map(trigger -> new TaskStop(trigger, 1_000, "evidence"))
            .injectNull(0.5);
    Arbitrary<Turn> turn =
        Arbitraries.of("", "d")
            .map(digest -> new Turn("ship", 4, digest, 1_000, Map.of(), List.of()));
    Arbitrary<@Nullable GitTrust> trust =
        Arbitraries.of(
                GitTrust.of(Map.of("a", "1")),
                GitTrust.of(Map.of("a", "1")).withChanged("a changed"))
            .injectNull(0.3);
    Arbitrary<List<TaskRepo>> repos =
        Arbitraries.of(
            List.of(TaskRepo.of("api", "/wt")),
            List.of(new TaskRepo("api", "/wt", null, "https://host.example/1", true, null)),
            List.of(new TaskRepo("api", "/wt", null, "https://host.example/1", null, null)));
    Arbitrary<List<InfraRetry>> retries =
        Arbitraries.of(
            List.of(),
            List.of(
                InfraRetry.armed(
                    9_000,
                    "shop",
                    "https://host.invalid/p/1",
                    "unit-tests",
                    "runner_system_failure",
                    1,
                    3)),
            List.of(
                new InfraRetry(
                    8_000,
                    "shop",
                    "https://host.invalid/p/1",
                    "unit-tests",
                    "runner_system_failure",
                    true,
                    3,
                    3)));
    return Combinators.combine(
            Arbitraries.of(TaskStatus.class),
            deployment,
            stop,
            Arbitraries.of(PlanGate.class),
            Arbitraries.of(AgentReport.class),
            turn,
            trust,
            repos)
        .as((status, recorded, standing, gate, report, held, git, held2) ->
            TaskState.builder("api", "/wt", status)
                .deployment(recorded)
                .stop(standing)
                .planGate(gate)
                .report(report)
                .turn(held)
                .gitTrust(git)
                .repos(held2)
                .build())
        .flatMap(task -> retries.map(held -> task.toBuilder().infraRetries(held).build()));
  }

  @Provide
  Arbitrary<List<TaskMove>> moves() {
    Arbitrary<TaskStatus> status = Arbitraries.of(TaskStatus.class);
    Arbitrary<Boolean> flag = Arbitraries.of(true, false);
    Arbitrary<TaskStop> stop =
        Arbitraries.of(TaskStop.Trigger.class)
            .map(trigger -> new TaskStop(trigger, 1_000, "evidence"));
    Arbitrary<TaskMove> move =
        Arbitraries.oneOf(
            Arbitraries.of(TaskAction.class)
                .flatMap(action ->
                    Arbitraries.of(Outcome.Kind.class)
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
            flag.map(TaskMove.NoPlanDeclared::new),
            Arbitraries.just(new TaskMove.Autoplanned()),
            Arbitraries.of(AgentReport.class).map(TaskMove.Said::new),
            Arbitraries.of(AgentReport.class).map(TaskMove.Stamped::new),
            Arbitraries.just(new TaskMove.Relayed("ship", "d", "a")),
            Arbitraries.of("accept", "decline", "again")
                .map(step -> new TaskMove.Relayed(step, "d1", "a")),
            flag.map(holds -> new TaskMove.Checked.ReadEnded("acceptance", holds)),
            Arbitraries.of(
                    new MasterNote.Left("deploy x"),
                    new MasterNote.Took(),
                    new MasterNote.Unanswered("u"),
                    new MasterNote.Sealed("s", 4))
                .map(note -> new TaskMove.MasterNoted(note, MasterPhase.LEFT)),
            Arbitraries.just(new TaskMove.Checked.Passed("acceptance", "api@abc", false)),
            Arbitraries.just(new TaskMove.Checked.RoundSent("acceptance", "d1", "- row", false)),
            Arbitraries.just(new TaskMove.TurnClosed()),
            Arbitraries.just(new TaskMove.GitTrusted(GitTrust.of(Map.of("b", "2")))),
            Arbitraries.just(new TaskMove.GitChanged("b changed")),
            Arbitraries.just(new TaskMove.RepoAsked("registry")),
            Arbitraries.just(new TaskMove.RepoUnasked()),
            Arbitraries.of("t1", "t2")
                .map(tip -> new TaskMove.HeadSeen(tip, true, false, InfraPhase.NONE)),
            Arbitraries.of("t1", "t2").map(tip -> new TaskMove.ChecksRead("failed", tip, false)),
            Arbitraries.just(
                new TaskMove.InfraArmed(
                    InfraRetry.armed(
                        9_000,
                        "shop",
                        "https://host.invalid/p/1",
                        "unit-tests",
                        "runner_system_failure",
                        1,
                        3))),
            flag.map(all -> new TaskMove.InfraCleared(all, InfraPhase.NONE)),
            Arbitraries.just(
                new TaskMove.InfraSent(
                    new InfraRetry(
                        9_500,
                        "shop",
                        "https://host.invalid/p/1",
                        "unit-tests",
                        "runner_system_failure",
                        true,
                        1,
                        3))),
            Arbitraries.just(new TaskMove.ChecksUnread(new ChecksUnread(500, "no listing"))),
            flag.map(TaskMove.ApprovalRead::new),
            flag.map(owed ->
                new TaskMove.RequestLinked(
                    Map.of("api", "https://host.example/2"),
                    "https://host.example/2",
                    owed,
                    false,
                    false)),
            flag.map(merged -> new TaskMove.MergedByGit("api", merged, false)),
            Arbitraries.just(new TaskMove.MergedByHost("api", false)),
            Arbitraries.just(new TaskMove.RepoAttached(TaskRepo.of("registry", "/wt-registry"))),
            Arbitraries.just(
                new TaskMove.RepoLeft("api", RequestPhase.NONE, TaskMove.ConflictDrop.NONE)),
            Arbitraries.just(new TaskMove.GitRepoJoined(GitTrust.of(Map.of("c", "3")))),
            Arbitraries.of("", "d")
                .map(digest ->
                    new TaskMove.TurnRestored(
                        new Turn("ship", 2, digest, 500, Map.of(), List.of()))));
    return move.list().ofMaxSize(50);
  }
}
