package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandMarkerTest {

  @Test
  void restartedJagtAndFinishedRunStillNameTheirContainer(@TempDir Path root) {
    StandMarker.started("happy-path/plain")
        .withContainer("jagt-stand-41-1")
        .withJagt(77, 9000)
        .withOutcome(StandMarker.PASSED)
        .writeTo(root);

    assertThat(StandMarker.read(root, 41).container()).isEqualTo("jagt-stand-41-1");
  }

  @Test
  void hostRunNamesNoContainer() {
    assertThat(StandMarker.started("happy-path/plain").container()).isEmpty();
  }
}
