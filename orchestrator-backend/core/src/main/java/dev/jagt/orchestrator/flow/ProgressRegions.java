package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.flow.ChartRegions.ALWAYS;
import static dev.jagt.orchestrator.flow.ChartRegions.ASK;
import static dev.jagt.orchestrator.flow.ChartRegions.LIFECYCLE;
import static dev.jagt.orchestrator.flow.ChartRegions.PLAN;
import static dev.jagt.orchestrator.flow.ChartRegions.REPORT;
import static dev.jagt.orchestrator.flow.ChartRegions.SESSION;
import static dev.jagt.orchestrator.flow.ChartRegions.STOP;
import static dev.jagt.orchestrator.flow.ChartRegions.TURN;
import static dev.jagt.orchestrator.flow.StatusSets.HELD;
import static dev.jagt.orchestrator.flow.StatusSets.STANDING_THROUGH_A_READ;

import dev.jagt.orchestrator.flow.session.AskStamp;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.statechart.Guard;
import dev.jagt.statechart.StatechartBuilder;
import dev.jagt.statechart.TransitionBuilder;
import java.util.EnumSet;
import java.util.function.Predicate;

/** The regions of what a task has been through: stop, plan, report, turn and session. */
final class ProgressRegions {

  private final Composition composition;
  private final Landing land;
  private final Predicate<TaskStatus> planMayComeFirst;

  ProgressRegions(Composition composition, Landing land, Predicate<TaskStatus> planMayComeFirst) {
    this.composition = composition;
    this.land = land;
    this.planMayComeFirst = planMayComeFirst;
  }

  void stops(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(STOP, StopKind.NONE);
    chart
        .onMatching(STOP, StopKind.NONE, "lift", new TaskMove.Lifted()::equals)
        .emit(new TaskEffect.Lifts())
        .to(StopKind.NONE);
    for (TaskStop.Trigger.Ends ends : TaskStop.Trigger.Ends.values()) {
      StopKind kind = StopKind.of(ends);
      chart
          .onMatching(
              STOP,
              StopKind.NONE,
              "stop ending on " + ends,
              move -> move instanceof TaskMove.StopSet set && set.stop().trigger().ends() == ends)
          .emitFor(move -> new TaskEffect.Stops(((TaskMove.StopSet) move).stop()))
          .to(kind);
      chart
          .onMatching(STOP, kind, "lift", new TaskMove.Lifted()::equals)
          .emit(new TaskEffect.Lifts())
          .to(StopKind.NONE);
      if (ends == TaskStop.Trigger.Ends.LIFT) {
        chart
            .onMatching(STOP, kind, "escalate", new TaskMove.Escalated()::equals)
            .emit(new TaskEffect.Escalates())
            .to(StopKind.NONE);
      }
      if (ends == TaskStop.Trigger.Ends.PAUSE) {
        continue;
      }
      chart
          .onMatching(STOP, kind, ends + " ends it", new TaskMove.StopEnded(ends)::equals)
          .emit(new TaskEffect.ClearsStop())
          .to(StopKind.NONE);
      chart
          .onMatching(STOP, kind, "a new request voids it", new TaskMove.RequestReplaced()::equals)
          .emit(new TaskEffect.ClearsStop())
          .to(StopKind.NONE);
    }
  }

  void plans(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(PLAN, PlanGate.OPEN);
    chart
        .onMatching(PLAN, PlanGate.OPEN, "autoplan", new TaskMove.Autoplanned()::equals)
        .emit(new TaskEffect.GatesPlan(PlanGate.CLOSED))
        .to(PlanGate.CLOSED);
    for (PlanGate from : EnumSet.of(PlanGate.OPEN, PlanGate.CLOSED)) {
      chart
          .onMatching(
              PLAN,
              from,
              "plan mode",
              move -> move instanceof TaskMove.PlanAsked asked && asked.planMode())
          .when((active, move) -> planMayComeFirst.test(active.state(LIFECYCLE)))
          .emit(new TaskEffect.GatesPlan(PlanGate.REQUIRED))
          .to(PlanGate.REQUIRED);
    }
    chart
        .onMatching(
            PLAN, PlanGate.CLOSED, "no plan declared", new TaskMove.NoPlanDeclared()::equals)
        .emit(new TaskEffect.GatesPlan(PlanGate.OPEN))
        .to(PlanGate.OPEN);
    for (PlanGate from : EnumSet.of(PlanGate.CLOSED, PlanGate.REQUIRED)) {
      chart
          .onMatching(PLAN, from, "plan approved", new TaskMove.PlanApproved()::equals)
          .emit(new TaskEffect.GatesPlan(PlanGate.OPEN))
          .to(PlanGate.OPEN);
    }
  }

  void says(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(REPORT, AgentReport.PLAIN);
    for (AgentReport from : AgentReport.values()) {
      said(
          chart,
          from,
          "said on a status that holds its line",
          from.asks() ? AgentReport.PLAIN : from,
          move -> move instanceof TaskMove.Said said && !said.means().asks(),
          (active, move) -> HELD.contains(active.state(LIFECYCLE)));
      becomes(chart, from);
      if (from != AgentReport.PLAIN) {
        clears(chart, from);
      }
    }
  }

  void turns(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(TURN, TurnKind.CLOSED);
    for (TurnKind from : TurnKind.values()) {
      relayed(chart, from);
      TransitionBuilder<TaskMove, ChartFacts, TaskEffect, TurnKind> closed =
          chart
              .onMatching(TURN, from, "turn closed", new TaskMove.TurnClosed()::equals)
              .emit(new TaskEffect.ClosesTurn());
      if (from == TurnKind.CLOSED) {
        closed.internal();
      } else {
        closed.to(TurnKind.CLOSED);
      }
      composition.roles().forEach(role -> role.declareTurnEdge(chart, from));
      for (TurnKind restored : TurnKind.values()) {
        chart
            .onMatching(
                TURN,
                from,
                "turn restored " + restored,
                move ->
                    move instanceof TaskMove.TurnRestored undone
                        && TurnKind.of(undone.before()) == restored)
            .emitFor(move -> new TaskEffect.RestoresTurn(((TaskMove.TurnRestored) move).before()))
            .to(restored);
      }
    }
  }

  void sessions(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(SESSION, SessionPhase.NONE);
    chart.datum(ASK, facts -> facts.stampedAsk() ? AskStamp.STAMPED : AskStamp.NONE);
    composition.roles().forEach(role -> role.declareBeneath(chart));
    EnumSet<SessionPhase> turnsEnd =
        EnumSet.of(SessionPhase.NONE, SessionPhase.WORKING, SessionPhase.IDLE, SessionPhase.QUIET);
    for (SessionPhase from : SessionPhase.values()) {
      startedOver(chart, from);
      chart
          .onMatching(
              SESSION,
              from,
              "work started clearing a stamped permission",
              move ->
                  move instanceof TaskMove.Signed signed
                      && signed.sign() instanceof SessionSign.WorkStarted)
          .when((active, move) ->
              active.state(REPORT) == AgentReport.PERMISSION
                  && active.datum(ASK) == AskStamp.STAMPED)
          .emitFor(move -> new TaskEffect.Signs(((TaskMove.Signed) move).sign()))
          .raise(new TaskMove.Stamped(AgentReport.PLAIN))
          .to(SessionPhase.WORKING);
      signed(chart, from, "work started", SessionPhase.WORKING, SessionSign.WorkStarted.class);
      chart
          .onMatching(
              SESSION,
              from,
              "asked",
              move ->
                  move instanceof TaskMove.Signed signed
                      && signed.sign() instanceof SessionSign.Asked)
          .when((active, move) -> active.state(REPORT) == AgentReport.PLAIN)
          .emitFor(move -> new TaskEffect.Signs(((TaskMove.Signed) move).sign()))
          .raise(new TaskMove.Stamped(AgentReport.PERMISSION))
          .internal();
      signed(chart, from, "called", SessionPhase.WORKING, SessionSign.Called.class);
      signed(chart, from, "tool used", SessionPhase.WORKING, SessionSign.ToolUsed.class);
      signed(chart, from, "reported", from, SessionSign.Reported.class);
      signed(chart, from, "log named", from, SessionSign.LogNamed.class);
      read(chart, from, "read", turnsEnd.contains(from) ? SessionPhase.QUIET : from, false);
      read(chart, from, "read found no process", SessionPhase.GONE, true);
      signed(chart, from, "ended", SessionPhase.GONE, SessionSign.Ended.class);
      signed(chart, from, "restarted", SessionPhase.IDLE, SessionSign.Restarted.class);
      if (turnsEnd.contains(from)) {
        signed(chart, from, "turn ended", SessionPhase.IDLE, SessionSign.TurnEnded.class);
      }
      if (turnsEnd.contains(from) || from == SessionPhase.FAILED) {
        signed(chart, from, "turn failed", SessionPhase.FAILED, SessionSign.TurnFailed.class);
      }
    }
  }

  private void startedOver(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, SessionPhase from) {
    TransitionBuilder<TaskMove, ChartFacts, TaskEffect, SessionPhase> over =
        chart
            .onMatching(SESSION, from, "started over", new TaskMove.SessionStartedOver()::equals)
            .emit(new TaskEffect.RestartsSession());
    if (from == SessionPhase.NONE) {
      over.internal();
    } else {
      over.to(SessionPhase.NONE);
    }
  }

  private void read(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
      SessionPhase from,
      String label,
      SessionPhase to,
      boolean processGone) {
    TransitionBuilder<TaskMove, ChartFacts, TaskEffect, SessionPhase> edge =
        chart
            .onMatching(
                SESSION,
                from,
                label,
                move ->
                    move instanceof TaskMove.Signed signed
                        && signed.sign() instanceof SessionSign.Read read
                        && read.processGone() == processGone)
            .emitFor(move -> new TaskEffect.Signs(((TaskMove.Signed) move).sign()));
    if (from == to) {
      edge.internal();
    } else {
      edge.to(to);
    }
  }

  private void signed(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
      SessionPhase from,
      String label,
      SessionPhase to,
      Class<? extends SessionSign> kind) {
    TransitionBuilder<TaskMove, ChartFacts, TaskEffect, SessionPhase> edge =
        chart
            .onMatching(
                SESSION,
                from,
                label,
                move -> move instanceof TaskMove.Signed signed && kind.isInstance(signed.sign()))
            .emitFor(move -> new TaskEffect.Signs(((TaskMove.Signed) move).sign()));
    if (from == to) {
      edge.internal();
    } else {
      edge.to(to);
    }
  }

  private void relayed(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TurnKind from) {
    TransitionBuilder<TaskMove, ChartFacts, TaskEffect, TurnKind> relayed =
        chart
            .onMatching(TURN, from, "relayed", move -> move instanceof TaskMove.Relayed)
            .emitFor(move ->
                new TaskEffect.OpensTurn(
                    ((TaskMove.Relayed) move).step(),
                    ((TaskMove.Relayed) move).digest(),
                    ((TaskMove.Relayed) move).answered()));
    if (from == TurnKind.OPEN) {
      relayed.internal();
    } else {
      relayed.to(TurnKind.OPEN);
    }
  }

  private void becomes(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, AgentReport from) {
    for (AgentReport means : AgentReport.values()) {
      if (means != from) {
        said(
            chart,
            from,
            "said " + means,
            means,
            move -> move instanceof TaskMove.Said said && said.means() == means,
            ALWAYS);
        said(
            chart,
            from,
            "stamped " + means,
            means,
            move -> move instanceof TaskMove.Stamped stamped && stamped.means() == means,
            ALWAYS);
      }
    }
  }

  private void clears(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, AgentReport from) {
    said(
        chart,
        from,
        "verified",
        AgentReport.PLAIN,
        move -> move instanceof TaskMove.Verified,
        ALWAYS);
    said(
        chart,
        from,
        "deploy read moving the status",
        AgentReport.PLAIN,
        move -> move instanceof TaskMove.DeployRead,
        (active, move) -> land.land(active.state(LIFECYCLE), move).isPresent());
    if (!STANDING_THROUGH_A_READ.contains(from)) {
      said(chart, from, "read", AgentReport.PLAIN, move -> move instanceof TaskMove.Read, ALWAYS);
    }
  }

  private void said(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
      AgentReport from,
      String label,
      AgentReport to,
      Predicate<TaskMove> matches,
      Guard<TaskMove> when) {
    TransitionBuilder<TaskMove, ChartFacts, TaskEffect, AgentReport> edge =
        chart.onMatching(REPORT, from, label, matches).when(when).emit(new TaskEffect.Reports(to));
    if (from == to) {
      edge.internal();
    } else {
      edge.to(to);
    }
  }
}
