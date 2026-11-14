package dev.jagt.orchestrator.capability.ship;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.TestTaskStopsBuilder.taskStops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.service.MergedRequests;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.StatusChange;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MergedGuardTest {

  @TempDir Path root;

  private final Clock clock = Clock.fixed(Instant.ofEpochMilli(100_000_000), ZoneOffset.UTC);
  private final MergedRequests merged = mock(MergedRequests.class);

  @Test
  void doesNotCountShipPressedAfterTheRequestMergedTowardTheLoopStop() {
    StateService state = stateService().withRoot(root).build();
    state.putTask(
        "ABC-42",
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .history(
                List.of(
                    new StatusChange(TaskStatus.SHIPPING, 99_000_000, ActionOrigin.MASTER),
                    new StatusChange(TaskStatus.SHIPPING, 99_100_000, ActionOrigin.MASTER),
                    new StatusChange(TaskStatus.SHIPPING, 99_200_000, ActionOrigin.MASTER)))
            .build());
    TaskStops stops = taskStops().withRoot(root).build(state, clock);
    when(merged.refresh("ABC-42")).thenReturn(true);

    new MergedGuard(merged)
        .around(
            "ABC-42",
            () -> {
              state.updateTask(
                  "ABC-42",
                  task -> task.withStatus(TaskStatus.SHIPPING, "shipping", true, clock.millis()));
              return Outcome.relayed("relayed", "shipping");
            });

    assertThat(stops.mayShip("ABC-42")).isTrue();
  }

  @Test
  void runsTheShipWhereRequestIsStillOpen() {
    when(merged.refresh("ABC-42")).thenReturn(false);

    Outcome outcome =
        new MergedGuard(merged).around("ABC-42", () -> Outcome.relayed("relayed", "shipping"));

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
  }

  @Test
  void shipsNothingWhereEveryRequestIsMerged() {
    when(merged.refresh("ABC-42")).thenReturn(true);

    Outcome outcome =
        new MergedGuard(merged).around("ABC-42", () -> Outcome.relayed("relayed", "shipping"));

    assertThat(outcome.kind()).isEqualTo(Outcome.Kind.NOTHING);
  }
}
