package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestInfraRetriesBuilder.infraRetries;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InfraRetriesTest {

  @TempDir Path dir;

  private StateService state;

  @BeforeEach
  void startsWithAnEmptyState() {
    state = stateService().withRoot(dir).build();
  }

  private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
  private static final InfraRetry SENT_FIRST =
      new InfraRetry(
          1_000,
          "shop",
          "https://host.invalid/p/1",
          "unit-tests",
          "runner_system_failure",
          true,
          1,
          3);
  private static final InfraRetry SENT_SECOND =
      new InfraRetry(
          2_000,
          "shop",
          "https://host.invalid/p/1",
          "unit-tests",
          "runner_system_failure",
          true,
          1,
          3);
  private static final InfraRetry SENT_THIRD =
      new InfraRetry(
          3_000,
          "shop",
          "https://host.invalid/p/1",
          "unit-tests",
          "runner_system_failure",
          true,
          1,
          3);

  @Test
  void armsTheFirstRetryFiveMinutesOut() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());

    Optional<InfraRetry> armed =
        infraRetries()
            .build(state)
            .arm(
                "ABC-42",
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure");

    assertThat(armed.orElseThrow().at()).isEqualTo(NOW.plusSeconds(300).toEpochMilli());
    assertThat(state.task("ABC-42").orElseThrow().infraRetries()).containsExactly(armed.get());
  }

  @Test
  void numbersTheFirstRetryOneOfThree() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());

    Optional<InfraRetry> armed =
        infraRetries()
            .build(state)
            .arm(
                "ABC-42",
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure");

    assertThat(armed.orElseThrow().attempt()).isEqualTo(1);
  }

  @Test
  void keepsTheTimeOfRetryAlreadyArmedForTheSamePipeline() {
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    InfraRetries retries = infraRetries().build(state);
    InfraRetry first =
        retries
            .arm(
                "ABC-42", "shop", "https://host.invalid/p/1", "unit-tests", "runner_system_failure")
            .orElseThrow();

    Optional<InfraRetry> again =
        retries.arm(
            "ABC-42", "shop", "https://host.invalid/p/1", "unit-tests", "runner_system_failure");

    assertThat(again).contains(first);
  }

  @Test
  void armsTheThirdRetryFortyFiveMinutesOutAfterTwoWereSent() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .infraRetries(List.of(SENT_FIRST, SENT_SECOND))
            .build());

    Optional<InfraRetry> armed =
        infraRetries()
            .build(state)
            .arm(
                "ABC-42",
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure");

    assertThat(armed.orElseThrow().at()).isEqualTo(NOW.plusSeconds(45 * 60).toEpochMilli());
  }

  @Test
  void armsNothingOnceAsManyRetriesWereSentAsWaitsAreConfigured() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .infraRetries(List.of(SENT_FIRST, SENT_SECOND, SENT_THIRD))
            .build());

    Optional<InfraRetry> armed =
        infraRetries()
            .build(state)
            .arm(
                "ABC-42",
                "shop",
                "https://host.invalid/p/1",
                "unit-tests",
                "runner_system_failure");

    assertThat(armed).isEmpty();
  }

  @Test
  void listsOnlyTheRetriesWhoseTimeHasCome() {
    InfraRetry overdue =
        InfraRetry.armed(
            NOW.minusSeconds(1).toEpochMilli(),
            "shop",
            "https://host.invalid/p/1",
            "a",
            "api_failure",
            1,
            3);
    InfraRetry later =
        InfraRetry.armed(
            NOW.plusSeconds(1).toEpochMilli(),
            "shop",
            "https://host.invalid/p/2",
            "b",
            "api_failure",
            1,
            3);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .infraRetries(List.of(overdue))
            .build());
    state.putTask(
        "ABC-43",
        TaskState.builder("proj", "/wt2", TaskStatus.CI_POLLING)
            .infraRetries(List.of(later))
            .build());

    Map<String, InfraRetry> due = infraRetries().build(state).due();

    assertThat(due).containsOnlyKeys("ABC-42");
  }

  @Test
  void recordsSentRetryAsMoveWithoutChangingTheStatus() {
    InfraRetry armed =
        InfraRetry.armed(
            1_000, "shop", "https://host.invalid/p/1", "unit-tests", "runner_system_failure", 1, 3);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .infraRetries(List.of(armed))
            .build());

    infraRetries().build(state).sent("ABC-42", armed);

    TaskState after = state.task("ABC-42").orElseThrow();
    assertThat(after.infraRetries()).containsExactly(armed.sentAt(NOW.toEpochMilli()));
    assertThat(after.history()).hasSize(2);
    assertThat(after.status()).isEqualTo(TaskStatus.CI_POLLING);
  }

  @Test
  void forgetsOnlyTheArmedRetryWhenTheSentOnesAreKept() {
    InfraRetry sent =
        new InfraRetry(
            1_000,
            "shop",
            "https://host.invalid/p/1",
            "unit-tests",
            "runner_system_failure",
            true,
            1,
            3);
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .infraRetries(
                List.of(
                    sent,
                    InfraRetry.armed(
                        9_000,
                        "shop",
                        "https://host.invalid/p/1",
                        "unit-tests",
                        "runner_system_failure",
                        1,
                        3)))
            .build());

    infraRetries().build(state).clear("ABC-42", false);

    assertThat(state.task("ABC-42").orElseThrow().infraRetries()).containsExactly(sent);
  }

  @Test
  void waitsOnlyWhileTheTaskIsOpenAndItsChecksStillReadRed() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).pipelineStatus("failed").build());
    state.putTask(
        "ABC-43",
        TaskState.builder("proj", "/wt2", TaskStatus.CI_POLLING).pipelineStatus("running").build());
    InfraRetries retries = infraRetries().build(state);

    assertThat(retries.stillWaiting("ABC-42")).isTrue();
    assertThat(retries.stillWaiting("ABC-43")).isFalse();
    assertThat(retries.stillWaiting("ABC-44")).isFalse();
  }

  @Test
  void doesNotApplyWhereTheTaskIsPastItsReview() {
    final InfraRetries retries = infraRetries().build(state);
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
    state.putTask("ABC-43", TaskState.builder("proj", "/wt2", TaskStatus.DEPLOYED).build());
    state.putTask("ABC-44", TaskState.builder("proj", "/wt3", TaskStatus.DEPLOY_CONFLICT).build());
    state.putTask("ABC-45", TaskState.builder("proj", "/wt4", TaskStatus.REVERTED).build());

    assertThat(retries.applies("ABC-42")).isTrue();
    assertThat(retries.applies("ABC-43")).isFalse();
    assertThat(retries.applies("ABC-44")).isFalse();
    assertThat(retries.applies("ABC-45")).isFalse();
  }

  @Test
  void sendsTheDueRetryOfDeployedTaskWhoseDeployPipelineDiedOnInfrastructure() {
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).pipelineStatus("success").build());

    assertThat(infraRetries().build(state).stillWaiting("ABC-42")).isTrue();
  }
}
