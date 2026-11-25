package dev.jagt.orchestrator.job.review;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.Verification;
import dev.jagt.orchestrator.service.log.StopLog;
import dev.jagt.orchestrator.service.report.AgentStatusReports;
import dev.jagt.orchestrator.service.stop.StopAnnouncements;
import dev.jagt.orchestrator.service.stop.StopPolicy;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.With;
import tools.jackson.databind.json.JsonMapper;

@With
public record TestVerifyJobBuilder(Path root) {

  public static TestVerifyJobBuilder verifyJob() {
    return new TestVerifyJobBuilder(Path.of("/state"));
  }

  public VerifyJob build(
      StateService state,
      Verification verification,
      AgentStatusReports statusReports,
      StepRelay relay) {
    OrchestratorPaths paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withData(root.toString())
                .withStateFile(root.resolve("state.json").toString()),
            FakeHost.machine());
    return new VerifyJob(
        state,
        verification,
        statusReports,
        relay,
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(new StopLog(new JsonMapper(), paths))),
            state,
            Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC),
            new FlowRules(new TaskChart(Composition.builtIn()))));
  }
}
