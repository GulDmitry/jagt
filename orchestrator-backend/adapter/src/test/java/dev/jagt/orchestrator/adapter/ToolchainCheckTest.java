package dev.jagt.orchestrator.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.host.Problem;
import org.junit.jupiter.api.Test;

class ToolchainCheckTest {

  @Test
  void raisesNoVersionControlProblemWhereItsToolIsOnPath() {
    assertThat(new ToolchainCheck().problems()).extracting(Problem::key).doesNotContain("git");
  }

  @Test
  void raisesNoHookTransportProblemWhereItsToolIsOnPath() {
    assertThat(new ToolchainCheck().problems()).extracting(Problem::key).doesNotContain("curl");
  }

  @Test
  void notesNothingAboutLeftoverProcessesWhereTheirToolIsOnPath() {
    assertThat(new ToolchainCheck().notes()).isEmpty();
  }
}
