package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.statechart.StatechartBuilder;

interface Role {

  enum Kind {
    ARBITER,
    CHECKER
  }

  String id();

  Kind kind();

  void declareRegion(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, Landing landing);

  void declareBeneath(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart);

  void declareTurnEdge(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TurnKind from);

  void declareLifecycleEdge(StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart);
}
