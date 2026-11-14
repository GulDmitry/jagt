package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.service.TestCaseDoorBuilder.caseDoor;
import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static dev.jagt.orchestrator.service.stop.TestTaskStopsBuilder.taskStops;

import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import java.nio.file.Path;
import java.time.Clock;
import lombok.With;

@With
public record TestStepRelayBuilder(Path root) {

  public static TestStepRelayBuilder stepRelay() {
    return new TestStepRelayBuilder(Path.of("/state"));
  }

  public StepRelay build(StateService state, AgentSessions sessions, Clock clock) {
    return new StepRelay(
        new SessionBrief(orchestratorPaths().withRoot(root).build()),
        sessions,
        caseDoor()
            .withRoot(root)
            .build(state, taskStops().withRoot(root).build(state, clock), clock),
        new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn()))));
  }
}
