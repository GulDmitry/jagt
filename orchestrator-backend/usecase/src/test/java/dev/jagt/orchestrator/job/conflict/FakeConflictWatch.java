package dev.jagt.orchestrator.job.conflict;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.job.deploy.DeployConflicts.WaitingConflict;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.deploy.Conflict.Progress;
import java.nio.file.Path;
import java.time.Clock;

/** The hand-off of a waiting deploy conflict, which no one outside its package can build. */
public final class FakeConflictWatch {

  private final StepRelay relay;
  private final ConflictHandOff handOff;

  public FakeConflictWatch(
      StateService state,
      ConfigService config,
      Clock clock,
      StepRelay relay,
      AgentSessions sessions,
      CommandService commands,
      TaskStops stops) {
    this.relay = relay;
    this.handOff =
        new ConflictHandOff(
            relay,
            new ConflictHandover(sessions, relay),
            new ConflictStall(
                state, config, clock, new Checkers(new TaskChart(Composition.builtIn()))),
            new ConflictFinish(commands, stops, state, clock));
  }

  public void waits(String taskId, Path worktree, Progress progress) {
    handOff.handle(
        taskId,
        new WaitingConflict(worktree, false, progress, null, false, relay.standing(taskId)));
  }
}
