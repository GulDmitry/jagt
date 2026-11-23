package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.statechart.RegionId;
import dev.jagt.statechart.StatechartBuilder;

record MasterRegion() implements Role {

  static final RegionId<MasterPhase> REGION = new RegionId<>(Arbiter.ID, MasterPhase.class);

  @Override
  public String id() {
    return Arbiter.ID;
  }

  @Override
  public Kind kind() {
    return Kind.ARBITER;
  }

  @Override
  public void declareRegion(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, Landing landing) {
    chart.region(REGION, MasterPhase.STANDS_IN);
    for (MasterPhase from : MasterPhase.values()) {
      for (MasterPhase to : MasterPhase.values()) {
        ChartRegions.regionEdge(
            chart,
            REGION,
            from,
            "noted, " + to + " after",
            move -> move instanceof TaskMove.MasterNoted noted && noted.after() == to,
            move -> new TaskEffect.NotesMaster(((TaskMove.MasterNoted) move).note()),
            to);
      }
    }
  }

  @Override
  public void declareBeneath(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {}

  @Override
  public void declareTurnEdge(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TurnKind from) {}

  @Override
  public void declareLifecycleEdge(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {}
}
