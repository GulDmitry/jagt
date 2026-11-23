package dev.jagt.orchestrator.service.report;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import lombok.With;

@With
public record TestCheckersBuilder() {

  public static TestCheckersBuilder checkers() {
    return new TestCheckersBuilder();
  }

  public Checkers build() {
    return new Checkers(new TaskChart(Composition.builtIn()));
  }
}
