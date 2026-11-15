package dev.jagt.orchestrator.service.stop;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoardStopTest {

  private final Clock clock = Clock.fixed(Instant.ofEpochMilli(7_000), ZoneOffset.UTC);

  @Test
  void startsStoppedWhereTheStopWasStandingAtTheLastRestart(@TempDir Path root) {
    OrchestratorPaths paths = orchestratorPaths().withRoot(root).build();
    new BoardStop(paths, clock).engage();

    BoardStop afterRestart = new BoardStop(paths, clock);

    assertThat(afterRestart.stopped()).isTrue();
    assertThat(afterRestart.standing()).contains(new BoardStop.Standing(7_000));
  }

  @Test
  void keepsTheFirstMomentWhereStoppedAgain(@TempDir Path root) {
    OrchestratorPaths paths = orchestratorPaths().withRoot(root).build();
    new BoardStop(paths, clock).engage();

    boolean engaged =
        new BoardStop(paths, Clock.fixed(Instant.ofEpochMilli(9_000), ZoneOffset.UTC)).engage();

    assertThat(engaged).isFalse();
    assertThat(new BoardStop(paths, clock).standing()).contains(new BoardStop.Standing(7_000));
  }

  @Test
  void runsAgainOnceTheHumanLiftsTheStop(@TempDir Path root) {
    BoardStop stop = new BoardStop(orchestratorPaths().withRoot(root).build(), clock);
    stop.engage();

    boolean lifted = stop.lift();

    assertThat(lifted).isTrue();
    assertThat(stop.stopped()).isFalse();
    assertThat(stop.standing()).isEmpty();
  }

  @Test
  void liftsNothingWhereNoStopStands(@TempDir Path root) {
    assertThat(new BoardStop(orchestratorPaths().withRoot(root).build(), clock).lift()).isFalse();
  }
}
