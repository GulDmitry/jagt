package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

class StandEngineTest {

  @Test
  void skipsWithOneSentenceWhenDockerIsAbsent(@TempDir Path root) {
    StandEngine engine = new StandEngine(StandEngine.CONTAINER, () -> false);

    assertThatThrownBy(() -> engine.start(root, 1, ""))
        .isInstanceOf(TestAbortedException.class)
        .hasMessage(
            "docker is absent on this machine, so the stand run is skipped;"
                + " -Plocal runs it on the host");
  }

  @Test
  void refusesAnEngineNobodyBuilt(@TempDir Path root) {
    StandEngine engine = new StandEngine("vm", () -> true);

    assertThatThrownBy(() -> engine.start(root, 1, ""))
        .isInstanceOf(StandFailure.class)
        .satisfies(thrown -> assertThat(thrown.getMessage()).contains("vm"));
  }
}
