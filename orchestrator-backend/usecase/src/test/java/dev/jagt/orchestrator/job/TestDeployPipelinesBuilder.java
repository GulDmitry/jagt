package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.port.CodeHost;
import dev.jagt.orchestrator.service.RedCauses;
import dev.jagt.orchestrator.service.StateService;
import java.time.Clock;

public record TestDeployPipelinesBuilder() {

  public static TestDeployPipelinesBuilder deployPipelines() {
    return new TestDeployPipelinesBuilder();
  }

  public DeployPipelines build(
      CodeHost pipelines,
      RedCauses causes,
      DeployFixWorktrees fixes,
      StateService state,
      Clock clock) {
    return new DeployPipelines(
        pipelines,
        causes,
        fixes,
        clock,
        new FlowReports(
            state,
            clock,
            new Checkers(new TaskChart(Composition.builtIn())),
            new FlowRules(new TaskChart(Composition.builtIn()))));
  }
}
