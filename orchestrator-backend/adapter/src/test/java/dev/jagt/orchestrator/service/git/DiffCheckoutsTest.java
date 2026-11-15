package dev.jagt.orchestrator.service.git;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.task.repo.BranchStrategy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiffCheckoutsTest {

  @TempDir Path dir;

  private final Processes runner = new ProcessRunner();
  private String project;
  private Path repo;
  private GitWorktrees git;
  private DiffCheckouts diffs;

  @BeforeEach
  void cloneRepository() throws Exception {
    project = "p" + dir.getFileName();
    repo = GitRepos.clone(runner, dir, "repo", "base");
    GitCommands commands = GitRepos.commands(runner);
    git = new GitWorktrees(commands);
    diffs = new DiffCheckouts(commands);
  }

  @Test
  void excludesGitIgnoredPlumbingFromTheIdeDiffSnapshot() throws Exception {
    Path wt = dir.resolve("wt");
    git.createWorktree(repo, wt, "ABC-1", "origin/main", BranchStrategy.FRESH);
    Files.writeString(wt.resolve("f.txt"), "task change");
    Files.writeString(wt.resolve("new.js"), "new source");
    Files.writeString(wt.resolve("mcp_client.js"), "plumbing");
    Files.writeString(repo.resolve(".git").resolve("info").resolve("exclude"), "mcp_client.js\n");

    Path clean = diffs.checkoutWorktreeCleanForDiff(wt, repo, "origin/main", "ABC-1", project);

    boolean plumbing = Files.exists(clean.resolve("mcp_client.js"));
    String changed = Files.readString(clean.resolve("f.txt"));
    final String added = Files.readString(clean.resolve("new.js"));
    git.removeDiffWorktrees(repo, "ABC-1", project);

    assertThat(plumbing).isFalse();
    assertThat(changed).isEqualTo("task change");
    assertThat(added).isEqualTo("new source");
  }

  @Test
  void prunesTheAdminEntryOfDiffCheckoutSomethingElseAlreadyDeleted() throws Exception {
    Path base = diffs.checkoutBaseForDiff(repo, "origin/main", "ABC-8", project);
    runner.run(dir, GitRepos.TIMEOUT, List.of("rm", "-rf", base.toString()));

    git.removeDiffWorktrees(repo, "ABC-8", project);

    assertThat(runner.run(repo, GitRepos.TIMEOUT, List.of("git", "worktree", "list")).stdout())
        .doesNotContain(base.getFileName().toString());
  }

  @Test
  void deletesBothThrowawayDiffCheckoutsWhenTheTaskIsRetired() throws Exception {
    Path wt = dir.resolve("wt");
    git.createWorktree(repo, wt, "ABC-9", "origin/main", BranchStrategy.FRESH);
    Path base = diffs.checkoutBaseForDiff(repo, "origin/main", "ABC-9", project);
    Path clean = diffs.checkoutWorktreeCleanForDiff(wt, repo, "origin/main", "ABC-9", project);

    git.removeDiffWorktrees(repo, "ABC-9", project);

    assertThat(base).doesNotExist();
    assertThat(clean).doesNotExist();
  }

  @Test
  void keepsTheDiffCheckoutsApartWhenOneTaskSpansTwoRepositories() throws Exception {
    Path web = GitRepos.clone(runner, dir, "web", "web base");

    Path apiBase = diffs.checkoutBaseForDiff(repo, "origin/main", "ABC-7", project + "-api");
    Path webBase = diffs.checkoutBaseForDiff(web, "origin/main", "ABC-7", project + "-web");

    String apiSaid = Files.readString(apiBase.resolve("f.txt"));
    final String webSaid = Files.readString(webBase.resolve("f.txt"));
    git.removeDiffWorktrees(repo, "ABC-7", project + "-api");
    git.removeDiffWorktrees(web, "ABC-7", project + "-web");
    assertThat(apiSaid).isEqualTo("base");
    assertThat(webSaid).isEqualTo("web base");
  }

  @Test
  void replacesLeftoverSnapshotOfTheTasksWorkWhenTheDiffIsOpenedAgain() throws Exception {
    Path wt = dir.resolve("wt");
    git.createWorktree(repo, wt, "ABC-6", "origin/main", BranchStrategy.FRESH);
    Files.writeString(wt.resolve("f.txt"), "task change");
    Path leftover = DiffCheckouts.diffWorktreePaths("ABC-6", project).getLast();
    Files.createDirectories(leftover);
    Files.writeString(leftover.resolve("stale.txt"), "from a snapshot nobody removed");

    Path clean = diffs.checkoutWorktreeCleanForDiff(wt, repo, "origin/main", "ABC-6", project);

    String changed = Files.readString(clean.resolve("f.txt"));
    boolean stale = Files.exists(clean.resolve("stale.txt"));
    git.removeDiffWorktrees(repo, "ABC-6", project);
    assertThat(changed).isEqualTo("task change");
    assertThat(stale).isFalse();
  }
}
