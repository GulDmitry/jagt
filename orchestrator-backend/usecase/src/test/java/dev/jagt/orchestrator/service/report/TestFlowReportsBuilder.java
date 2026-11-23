package dev.jagt.orchestrator.service.report;

import static dev.jagt.orchestrator.service.report.TestCheckersBuilder.checkers;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.testing.FakeClock;
import lombok.With;

@With
public record TestFlowReportsBuilder() {

  public static TestFlowReportsBuilder flowReports() {
    return new TestFlowReportsBuilder();
  }

  public FlowReports build(StateService state) {
    return new FlowReports(
        state,
        new FakeClock(),
        checkers().build(),
        new FlowRules(new TaskChart(Composition.builtIn())));
  }
}
