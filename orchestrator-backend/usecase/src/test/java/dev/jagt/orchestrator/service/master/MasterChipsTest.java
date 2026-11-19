package dev.jagt.orchestrator.service.master;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.service.role.HeaderChips.HeaderChip;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.service.stop.ActStop;
import dev.jagt.orchestrator.service.stop.ActStop.Cause;
import dev.jagt.orchestrator.service.stop.ActStops;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterChipsTest {

  @TempDir Path root;

  private final ConfigService config = mock(ConfigService.class);
  private final ActStops stops = mock(ActStops.class);
  private MasterChips chips;

  @BeforeEach
  void masterInstalledOverTheTempRoot() throws Exception {
    Files.createDirectories(root.resolve("roles/master"));
    Files.writeString(root.resolve("roles/master/brief.md"), "brief");
    chips =
        new MasterChips(
            new InstalledRoles(orchestratorPaths().withRoot(root).build()), config, stops);
    when(stops.standing()).thenReturn(Optional.empty());
  }

  @Test
  void saysNothingWhenTheRoleIsNotInstalled(@TempDir Path bare) {
    var absent =
        new MasterChips(
            new InstalledRoles(orchestratorPaths().withRoot(bare).build()), config, stops);

    assertThat(absent.chips()).isEmpty();
  }

  @Test
  void saysTheMasterActsWhenItsModeIsAct() {
    when(config.load())
        .thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("act")));

    assertThat(chips.chips()).containsExactly(new HeaderChip("header.acting", null, false, null));
  }

  @Test
  void saysNothingWhenTheMasterOnlyJudges() {
    when(config.load())
        .thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("judge")));

    assertThat(chips.chips()).isEmpty();
  }

  @Test
  void offersActBackAsBrokenWhenThreeBadEndingsStoppedIt() {
    when(stops.standing()).thenReturn(Optional.of(new ActStop(Cause.STRIKES, null)));

    assertThat(chips.chips()).containsExactly(new HeaderChip("act-stopped", "act", true, null));
  }

  @Test
  void leavesTheBoardStopToTheBoardWhenOnlyTheBoardIsStopped() {
    when(config.load())
        .thenReturn(ConfigFile.defaults().withMaster(MasterConfig.defaults().withMode("judge")));
    when(stops.standing()).thenReturn(Optional.of(new ActStop(Cause.BOARD, 5L)));

    assertThat(chips.chips()).isEmpty();
  }

  @Test
  void saysNothingWhenTheConfigCannotBeRead() {
    when(config.load()).thenThrow(new IllegalStateException("half-saved"));

    assertThat(chips.chips()).isEmpty();
  }
}
