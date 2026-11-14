package dev.jagt.orchestrator.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.host.Processes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorktreeHooksTest {

  @TempDir Path dir;

  private final Processes runner = new ProcessRunner();
  private Path repo;
  private Path worktree;

  @BeforeEach
  void cutWorktreeFromClone() throws Exception {
    repo = GitRepos.clone(runner, dir, "repo", "base");
    worktree = dir.resolve("wt");
    GitRepos.addWorktree(runner, repo, worktree, "ABC-42");
  }

  @Test
  void leavesSessionUngatedRatherThanTakingAwayTheProjectsOwnHooks() {
    assertThat(WorktreeHooks.gitEnv(dir.resolve("a worktree from before this existed"))).isEmpty();
  }

  @Test
  void writesNoStubForHookThatNeverRunsInWorktree() {
    WorktreeHooks.install(worktree, "ABC-42");

    assertThat(worktree.resolve(".jagt/hooks"))
        .isDirectoryNotContaining("glob:**/{pre-receive,update,post-receive,p4-pre-submit}");
  }

  @Test
  void leavesTheRepositorysOwnHooksExactlyAsTheyWere() throws Exception {
    Path hooks = repo.resolve(".git").resolve("hooks");
    Files.writeString(hooks.resolve("pre-push"), "#!/bin/sh\nexit 0\n");
    List<String> untouched =
        List.of(Objects.requireNonNull(hooks.toFile().list(), hooks.toString()));

    WorktreeHooks.install(worktree, "ABC-42");

    assertThat(hooks.resolve("pre-push")).hasContent("#!/bin/sh\nexit 0\n");
    assertThat(hooks.toFile().list()).containsExactlyInAnyOrderElementsOf(untouched);
  }

  @Test
  void runsProjectHookTheRepositoryGainedAfterTheWorktreeWasCut() throws Exception {
    WorktreeHooks.install(worktree, "ABC-42");
    GitRepos.executable(
        repo.resolve(".git").resolve("hooks").resolve("pre-commit"),
        "#!/bin/sh\necho ran > " + dir.resolve("linted.txt") + "\n");
    Files.writeString(worktree.resolve("f.txt"), "changed");

    Process commit =
        GitRepos.shell(
            worktree,
            WorktreeHooks.gitEnv(worktree)
                + "git -c user.email=t@t -c user.name=t commit -qam work");
    assertThat(commit.waitFor(30, TimeUnit.SECONDS)).isTrue();

    assertThat(dir.resolve("linted.txt")).exists();
  }

  @Test
  void refusesPushToAnyBranchButTheTaskBranch() throws Exception {
    Files.writeString(worktree.resolve("f.txt"), "work");
    GitRepos.git(runner, worktree, "commit", "-qam", "work");
    WorktreeHooks.install(worktree, "ABC-42");

    Process push =
        GitRepos.shell(
            worktree, WorktreeHooks.gitEnv(worktree) + "git push origin HEAD:refs/heads/main");
    String output = new String(push.getInputStream().readAllBytes(), UTF_8);
    assertThat(push.waitFor(30, TimeUnit.SECONDS)).isTrue();

    assertThat(push.exitValue()).isNotZero();
    assertThat(output).contains("refs/heads/main is not this task's branch");
  }

  @Test
  void pushesTheTaskBranchItself() throws Exception {
    WorktreeHooks.install(worktree, "ABC-42");

    Process push =
        GitRepos.shell(worktree, WorktreeHooks.gitEnv(worktree) + "git push origin ABC-42");
    String output = new String(push.getInputStream().readAllBytes(), UTF_8);
    assertThat(push.waitFor(30, TimeUnit.SECONDS)).isTrue();

    assertThat(push.exitValue()).describedAs(output).isZero();
  }

  @Test
  void refusesDeletingTheBranchTheReviewRequestIsBuiltOn() throws Exception {
    GitRepos.git(runner, worktree, "push", "-q", "origin", "ABC-42");
    WorktreeHooks.install(worktree, "ABC-42");

    Process push =
        GitRepos.shell(worktree, WorktreeHooks.gitEnv(worktree) + "git push origin :ABC-42");
    String output = new String(push.getInputStream().readAllBytes(), UTF_8);
    assertThat(push.waitFor(30, TimeUnit.SECONDS)).isTrue();

    assertThat(push.exitValue()).isNotZero();
    assertThat(output).contains("refuses deleting refs/heads/ABC-42");
  }

  @Test
  void runsTheProjectsOwnPushHookOnAnAllowedPush() throws Exception {
    GitRepos.executable(
        repo.resolve(".git").resolve("hooks").resolve("pre-push"),
        "#!/bin/sh\ncat > " + dir.resolve("seen-by-the-project.txt") + "\n");
    WorktreeHooks.install(worktree, "ABC-42");

    Process push =
        GitRepos.shell(worktree, WorktreeHooks.gitEnv(worktree) + "git push origin ABC-42");
    assertThat(push.waitFor(30, TimeUnit.SECONDS)).isTrue();

    assertThat(dir.resolve("seen-by-the-project.txt")).content().contains("refs/heads/ABC-42");
  }

  @Test
  void delegatesToTheHooksOfTheRepositoryBeingPushedNotOfTheOneItWasWrittenFor() throws Exception {
    Path siblingRepo =
        GitRepos.siblingClone(runner, dir, GitRepos.originOf(dir, "repo"), "sibling");
    Path siblingWorktree = dir.resolve("sibling-wt");
    GitRepos.addWorktree(runner, siblingRepo, siblingWorktree, "ABC-42");
    GitRepos.executable(
        siblingRepo.resolve(".git").resolve("hooks").resolve("pre-push"),
        "#!/bin/sh\ncat > " + dir.resolve("seen-by-the-sibling.txt") + "\n");
    WorktreeHooks.install(worktree, "ABC-42");

    Process push =
        GitRepos.shell(siblingWorktree, WorktreeHooks.gitEnv(worktree) + "git push origin ABC-42");
    assertThat(push.waitFor(30, TimeUnit.SECONDS)).isTrue();

    assertThat(dir.resolve("seen-by-the-sibling.txt")).content().contains("refs/heads/ABC-42");
  }
}
