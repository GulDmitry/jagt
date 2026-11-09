package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.task.Conflict;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StagedFixReadTest {

  private final ConfigService config = mock(ConfigService.class);
  private final DeployConflicts conflicts = mock(DeployConflicts.class);
  private final ConflictHandOff handOff = mock(ConflictHandOff.class);

  @Test
  void handsTheWaitingConflictOnWhereTheMasterActs() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).build();
    DeployConflicts.WaitingConflict conflict =
        new DeployConflicts.WaitingConflict(
            Path.of("/src/ABC-42-deploy"),
            false,
            new Conflict.Progress(7, 0, "d"),
            null,
            false,
            Standing.ABSENT);
    when(config.load())
        .thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")));
    when(conflicts.waiting("ABC-42", task)).thenReturn(Optional.of(conflict));

    boolean changed = new StagedFixRead(config, conflicts, handOff).read("ABC-42", task);

    verify(handOff).handle("ABC-42", conflict);
    assertThat(changed).isTrue();
  }

  @Test
  void changesNothingWhereNoWorktreeHoldsTheConflict() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).build();
    when(config.load())
        .thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")));
    when(conflicts.waiting("ABC-42", task)).thenReturn(Optional.empty());

    boolean changed = new StagedFixRead(config, conflicts, handOff).read("ABC-42", task);

    assertThat(changed).isFalse();
  }

  @Test
  void leavesTheConflictToTheHumanWhereTheMasterOnlyJudges() {
    when(config.load())
        .thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("judge")));

    boolean changed =
        new StagedFixRead(config, conflicts, handOff)
            .read("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).build());

    verifyNoInteractions(conflicts, handOff);
    assertThat(changed).isFalse();
  }
}
