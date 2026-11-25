package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.port.channel.NotificationSink;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import lombok.With;

@With
public record TestPointWatchBuilder(Path root, Arbiter.Profile profile) {

  public static TestPointWatchBuilder pointWatch() {
    return new TestPointWatchBuilder(Path.of("/state"), Arbiter.Profile.OFF);
  }

  public PointWatch build(StateService state, NotificationSink sink) {
    return new PointWatch(
        state,
        sink,
        new InstalledRoles(
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withData(root.toString()),
                FakeHost.machine())),
        ignored -> profile);
  }
}
