package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.job.ProjectClonesJob;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class ClonesHealthTest {

  private final ProjectClonesJob clones = mock(ProjectClonesJob.class);
  private final ClonesHealth health = new ClonesHealth(clones);

  @Test
  void isNotReadyWhileOneCloneRuns() {
    when(clones.cloning()).thenReturn(true);

    assertThat(health.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
  }

  @Test
  void isReadyWhenNoCloneRuns() {
    when(clones.cloning()).thenReturn(false);

    assertThat(health.health().getStatus()).isEqualTo(Status.UP);
  }
}
