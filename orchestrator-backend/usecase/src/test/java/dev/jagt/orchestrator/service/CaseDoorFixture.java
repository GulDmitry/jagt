package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.notify.Notifications;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

public final class CaseDoorFixture {

  public final OrchestratorPaths paths;
  public final StateService state;
  public final CaseDoor door;

  public CaseDoorFixture(Path root, Clock clock, Arbiter.Profile profile) {
    paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    state =
        new StateService(
            new JsonMapper(),
            paths,
            new TaskReducer(new TaskChart(Composition.builtIn())),
            event -> {});
    door =
        new CaseDoor(
            new SessionCases(paths),
            new MasterCases(paths),
            state,
            new MissLog(new JsonMapper(), paths, clock),
            new TaskStops(
                new StopPolicy(
                    ignored -> profile,
                    ConfigService.ConfigFile.defaults()::loopBreakerOrDefault,
                    new StopAnnouncements(
                        new Notifications(List.of(), state, Runnable::run),
                        new StopLog(new JsonMapper(), paths))),
                state,
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));
  }
}
