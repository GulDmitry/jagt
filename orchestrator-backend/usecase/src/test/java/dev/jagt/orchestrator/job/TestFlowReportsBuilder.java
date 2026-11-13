package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.StateService;
import java.time.Clock;

public record TestFlowReportsBuilder() {

  public static TestFlowReportsBuilder flowReports() {
    return new TestFlowReportsBuilder();
  }

  public FlowReports build(StateService state, Clock clock) {
    return new FlowReports(
        state,
        clock,
        new Checkers(new TaskChart(Composition.builtIn())),
        new FlowRules(new TaskChart(Composition.builtIn())));
  }
}
