package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestTaskStopsBuilder.taskStops;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import java.nio.file.Path;
import java.time.Clock;
import lombok.With;

@With
public record TestReportEffectsBuilder(
    Path root, Clock clock, InfraWaitProperties infraWait, SessionLines sessions) {

  public static TestReportEffectsBuilder reportEffects() {
    return new TestReportEffectsBuilder(
        Path.of("/state"), Clock.systemUTC(), InfraWaitProperties.defaults(), (task, line) -> "");
  }

  public ReportEffects build(StateService state) {
    return build(
        state,
        taskStops().withRoot(root).build(state, clock),
        new LinkRefusals(new ReachLedger(), sessions, state));
  }

  public ReportEffects build(StateService state, TaskStops stops, LinkRefusals links) {
    TaskTurns turns =
        new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn())));
    return new ReportEffects(new SessionWakes(state, turns, clock), turns, stops, infraWait, links);
  }
}
