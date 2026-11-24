package dev.jagt.orchestrator.job.master;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterStopAnswer;
import dev.jagt.orchestrator.service.master.MasterStopPanel;
import dev.jagt.orchestrator.service.review.Rounds;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestMasterStopJobBuilder(
    String mode,
    @Nullable List<String> mine,
    TaskStatus status,
    TaskStop stop,
    Master master,
    MasterStopAnswer.Kind kind,
    String advice) {

  public static TestMasterStopJobBuilder masterStopJob() {
    return new TestMasterStopJobBuilder(
        "act",
        null,
        TaskStatus.IN_PROGRESS,
        new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds"),
        Master.defaults(),
        MasterStopAnswer.Kind.LIFT,
        "each round answered a new report");
  }

  public ConfigFile config() {
    return ConfigFile.defaults()
        .withArbiter(new ConfigFile.ArbiterConfig(mode, null, null, mine, null));
  }

  public TaskState task() {
    return TaskState.builder("proj", "/wt", status).stop(stop).master(master).build();
  }

  public Optional<MasterStopAnswer> answer() {
    return Optional.of(new MasterStopAnswer(kind, advice));
  }

  public MasterStopJob build(
      ConfigService configService,
      MasterStopPanel panel,
      MasterStopMoves moves,
      StateService state,
      Clock clock) {
    return new MasterStopJob(
        configService,
        panel,
        moves,
        new TaskTurns(state, clock, new Checkers(new TaskChart(Composition.builtIn()))),
        new Rounds(
            configService,
            ignored -> false,
            () -> Facts.Reach.defaults(),
            unheld -> null,
            new Checkers(new TaskChart(Composition.builtIn()))));
  }
}
