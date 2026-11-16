package dev.jagt.orchestrator.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;

@Tag("e2e")
class FlowHarnessTest {

  @TempDir Path workspace;

  @Test
  void throwawayWorldHoldsItsConfigBeforeAnyContextStartsItsJobs() throws Exception {
    FlowHarness.createThrowawayWorld(workspace, false);

    assertThat(Files.exists(workspace.resolve("root/jagt.yml"))).isTrue();
  }

  @Test
  void flowContextEndsWithItsClassBeforeItsTempRootIsGone() {
    DirtiesContext dirties = E2eFlow.class.getAnnotation(DirtiesContext.class);

    assertThat(dirties.classMode()).isEqualTo(DirtiesContext.ClassMode.AFTER_CLASS);
  }
}
