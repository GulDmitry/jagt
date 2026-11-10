package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.TaskDiff;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskDiffControllerTest {

  private final TaskDiff diff = mock(TaskDiff.class);
  private final TaskDiffController api = new TaskDiffController(diff);

  @Test
  void answersTheTasksDiffPerRepository() {
    var repo = new TaskDiff.RepoDiff("alpha", "origin/main", "+new\n", false);
    when(diff.read("ABC-42")).thenReturn(List.of(repo));

    assertThat(api.diff("ABC-42")).containsExactly(repo);
  }
}
