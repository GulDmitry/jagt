package dev.jagt.orchestrator.capability.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.git.GitCommands;
import dev.jagt.orchestrator.service.git.GitDeploy;
import dev.jagt.orchestrator.service.git.GitRevert;
import dev.jagt.orchestrator.task.repo.Remote;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitCaseFactsTest {

  private final GitCommands git = mock(GitCommands.class);
  private final GitDeploy deploy = mock(GitDeploy.class);
  private final GitRevert revert = mock(GitRevert.class);
  private final GitCaseFacts facts = new GitCaseFacts(git, deploy, revert);

  @Test
  void namesTheRevertCaseWhenTheTasksRevertWorktreeExists() {
    Path repo = Path.of("repo");
    Path worktree = Path.of("revert-ABC-1");
    when(git.remote(repo)).thenReturn(Remote.LOCAL);
    when(git.run(any(), any())).thenReturn(new Processes.Result(0, "", ""));
    when(revert.hasRevertWorktree(repo, "ABC-1")).thenReturn(true);
    when(revert.revertWorktreePath(repo, "ABC-1")).thenReturn(worktree);

    List<String> cases = facts.cases(repo, "ABC-1", "demo", "dev");

    assertThat(cases)
        .containsExactly(
            "demo: the revert worktree revert-ABC-1 waits; run `git merge dev` in it,"
                + " resolve what conflicts, `git add` it, then press `revert ABC-1`.");
  }
}
