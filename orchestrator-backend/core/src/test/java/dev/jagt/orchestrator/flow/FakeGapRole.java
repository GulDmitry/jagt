package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.statechart.RegionId;
import dev.jagt.statechart.StatechartBuilder;

final class FakeGapRole implements Role {

  static final RegionId<CheckerPhase> REGION = new RegionId<>("gap", CheckerPhase.class);

  @Override
  public String id() {
    return "gap";
  }

  @Override
  public Kind kind() {
    return Kind.CHECKER;
  }

  @Override
  public void declareRegion(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, Landing landing) {
    chart.region(REGION, CheckerPhase.NONE);
    chart.addressed(TaskMove.Verified.class, REGION);
    chart
        .onMatching(REGION, CheckerPhase.NONE, TaskMove.Verified.class, "verified", move -> true)
        .to(CheckerPhase.READING);
  }

  @Override
  public void declareBeneath(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {}

  @Override
  public void declareTurnEdge(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TurnKind from) {}

  @Override
  public void declareLifecycleEdge(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {}
}
