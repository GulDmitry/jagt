package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

class MasterTurnEndNoteTest {

  private final ConfigService config = mock(ConfigService.class);

  @Test
  void saysTheRoundIsWithTheMasterWhereItReadsTheHandedBackRound() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withArbiter(new ConfigFile.ArbiterConfig("act", null, null, null, null)));

    assertThat(
            new MasterTurnEndNote(config)
                .noteFor(
                    TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                        .alias("a1")
                        .build()))
        .contains("→ with the Master for review · its verdict arrives here");
  }

  @Test
  void saysVerificationRunsFirstWhereTheRoundIsBeingVerified() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withArbiter(new ConfigFile.ArbiterConfig("act", null, null, null, null)));

    assertThat(
            new MasterTurnEndNote(config)
                .noteFor(
                    TaskState.builder("proj", "/wt", TaskStatus.VERIFYING).alias("a1").build()))
        .contains("→ verification runs first · the Master reads the round once it passes");
  }

  @Test
  void saysNothingWhereNoMasterReads() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withArbiter(new ConfigFile.ArbiterConfig("off", null, null, null, null)));

    assertThat(
            new MasterTurnEndNote(config)
                .noteFor(
                    TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                        .alias("a1")
                        .build()))
        .isEmpty();
  }
}
