package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InfraRetriesTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private static StateService state(Path dir) {
        return new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(dir.toString()).withStateFile(dir.resolve("state.json").toString())));
    }

    private static InfraRetries retries(StateService state) {
        return new InfraRetries(state, mock(TaskStops.class), InfraWaitProperties.defaults(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void armsTheFirstRetryFiveMinutesOut(@TempDir Path dir) {
        StateService state = state(dir);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());

        Optional<InfraRetry> armed = retries(state).arm("ABC-42", "https://host.invalid/p/1", "unit-tests",
                "runner_system_failure");

        assertThat(armed.orElseThrow().at()).isEqualTo(NOW.plusSeconds(300).toEpochMilli());
        assertThat(state.task("ABC-42").orElseThrow().infraRetries()).containsExactly(armed.get());
    }

    @Test
    void keepsTheTimeOfARetryAlreadyArmedForTheSamePipeline(@TempDir Path dir) {
        StateService state = state(dir);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build());
        InfraRetries retries = retries(state);
        InfraRetry first = retries.arm("ABC-42", "https://host.invalid/p/1", "unit-tests", "runner_system_failure")
                .orElseThrow();

        Optional<InfraRetry> again = retries.arm("ABC-42", "https://host.invalid/p/1", "unit-tests",
                "runner_system_failure");

        assertThat(again).contains(first);
    }

    @Test
    void armsTheThirdRetryFortyFiveMinutesOutAfterTwoWereSent(@TempDir Path dir) {
        StateService state = state(dir);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).infraRetries(List.of(
                new InfraRetry(1_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true, 1, 3),
                new InfraRetry(2_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true, 1, 3))).build());

        Optional<InfraRetry> armed = retries(state).arm("ABC-42", "https://host.invalid/p/1", "unit-tests",
                "runner_system_failure");

        assertThat(armed.orElseThrow().at()).isEqualTo(NOW.plusSeconds(45 * 60).toEpochMilli());
    }

    @Test
    void armsNothingOnceAsManyRetriesWereSentAsWaitsAreConfigured(@TempDir Path dir) {
        StateService state = state(dir);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).infraRetries(List.of(
                new InfraRetry(1_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true, 1, 3),
                new InfraRetry(2_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true, 1, 3),
                new InfraRetry(3_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true, 1, 3))).build());

        Optional<InfraRetry> armed = retries(state).arm("ABC-42", "https://host.invalid/p/1", "unit-tests",
                "runner_system_failure");

        assertThat(armed).isEmpty();
    }

    @Test
    void listsOnlyTheRetriesWhoseTimeHasCome(@TempDir Path dir) {
        StateService state = state(dir);
        InfraRetry overdue = InfraRetry.armed(NOW.minusSeconds(1).toEpochMilli(), "https://host.invalid/p/1", "a", "api_failure", 1, 3);
        InfraRetry later = InfraRetry.armed(NOW.plusSeconds(1).toEpochMilli(), "https://host.invalid/p/2", "b", "api_failure", 1, 3);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).infraRetries(List.of(overdue)).build());
        state.putTask("ABC-43", TaskState.builder("proj", "/wt2", TaskStatus.CI_POLLING).infraRetries(List.of(later)).build());

        Map<String, InfraRetry> due = retries(state).due();

        assertThat(due).containsOnlyKeys("ABC-42");
    }

    @Test
    void recordsASentRetryAsAMoveWithoutChangingTheStatus(@TempDir Path dir) {
        StateService state = state(dir);
        InfraRetry armed = InfraRetry.armed(1_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", 1, 3);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).infraRetries(List.of(armed)).build());

        retries(state).sent("ABC-42", armed);

        TaskState after = state.task("ABC-42").orElseThrow();
        assertThat(after.infraRetries()).containsExactly(armed.sentAt(NOW.toEpochMilli()));
        assertThat(after.history()).hasSize(2);
        assertThat(after.status()).isEqualTo(TaskStatus.CI_POLLING);
    }

    @Test
    void forgetsOnlyTheArmedRetryWhenTheSentOnesAreKept(@TempDir Path dir) {
        StateService state = state(dir);
        InfraRetry sent = new InfraRetry(1_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", true, 1, 3);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).infraRetries(List.of(sent,
                InfraRetry.armed(9_000, "https://host.invalid/p/1", "unit-tests", "runner_system_failure", 1, 3))).build());

        retries(state).clear("ABC-42", false);

        assertThat(state.task("ABC-42").orElseThrow().infraRetries()).containsExactly(sent);
    }

    @Test
    void waitsOnlyWhileTheTaskIsOpenAndItsChecksStillReadRed(@TempDir Path dir) {
        StateService state = state(dir);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).pipelineStatus("failed").build());
        state.putTask("ABC-43", TaskState.builder("proj", "/wt2", TaskStatus.CI_POLLING).pipelineStatus("running").build());
        InfraRetries retries = retries(state);

        assertThat(retries.stillWaiting("ABC-42")).isTrue();
        assertThat(retries.stillWaiting("ABC-43")).isFalse();
        assertThat(retries.stillWaiting("ABC-44")).isFalse();
    }

    @Test
    void doesNotApplyWhereTheTaskIsPastItsReview(@TempDir Path dir) {
        StateService state = state(dir);
        InfraRetries retries = retries(state);
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
    void sendsTheDueRetryOfADeployedTaskWhoseDeployPipelineDiedOnInfrastructure(@TempDir Path dir) {
        StateService state = state(dir);
        state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).pipelineStatus("success").build());

        assertThat(retries(state).stillWaiting("ABC-42")).isTrue();
    }
}
