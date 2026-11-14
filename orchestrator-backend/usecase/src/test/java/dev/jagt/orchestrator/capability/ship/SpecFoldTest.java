package dev.jagt.orchestrator.capability.ship;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.port.worktree.Specs;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecFoldTest {

  @TempDir Path root;

  Specs specs = mock(Specs.class);
  StateService state;

  @BeforeEach
  void stateOverTheTempRoot() {
    state = stateService().withRoot(root).build();
  }

  @Test
  void foldsTheChangeInEveryRepositoryOfTheTask() {
    state.putTask(
        "ABC-42",
        TaskState.builder("api", "/wt/api", TaskStatus.REVIEW_PENDING)
            .repos(List.of(TaskRepo.of("api", "/wt/api"), TaskRepo.of("web", "/wt/web")))
            .build());

    Outcome outcome = new SpecFold(state, specs).around("ABC-42", () -> Outcome.ok("shipped"));

    verify(specs).fold(Path.of("/wt/web"), "ABC-42");
    assertThat(outcome.message()).isEqualTo("shipped");
  }

  @Test
  void shipsNothingWhenChangeCannotBeFolded() {
    state.putTask("ABC-42", TaskState.builder("api", "/wt/api", TaskStatus.REVIEW_PENDING).build());
    doThrow(new IllegalStateException("openspec/changes/abc-42 does not validate"))
        .when(specs)
        .fold(Path.of("/wt/api"), "ABC-42");

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
