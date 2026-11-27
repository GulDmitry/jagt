package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.task.TaskStatus.DEPLOYED;
import static dev.jagt.orchestrator.task.TaskStatus.IN_PROGRESS;

import dev.jagt.orchestrator.flow.role.CheckerRole;
import dev.jagt.orchestrator.flow.role.RolePointKind;
import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.statechart.DataId;
import dev.jagt.statechart.RegionId;
import dev.jagt.statechart.StatechartBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

record CheckerRegion(CheckerRole role) implements Role {

  private static final Set<Class<? extends TaskMove>> TURN_ENDS =
      Set.of(
          TaskMove.Relayed.class,
          TaskMove.TurnClosed.class,
          TaskMove.TurnRestored.class,
          TaskMove.Checked.ReadEnded.class);

  static final CheckerRegion ACCEPTANCE =
      new CheckerRegion(
          new CheckerRole(
              Checkers.ACCEPTANCE,
              true,
              List.of(AcceptanceVerdict.ACCEPT, AcceptanceVerdict.AGAIN, AcceptanceVerdict.DECLINE),
              List.of(RolePointKind.values())));

  @Override
  public String id() {
    return role.id();
  }

  @Override
  public Kind kind() {
    return Kind.CHECKER;
  }

  RegionId<CheckerPhase> region() {
    return new RegionId<>(id(), CheckerPhase.class);
  }

  DataId<CheckerPhase> beneath() {
    return new DataId<>(id() + " beneath a read", CheckerPhase.class);
  }

  @Override
  public void declareBeneath(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.datum(beneath(), facts -> facts.beneath(id()));
  }

  @Override
  public void declareRegion(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, Landing landing) {
    chart.region(region(), CheckerPhase.NONE);
    chart.addressed(TaskMove.Relayed.class, region());
    chart.addressed(TaskMove.TurnClosed.class, region());
    chart.addressed(TaskMove.TurnRestored.class, region());
    chart.addressed(TaskMove.Checked.ReadEnded.class, region(), ChartRegions.TURN);
    chart.addressed(TaskMove.Checked.Passed.class, region());
    chart.addressed(TaskMove.Checked.RoundSent.class, region());
    for (Class<? extends TaskMove> ends :
        List.of(
            TaskMove.TurnClosed.class,
            TaskMove.TurnRestored.class,
            TaskMove.Checked.ReadEnded.class)) {
      chart.ignores(
          region(),
          ends,
          EnumSet.of(CheckerPhase.NONE, CheckerPhase.ROUND_OPEN, CheckerPhase.PASSED),
          "a turn ending closes a read only while one is open");
    }
    for (CheckerPhase from : CheckerPhase.values()) {
      chart
          .onMatching(
              region(),
              from,
              TaskMove.Relayed.class,
              id() + " read opened",
              move ->
                  move instanceof TaskMove.Relayed relayed
                      && role.readSteps().contains(relayed.step()))
          .when((active, move) -> active.state(ChartRegions.LIFECYCLE) == DEPLOYED)
          .emitFor(move -> new TaskEffect.OpensCheckerRead(id(), ((TaskMove.Relayed) move).step()))
          .to(CheckerPhase.READING);
    }
    for (CheckerPhase under :
        EnumSet.of(CheckerPhase.NONE, CheckerPhase.ROUND_OPEN, CheckerPhase.PASSED)) {
      chart
          .onMatchingAny(
              region(),
              CheckerPhase.READING,
              TURN_ENDS,
              "the read ended, " + under + " beneath",
              this::endsTurn)
          .when((active, move) -> active.datum(beneath()) == under)
          .emit(new TaskEffect.ClosesCheckerRead(id()))
          .to(under);
      chart
          .onMatching(
              region(),
              CheckerPhase.READING,
              TaskMove.class,
              "deployed change left, " + under + " beneath",
              move -> !endsTurn(move))
          .when((active, move) ->
              active.datum(beneath()) == under
                  && active.state(ChartRegions.LIFECYCLE) == DEPLOYED
                  && landing.land(DEPLOYED, move).filter(to -> to != DEPLOYED).isPresent())
          .emit(new TaskEffect.ClosesCheckerRead(id()))
          .to(under);
    }
    declareVerdicts(chart);
  }

  private void declareVerdicts(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    for (CheckerPhase from : CheckerPhase.values()) {
      boolean reading = from == CheckerPhase.READING;
      ChartRegions.regionEdge(
          chart,
          region(),
          from,
          id() + " passed, standing",
          ChartRegions.takes(
              TaskMove.Checked.Passed.class,
              move ->
                  move instanceof TaskMove.Checked.Passed passed
                      && ownsAndStands(passed.role(), passed.standing())),
          CheckerRegion::passes,
          reading ? from : CheckerPhase.PASSED);
      ChartRegions.regionEdge(
          chart,
          region(),
          from,
          id() + " passed, not standing",
          ChartRegions.takes(
              TaskMove.Checked.Passed.class,
              move ->
                  move instanceof TaskMove.Checked.Passed passed
                      && ownsAndStands(passed.role(), !passed.standing())),
          CheckerRegion::passes,
          from);
      ChartRegions.regionEdge(
          chart,
          region(),
          from,
          id() + " round sent, open",
          ChartRegions.takes(
              TaskMove.Checked.RoundSent.class,
              move ->
                  move instanceof TaskMove.Checked.RoundSent sent
                      && ownsAndStands(sent.role(), sent.opens())),
          CheckerRegion::sendsRound,
          reading ? from : CheckerPhase.ROUND_OPEN);
      ChartRegions.regionEdge(
          chart,
          region(),
          from,
          id() + " round sent, not open",
          ChartRegions.takes(
              TaskMove.Checked.RoundSent.class,
              move ->
                  move instanceof TaskMove.Checked.RoundSent sent
                      && ownsAndStands(sent.role(), !sent.opens())),
          CheckerRegion::sendsRound,
          from);
    }
  }

  @Override
  public void declareTurnEdge(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TurnKind from) {
    chart
        .onMatching(
            ChartRegions.TURN,
            from,
            TaskMove.Checked.ReadEnded.class,
            id() + " read ended on its turn",
            move ->
                move instanceof TaskMove.Checked.ReadEnded ended
                    && id().equals(ended.role())
                    && ended.holds())
        .emit(new TaskEffect.ClosesTurn())
        .to(TurnKind.CLOSED);
  }

  @Override
  public void declareLifecycleEdge(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.addressed(TaskMove.Checked.RoundDeclined.class, ChartRegions.LIFECYCLE);
    chart.ignores(
        ChartRegions.LIFECYCLE,
        TaskMove.Checked.RoundDeclined.class,
        EnumSet.of(
            TaskStatus.APPROVED,
            TaskStatus.CI_FAILED,
            TaskStatus.CI_POLLING,
            TaskStatus.DEPLOY_CONFLICT,
            TaskStatus.DEPLOYED,
            TaskStatus.DONE,
            TaskStatus.NEW,
            TaskStatus.PLAN_PENDING,
            TaskStatus.REVERTED,
            TaskStatus.REVIEW_PENDING,
            TaskStatus.REVIEWED,
            TaskStatus.SHIPPING,
            TaskStatus.VERIFYING),
        "a declined round returns only a session at work on the deployed change");
    chart
        .onMatching(
            ChartRegions.LIFECYCLE,
            IN_PROGRESS,
            TaskMove.Checked.RoundDeclined.class,
            id() + " round declined",
            this::declinesRound)
        .when((active, move) ->
            active.state(region()) == CheckerPhase.ROUND_OPEN
                && active.state(ChartRegions.TURN) == TurnKind.OPEN)
        .emit(new TaskEffect.DeclinesRound(id()))
        .to(DEPLOYED);
  }

  private boolean declinesRound(TaskMove move) {
    return move instanceof TaskMove.Checked.RoundDeclined declined
        && id().equals(declined.role())
        && declined.means() == AgentReport.NO_CHANGES
        && declined.reported() == IN_PROGRESS;
  }

  private boolean endsTurn(TaskMove move) {
    return move instanceof TaskMove.Relayed
        || move instanceof TaskMove.TurnClosed
        || move instanceof TaskMove.TurnRestored
        || (move instanceof TaskMove.Checked.ReadEnded ended && id().equals(ended.role()));
  }

  private boolean ownsAndStands(String role, boolean stands) {
    return id().equals(role) && stands;
  }

  private static TaskEffect passes(TaskMove move) {
    TaskMove.Checked.Passed passed = (TaskMove.Checked.Passed) move;
    return new TaskEffect.PassesChecker(passed.role(), passed.sha());
  }

  private static TaskEffect sendsRound(TaskMove move) {
    TaskMove.Checked.RoundSent sent = (TaskMove.Checked.RoundSent) move;
    return new TaskEffect.SendsCheckerRound(sent.role(), sent.digest(), sent.rows());
  }
}
