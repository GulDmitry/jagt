package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.notify.PointWatch;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PointSweepTest {

  private final PointWatch watch = mock(PointWatch.class);

  @Test
  void sweepsThePointsEveryFiveSecondsForTheTasks() {
    PointSweep job = new PointSweep(watch);

    job.run();

    verify(watch).sweep();
    assertThat(job.every()).isEqualTo(Duration.ofSeconds(5));
    assertThat(job.id()).isEqualTo("points");
    assertThat(job.serves()).isEqualTo(Job.Serves.TASKS);
    assertThat(job.describe()).isNotBlank();
  }
}
