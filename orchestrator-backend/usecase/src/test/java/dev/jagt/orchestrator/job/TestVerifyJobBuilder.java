package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.StopPolicy;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.Verification;
import java.nio.file.Path;
import java.time.Clock;
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
                .withStateFile(root.resolve("state.json").toString()));
    return new VerifyJob(
        state,
        verification,
        statusReports,
        relay,
        new TaskStops(
            new StopPolicy(
                ignored -> Arbiter.Profile.OFF,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(new StopLog(new JsonMapper(), paths))),
            state,
            Clock.systemUTC(),
            new FlowRules(new TaskChart(Composition.builtIn()))));
  }
}
