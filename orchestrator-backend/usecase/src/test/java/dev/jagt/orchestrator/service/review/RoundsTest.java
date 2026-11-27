package dev.jagt.orchestrator.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class RoundsTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}, gap -> {});

  private final ConfigService configService = mock(ConfigService.class);
  private final Rounds rounds =
      new Rounds(
          configService,
          ignored -> false,
          new ReachOffers(() -> false, () -> false),
          unheld -> null,
          new Checkers(new TaskChart(Composition.builtIn())));

  @Test
  void theMasterReadsHandBackItHasNotReadYetWhileItRuns() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    var round =
        rounds.of(
            task,
            ConfigFile.defaults()
                .withArbiter(new ConfigFile.ArbiterConfig("judge", null, null, null, null)));

    assertThat(round.arbiterReading()).isTrue();
  }

  @Test
  void nobodyReadsHandBackBeforeTheHumanWhileTheMasterIsOff() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    var round =
        rounds.of(
            task,
            ConfigFile.defaults()
                .withArbiter(new ConfigFile.ArbiterConfig("off", null, null, null, null)));

    assertThat(round.arbiterReading()).isFalse();
  }

  @Test
  void theMasterNoLongerReadsHandBackItHasAlreadyJudged() {
    TaskState task =
        reducer.arbiterSealed(
            TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build(), "fingerprint", 0);

    var round =
        rounds.of(
            task,
            ConfigFile.defaults()
                .withArbiter(new ConfigFile.ArbiterConfig("judge", null, null, null, null)));

    assertThat(round.arbiterReading()).isFalse();
  }

  @Test
  void theMasterReadsTheHandBackAgainWhereTheVerdictFileIsGone() {
    TaskState task =
        reducer.arbiterVerdictGone(
            TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .master(Master.defaults().withSeal("fingerprint", 0))
                .build());

    var round =
        rounds.of(
            task,
            ConfigFile.defaults()
                .withArbiter(new ConfigFile.ArbiterConfig("judge", null, null, null, null)));

    assertThat(round.arbiterReading()).isTrue();
  }

  @Test
  void theMasterHasJudgedNothingWhileItIsOff() {
    TaskState task =
        reducer.arbiterSealed(
            TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build(), "fingerprint", 0);
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withArbiter(new ConfigFile.ArbiterConfig("off", null, null, null, null)));

    assertThat(rounds.judged(task)).isFalse();
  }

  @Test
  void offersTheDeployWhereTheWorkStandsPushedWithNoRequest() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    var round =
        new Rounds(
                configService,
                standing -> Objects.equals(standing, task),
                new ReachOffers(() -> false, () -> false),
                unheld -> null,
                new Checkers(new TaskChart(Composition.builtIn())))
            .of(task, ConfigFile.defaults());

    assertThat(round.requestless()).isTrue();
  }

  @Test
  void theMasterActsOnTaskItStandsInForAndOnNoneItLeft() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withArbiter(new ConfigFile.ArbiterConfig("act", null, null, null, null)));
    TaskState standsIn = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState left =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .master(Master.defaults().withLeft("deploy"))
            .build();

    assertThat(rounds.at(standsIn).running()).isTrue();
    assertThat(rounds.at(left).running()).isFalse();
  }

  @Test
  void roundCarriesWhatHoldsTheSessionBack() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.NEW).build();
    Rounds held =
        new Rounds(
            configService,
            ignored -> false,
            new ReachOffers(() -> false, () -> false),
            ignored -> "credential check fails",
            new Checkers(new TaskChart(Composition.builtIn())));

    assertThat(held.of(task, ConfigFile.defaults()).reach().sessionHeld())
        .isEqualTo("credential check fails");
  }
}
