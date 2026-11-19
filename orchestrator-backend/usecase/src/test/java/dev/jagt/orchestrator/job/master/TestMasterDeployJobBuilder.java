package dev.jagt.orchestrator.job.master;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import java.time.Clock;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestMasterDeployJobBuilder(String mode, @Nullable List<String> mine) {

  public static TestMasterDeployJobBuilder masterDeployJob() {
    return new TestMasterDeployJobBuilder("act", null);
  }

  public ConfigService.ConfigFile config() {
    return ConfigService.ConfigFile.defaults()
        .withArbiter(new ConfigService.ConfigFile.ArbiterConfig(mode, null, null, mine, null));
  }

  public MasterDeployJob build(
      ConfigService configService, MasterPress press, StateService state, Clock clock) {
    return new MasterDeployJob(
        configService,
        press,
        new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn()))));
  }
}
