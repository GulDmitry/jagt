package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StandDockerProbeTest {

  @Test
  void dockerProbeIsReadOnlyByStandTasksAndBoundedSoHungDaemonCannotStallConfiguration() {
    String stand = SourceRoots.read(Path.of("build-logic/src/main/groovy/jagt.stand.gradle"));

    assertThat(stand).doesNotContain(".result.get()").contains("(sleep 5; kill $p)");
  }
}
