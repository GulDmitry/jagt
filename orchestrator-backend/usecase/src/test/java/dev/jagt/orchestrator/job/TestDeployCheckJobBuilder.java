package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestFlowReportsBuilder.flowReports;
import static dev.jagt.orchestrator.service.TestTaskStopsBuilder.taskStops;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.With;

@With
public record TestDeployCheckJobBuilder(Path root) {

  public static TestDeployCheckJobBuilder deployCheckJob() {
    return new TestDeployCheckJobBuilder(Path.of("/state"));
  }

  public DeployCheckJob build(
      StateService state, ConfigService config, StepRelay relay, DeployPipelines pipelines) {
    Clock clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);
    return new DeployCheckJob(
        config,
        relay,
        pipelines,
        flowReports().build(state, clock),
        taskStops().withRoot(root).build(state, clock));
  }
}
