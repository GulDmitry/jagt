package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelinesTest {

  @Test
  void runningPipelineStaysRunningUntilItsDelayPassed(@TempDir Path dir) {
    StandStore store = new StandStore(dir, "http://127.0.0.1:1");
    store.createRequest("feature", "main", "t", "");
    Pipelines pipelines = new Pipelines(store, Duration.ofHours(1));

    pipelines.passDue(new Timeline(dir.resolve("timeline.log"), mock(ContextRelays.class)));
    pipelines.passDue(new Timeline(dir.resolve("timeline.log"), mock(ContextRelays.class)));

    assertThat(store.request(1).path("pipeline").asString()).isEqualTo("running");
  }

  @Test
  void runningPipelineSucceedsOnceItsDelayPassed(@TempDir Path dir) {
    StandStore store = new StandStore(dir, "http://127.0.0.1:1");
    store.createRequest("feature", "main", "t", "");
    Pipelines pipelines = new Pipelines(store, Duration.ZERO);
    Timeline timeline = new Timeline(dir.resolve("timeline.log"), mock(ContextRelays.class));

    pipelines.passDue(timeline);
    pipelines.passDue(timeline);

    assertThat(store.request(1).path("pipeline").asString()).isEqualTo("success");
    assertThat(timeline.tail(1)).singleElement().asString().endsWith("merge request 1 set success");
  }

  @Test
  void failedPipelineIsLeftAlone(@TempDir Path dir) {
    StandStore store = new StandStore(dir, "http://127.0.0.1:1");
    store.createRequest("feature", "main", "t", "");
    store.setPipeline(1, "failed");
    Pipelines pipelines = new Pipelines(store, Duration.ZERO);
    Timeline timeline = new Timeline(dir.resolve("timeline.log"), mock(ContextRelays.class));

    pipelines.passDue(timeline);
    pipelines.passDue(timeline);

    assertThat(store.request(1).path("pipeline").asString()).isEqualTo("failed");
  }
}
