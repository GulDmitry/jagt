package dev.jagt.orchestrator.job.verdict;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestMasterAnswerJobBuilder(@Nullable List<String> mine) {

  public static TestMasterAnswerJobBuilder masterAnswerJob() {
    return new TestMasterAnswerJobBuilder(null);
  }

  public ConfigService.ConfigFile config() {
    return ConfigService.ConfigFile.defaults()
        .withArbiter(new ConfigService.ConfigFile.ArbiterConfig("act", null, null, mine, null));
  }

  public MasterAnswerJob build(
      StateService state, ConfigService config, MasterPanel panel, MasterVerdicts verdicts) {
    return new MasterAnswerJob(
        state,
        config,
        panel,
        verdicts,
        new TaskTurns(
            state,
            Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC),
            new Checkers(new TaskChart(Composition.builtIn()))));
  }
}
