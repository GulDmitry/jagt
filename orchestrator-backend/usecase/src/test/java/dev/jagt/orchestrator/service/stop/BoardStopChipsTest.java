package dev.jagt.orchestrator.service.stop;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.role.HeaderChips.HeaderChip;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoardStopChipsTest {

  @Test
  void offersTheLiftWithItsMomentWhenTheBoardIsStopped(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("board-stopped"), "1000");
    var paths = orchestratorPaths().withRoot(root).build();

    var chips = new BoardStopChips(new BoardStop(paths, Clock.systemUTC())).chips();

    assertThat(chips).containsExactly(new HeaderChip("board-stopped", "unstop", false, 1000L));
  }

  @Test
  void saysNothingWhenTheBoardRuns(@TempDir Path root) {
    var paths = orchestratorPaths().withRoot(root).build();

    assertThat(new BoardStopChips(new BoardStop(paths, Clock.systemUTC())).chips()).isEmpty();
  }
}
