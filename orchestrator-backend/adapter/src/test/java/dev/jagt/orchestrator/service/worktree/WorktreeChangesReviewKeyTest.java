package dev.jagt.orchestrator.service.worktree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.StubAgentProperties;
import dev.jagt.orchestrator.adapter.agent.StubAgentRuntime;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.git.GitRepos;
import dev.jagt.orchestrator.service.git.WorktreeInspection;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorktreeChangesReviewKeyTest {

  @TempDir Path dir;
  private final Processes runner = new ProcessRunner();
  private final ConfigService configService = mock(ConfigService.class);

  @Test
  void keepsTheKeyWhenTheSessionCommitsTheChangeItHandedBackAndMovesItWhenTheChangeMoves()
      throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("a.txt"), "1\n");
    GitRepos.git(runner, repo, "add", ".");
    GitRepos.git(runner, repo, "commit", "-qam", "c");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "work");
    when(configService.project("demo"))
        .thenReturn(new ProjectConfig(repo.toString(), "main", "dev", List.of()));
    WorktreeChanges changes =
        new WorktreeChanges(
            configService,
            new WorktreeInspection(
                GitRepos.commands(runner), new StubAgentRuntime(StubAgentProperties.defaults())));
    TaskState task = TaskState.builder("demo", repo.toString(), TaskStatus.REVIEW_PENDING).build();
    Files.writeString(repo.resolve("a.txt"), "1\n2\n");
    String handedBack = changes.reviewKey(task);

    GitRepos.git(runner, repo, "commit", "-qam", "ship");
    String shipped = changes.reviewKey(task);
    Files.writeString(repo.resolve("a.txt"), "1\n2\n3\n");
    String edited = changes.reviewKey(task);

    assertThat(shipped).isEqualTo(handedBack);
    assertThat(edited).isNotEqualTo(handedBack);
  }
}
