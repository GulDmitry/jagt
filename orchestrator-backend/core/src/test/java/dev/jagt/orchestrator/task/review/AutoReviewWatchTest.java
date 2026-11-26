package dev.jagt.orchestrator.task.review;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.stop.TaskStop;
import org.junit.jupiter.api.Test;

class AutoReviewWatchTest {

  @Test
  void labelsYourPauseAsYoursNotJagtStopped() {
    AutoReviewWatch watch =
        AutoReviewWatch.stopped(new TaskStop(TaskStop.Trigger.PAUSE, 1L, "you paused it"));

    assertThat(watch.label()).isEqualTo("you paused it");
  }

  @Test
  void labelsGuardAsJagtStopped() {
    AutoReviewWatch watch =
        AutoReviewWatch.stopped(new TaskStop(TaskStop.Trigger.SHIPS, 1L, "past 3 without a note"));

    assertThat(watch.label()).isEqualTo("jagt stopped");
  }
}
