package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StopAnnouncements;
import dev.jagt.orchestrator.service.StopLog;
import dev.jagt.orchestrator.service.StopPolicy;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.triage.StopTriagePanel;
import java.time.Clock;
import java.util.Map;
import lombok.With;
import tools.jackson.databind.json.JsonMapper;

@With
public record TestStopTriageJobBuilder(Arbiter.Profile masterProfile, boolean triage) {

  public static TestStopTriageJobBuilder stopTriageJob() {
    return new TestStopTriageJobBuilder(Arbiter.Profile.OFF, true);
  }

  public ConfigService.ConfigFile config() {
    return ConfigService.ConfigFile.defaults()
        .withRoles(Map.of("triage", new ConfigService.ConfigFile.RoleConfig(triage, null)));
  }

  public StopTriageJob build(
      ConfigService configService,
      StopTriagePanel panel,
      StateService state,
      OrchestratorPaths paths,
      Clock clock) {
    return new StopTriageJob(
        configService,
        ignored -> masterProfile,
        panel,
        new TaskStops(
            new StopPolicy(
                ignored -> masterProfile,
                ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                new StopAnnouncements(new StopLog(new JsonMapper(), paths))),
            state,
            clock,
            new FlowRules(new TaskChart(Composition.builtIn()))));
  }
}
