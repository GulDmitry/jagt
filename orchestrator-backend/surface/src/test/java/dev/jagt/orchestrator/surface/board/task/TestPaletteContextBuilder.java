package dev.jagt.orchestrator.surface.board.task;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.review.Rounds;
import dev.jagt.orchestrator.service.task.TaskViews;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.With;

@With
public record TestPaletteContextBuilder() {

  public static TestPaletteContextBuilder paletteContext() {
    return new TestPaletteContextBuilder();
  }

  public PaletteContext build(StateService state, ConfigService config) {
    return new PaletteContext(
        state,
        new TaskViews(
            state,
            config,
            new Rounds(
                config,
                ignored -> false,
                () -> false,
                () -> false,
                new Checkers(new TaskChart(Composition.builtIn()))),
            Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC),
            new FlowRules(new TaskChart(Composition.builtIn()))));
  }
}
