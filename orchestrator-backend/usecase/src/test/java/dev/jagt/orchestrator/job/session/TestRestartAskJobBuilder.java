package dev.jagt.orchestrator.job.session;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;

import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.turn.StepRelay;
import java.nio.file.Path;
import java.time.Clock;
import lombok.With;

@With
public record TestRestartAskJobBuilder(Path root) {

  public static TestRestartAskJobBuilder restartAskJob() {
    return new TestRestartAskJobBuilder(Path.of("/state"));
  }

  public RestartAskJob build(
      AgentSessions sessions, AgentPresence presence, StepRelay relay, Clock clock) {
    return new RestartAskJob(
        sessions,
        presence,
        relay,
        new AliveStamp(orchestratorPaths().withRoot(root).build(), clock));
  }
}
