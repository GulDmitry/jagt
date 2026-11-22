package dev.jagt.orchestrator.job.conflict;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import java.time.Clock;
import lombok.With;

@With
public record TestConflictStallBuilder(String masterMode) {

  public static TestConflictStallBuilder conflictStall() {
    return new TestConflictStallBuilder("off");
  }

  public ConfigFile config() {
    return ConfigFile.defaults()
        .withArbiter(new ConfigFile.ArbiterConfig(masterMode, null, null, null, null));
  }

  public ConflictStall build(StateService state, ConfigService config, Clock clock) {
    return new ConflictStall(
        state, config, clock, new Checkers(new TaskChart(Composition.builtIn())));
  }
}
