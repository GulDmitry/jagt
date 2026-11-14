package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import java.time.Clock;

public record TestTaskTurnsBuilder() {

  public static TestTaskTurnsBuilder taskTurns() {
    return new TestTaskTurnsBuilder();
  }

  public TaskTurns build(StateService state, Clock clock) {
    return new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn())));
  }
}
