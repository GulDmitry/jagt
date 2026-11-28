package dev.jagt.orchestrator.job.session;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.presence.SessionProbe;
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
        .withData(root.toString())
        .withStateFile(root.resolve("state.json").toString());
  }

  public WatchdogService build(
      StateService state, SessionProbe probe, SilentWake wake, Clock clock) {
    return new WatchdogService(state, properties(), probe, wake, clock);
  }
}
