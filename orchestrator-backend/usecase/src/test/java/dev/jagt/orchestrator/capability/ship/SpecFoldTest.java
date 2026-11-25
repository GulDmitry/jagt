package dev.jagt.orchestrator.capability.ship;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.worktree.TaskSpecs;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecFoldTest {

  @TempDir Path root;

  TaskSpecs specs = mock(TaskSpecs.class);
  StateService state;

  @BeforeEach
  void stateOverTheTempRoot() {
    state = stateService().withRoot(root).build();
  }

  @Test
  void foldsTheTasksChangeBeforeTheShip() {
    state.putTask("ABC-42", TaskState.builder("api", "/wt/api", TaskStatus.REVIEW_PENDING).build());

    Outcome outcome = new SpecFold(state, specs).around("ABC-42", () -> Outcome.ok("shipped"));

    verify(specs).fold(eq("ABC-42"), any(TaskState.class));
    assertThat(outcome.message()).isEqualTo("shipped");
  }

  @Test
  void shipsNothingWhenChangeCannotBeFolded() {
    state.putTask("ABC-42", TaskState.builder("api", "/wt/api", TaskStatus.REVIEW_PENDING).build());
    doThrow(new IllegalStateException("openspec/changes/abc-42 does not validate"))
        .when(specs)
        .fold(eq("ABC-42"), any(TaskState.class));

    assertThatThrownBy(() ->
            new SpecFold(state, specs)
                .around(
                    "ABC-42",
                    () -> {
                      throw new AssertionError("shipped");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("openspec/changes/abc-42 does not validate");
  }
}
