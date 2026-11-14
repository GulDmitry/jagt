package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.statechart.ChartBuilder;

interface Role {

  enum Kind {
    ARBITER,
    CHECKER
  }

  String id();

  Kind kind();

  void declareRegion(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, Landing landing);

  void declareBeneath(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart);

  void declareTurnEdge(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TurnKind from);

  void declareLifecycleEdge(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart);
}
