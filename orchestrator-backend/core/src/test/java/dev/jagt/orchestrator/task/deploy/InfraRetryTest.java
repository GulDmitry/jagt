package dev.jagt.orchestrator.task.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.review.RoundTrail;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class InfraRetryTest {

  @Test
  void recordArmedBeforeItNamedItsProjectReadsAsBlank() throws Exception {
    InfraRetry old =
        new JsonMapper()
            .readValue(
                "{\"at\":1000,\"pipelineUrl\":\"https://host.invalid/p/1\","
                    + "\"jobs\":\"unit-tests\",\"reason\":\"runner_system_failure\","
                    + "\"sent\":false,\"attempt\":1,\"of\":3}",
                InfraRetry.class);

    assertThat(old.project()).isEmpty();
  }

  @Test
  void stopsTheTaskWithTheJobAndItsReasonOnceTheLastRetryWasSent() {
    List<InfraRetry> retries =
        List.of(
            new InfraRetry(
                1_000,
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure",
                true,
                1,
                3),
            new InfraRetry(
                2_000,
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure",
                true,
                1,
                3),
            new InfraRetry(
                3_000,
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure",
                true,
                1,
                3));

    Optional<TaskStop> stop = InfraRetry.stop(retries, RoundTrail.none(), 3, 4_000);

    assertThat(stop)
        .contains(
            new TaskStop(
                TaskStop.Trigger.INFRA, 4_000, "unit-tests: runner_system_failure, 3 retries"));
  }

  @Test
  void leavesTheTaskWaitingWhileRetryIsLeft() {
    List<InfraRetry> retries =
        List.of(
            new InfraRetry(
                1_000,
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure",
                true,
                1,
                3),
            InfraRetry.armed(
                9_000,
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure",
                1,
                3));

    Optional<TaskStop> stop = InfraRetry.stop(retries, RoundTrail.none(), 2, 4_000);

    assertThat(stop).isEmpty();
  }

  @Test
  void countsNoRetrySentBeforeTheLift() {
    List<InfraRetry> retries =
        List.of(
            new InfraRetry(
                1_000,
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure",
                true,
                1,
                3),
            new InfraRetry(
                2_000,
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure",
                true,
                1,
                3));

    Optional<TaskStop> stop = InfraRetry.stop(retries, RoundTrail.liftedAt(1_500), 2, 4_000);

    assertThat(stop).isEmpty();
  }

  @Test
  void stopsAtOnceWhenNoRetryIsAllowed() {
    Optional<TaskStop> stop = InfraRetry.stop(List.of(), RoundTrail.none(), 0, 4_000);

    assertThat(stop).isEmpty();
  }

  @Test
  void countsNoRetrySentAtTheInstantOfTheLift() {
    List<InfraRetry> retries =
        List.of(
            new InfraRetry(
                1_000,
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure",
                true,
                1,
                3),
            new InfraRetry(
                2_000,
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure",
                true,
                2,
                3));

    assertThat(InfraRetry.sentSince(retries, RoundTrail.liftedAt(1_000)))
        .extracting(InfraRetry::at)
        .containsExactly(2_000L);
  }
}
