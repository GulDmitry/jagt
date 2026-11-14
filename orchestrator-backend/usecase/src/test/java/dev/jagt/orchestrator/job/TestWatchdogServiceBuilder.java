package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.StateService;
import java.nio.file.Path;
import java.time.Clock;
import lombok.With;

@With
public record TestWatchdogServiceBuilder(Path root) {

  public static TestWatchdogServiceBuilder watchdogService() {
    return new TestWatchdogServiceBuilder(Path.of("/state"));
  }

  public OrchestratorProperties properties() {
    return OrchestratorProperties.defaults()
        .withRoot(root.toString())
        .withStateFile(root.resolve("state.json").toString());
  }

  public WatchdogService build(StateService state, SessionProbe probe, Clock clock) {
    return new WatchdogService(state, properties(), probe, clock);
  }
}
