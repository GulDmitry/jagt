package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.git.TaskDiff;
import dev.jagt.orchestrator.service.git.TrustedGitState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskDiffControllerTest {

  private final TaskDiff diff = mock(TaskDiff.class);
  private final TrustedGitState trusted = mock(TrustedGitState.class);
  private final TaskDiffController api = new TaskDiffController(diff, trusted);

  @Test
  void answersTheTasksDiffPerRepository() {
    var repo = new TaskDiff.RepoDiff("alpha", "origin/main", "+new\n", false);
    when(diff.read("ABC-42")).thenReturn(List.of(repo));
    when(trusted.changes("ABC-42")).thenReturn(Optional.empty());

    assertThat(api.diff("ABC-42")).containsExactly(repo);
  }

  @Test
  void addsWhatGitObeysAndWasNotTrustedAfterTheRepositories() {
    var repo = new TaskDiff.RepoDiff("alpha", "origin/main", "+new\n", false);
    var changed =
        new TaskDiff.RepoDiff("git config and hooks", "trusted", "@@ k added\n+v\n", false);
    when(diff.read("ABC-42")).thenReturn(List.of(repo));
    when(trusted.changes("ABC-42")).thenReturn(Optional.of(changed));

    assertThat(api.diff("ABC-42")).containsExactly(repo, changed);
  }
}
