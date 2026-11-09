package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.Master;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

class RoundsTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final Rounds rounds = new Rounds(configService, ignored -> false, () -> false);

  @Test
  void theMasterReadsAHandBackItHasNotReadYetWhileItRuns() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    var round =
        rounds.of(
            task,
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

    assertThat(round.masterReading()).isTrue();
  }

  @Test
  void nobodyReadsAHandBackBeforeTheHumanWhileTheMasterIsOff() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    var round =
        rounds.of(
            task,
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));

    assertThat(round.masterReading()).isFalse();
  }

  @Test
  void theMasterNoLongerReadsAHandBackItHasAlreadyJudged() {
    TaskState task =
        TaskReducer.masterSealed(
            TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build(), "fingerprint", 0);

    var round =
        rounds.of(
            task,
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

    assertThat(round.masterReading()).isFalse();
  }

  @Test
  void theMasterReadsTheHandBackAgainWhereTheVerdictFileIsGone() {
    TaskState task =
        TaskReducer.masterVerdictGone(
            TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .master(Master.defaults().withSeal("fingerprint", 0))
                .build());

    var round =
        rounds.of(
            task,
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

    assertThat(round.masterReading()).isTrue();
  }

  @Test
  void theMasterHasJudgedNothingWhileItIsOff() {
    TaskState task =
        TaskReducer.masterSealed(
            TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build(), "fingerprint", 0);
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));

    assertThat(rounds.judged(task)).isFalse();
  }

  @Test
  void offersTheDeployWhereTheWorkStandsPushedWithNoRequest() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

    var round =
        new Rounds(configService, standing -> standing == task, () -> false)
            .of(task, ConfigService.ConfigFile.defaults());

    assertThat(round.requestless()).isTrue();
  }

  @Test
  void theMasterActsOnATaskItStandsInForAndOnNoneItLeft() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
    TaskState standsIn = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();
    TaskState left =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .master(Master.defaults().withLeft("deploy"))
            .build();

    assertThat(rounds.at(standsIn).running()).isTrue();
    assertThat(rounds.at(left).running()).isFalse();
  }
}
