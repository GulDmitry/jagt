package dev.jagt.orchestrator.flow;

import dev.jagt.statechart.ChartBuilder;
import dev.jagt.statechart.RegionId;

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
  public void declareRegion(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
    chart.region(REGION, MasterPhase.STANDS_IN);
    for (MasterPhase from : MasterPhase.values()) {
      for (MasterPhase to : MasterPhase.values()) {
        TaskChart.regionEdge(
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
  public void declareBeneath(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {}

  @Override
  public void declareTurnEdge(
      ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TurnKind from) {}

  @Override
  public void declareLifecycleEdge(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {}
}
