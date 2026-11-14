package dev.jagt.orchestrator.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.adapter.LsofWorktreeProcesses;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.StubAgentProperties;
import dev.jagt.orchestrator.adapter.agent.StubAgentRuntime;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.OriginContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorktreeInspectionTest {

  @TempDir Path dir;
  private final Processes runner = new ProcessRunner();
  private final WorktreeInspection git =
      new WorktreeInspection(
          GitRepos.commands(runner), new StubAgentRuntime(StubAgentProperties.defaults()));

  @Test
  void readsTheWorkWorktreeHoldsUncommittedAndIgnoresJagtsOwnFiles() throws Exception {
    Path repo = dir.resolve("repo");
    Files.createDirectories(repo);
    GitRepos.git(runner, dir, "init", "-q", "-b", "main", repo.toString());
    Files.writeString(repo.resolve("a file.java"), "class A {}");
    GitRepos.commit(runner, repo, "init");

    Files.writeString(repo.resolve("task_context.md"), "the round brief");
    assertThat(git.hasUncommittedChanges(repo, repo)).isFalse();

    Files.writeString(repo.resolve("a file.java"), "class A { int x; }");
    assertThat(git.hasUncommittedChanges(repo, repo)).isTrue();
  }

  @Test
  void readsTheTreeAsChangedByAnEditToItsWorkButNotByJagtsOwnFiles() throws Exception {
    Path repo = dir.resolve("repo");
    Files.createDirectories(repo);
    GitRepos.git(runner, dir, "init", "-q", "-b", "main", repo.toString());
    Files.writeString(repo.resolve("A.java"), "class A {}");
    GitRepos.commit(runner, repo, "init");
    Files.writeString(repo.resolve("A.java"), "class A { int x; }");
    String answered = git.treeState(repo, repo);

    Files.writeString(repo.resolve("task_context.md"), "the next brief");
    assertThat(git.treeState(repo, repo)).isEqualTo(answered);

    Files.writeString(repo.resolve("A.java"), "class A { int y; }");
    assertThat(git.treeState(repo, repo)).isNotEqualTo(answered);
  }

  @Test
  void readsTheTreeWithoutRunningDiffProgramTheSessionSet() throws Exception {
    Path repo = dir.resolve("repo");
    Files.createDirectories(repo);
    GitRepos.git(runner, dir, "init", "-q", "-b", "main", repo.toString());
    Files.writeString(repo.resolve("A.java"), "class A {}");
    GitRepos.commit(runner, repo, "init");
    Path ran = dir.resolve("ran");
    Path program = dir.resolve("external.sh");
    Files.writeString(program, "#!/bin/sh\ntouch " + ran + "\n");
    program.toFile().setExecutable(true);
    GitRepos.git(runner, repo, "config", "diff.external", program.toString());
    Files.writeString(repo.resolve("A.java"), "class A { int x; }");

    git.treeState(repo, repo);

    assertThat(ran).doesNotExist();
  }

  @ParameterizedTest
  @ValueSource(strings = {"main", "origin/main"})
  void readsTheChangesSinceTheBaseCommittedAndNotNamingNewFilesButNotJagtsOwn(String baseBranch)
      throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("a.txt"), "base\n");
    Files.writeString(repo.resolve("b.txt"), "base\n");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    Files.writeString(repo.resolve("a.txt"), "committed\n");
    GitRepos.git(runner, repo, "commit", "-qam", "ABC-42 Widen the column");
    Files.writeString(repo.resolve("b.txt"), "uncommitted\n");
    Files.writeString(repo.resolve("new.txt"), "fresh\n");
    Files.writeString(repo.resolve("task_context.md"), "the round brief");

    String changes = git.changesSince(repo, repo, baseBranch);

    assertThat(changes)
        .contains("+committed", "+uncommitted")
        .endsWith("New files, not in the diff: new.txt\n");
  }

  @Test
  void identifiesTheTasksOwnDiffTheSameAfterTheBaseMovedUnderItAndDifferentOnceItsWorkChanges()
      throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("a.txt"), "1\n2\n3\n4\n5\n6\n7\n8\n");
    GitRepos.git(runner, repo, "add", ".");
    GitRepos.git(runner, repo, "commit", "-qam", "c");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "work");
    Files.writeString(repo.resolve("a.txt"), "1\n2\n3\n4\n5\n6\n7 changed\n8\n");
    GitRepos.git(runner, repo, "commit", "-qam", "c");
    final String before = git.ownDiffId(repo, repo, "main").orElseThrow();

    GitRepos.git(runner, repo, "checkout", "-q", "main");
    Files.writeString(repo.resolve("a.txt"), "0\n1\n2\n3\n4\n5\n6\n7\n8\n");
    Files.writeString(repo.resolve("other.txt"), "elsewhere\n");
    GitRepos.git(runner, repo, "add", ".");
    GitRepos.git(runner, repo, "commit", "-qam", "c");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    GitRepos.git(runner, repo, "checkout", "-q", "work");
    GitRepos.git(runner, repo, "rebase", "-q", "main");
    String afterBaseMoved = git.ownDiffId(repo, repo, "main").orElseThrow();

    Files.writeString(repo.resolve("a.txt"), "0\n1\n2\n3\n4\n5\n6\n7 changed again\n8\n");
    String afterOwnChange = git.ownDiffId(repo, repo, "main").orElseThrow();

    assertThat(afterBaseMoved).isEqualTo(before);
    assertThat(afterOwnChange).isNotEqualTo(before);
  }

  @Test
  void cannotIdentifyAnOwnDiffWhileNewFileIsUntracked() throws Exception {
    Path repo = dir.resolve("repo");
    Files.createDirectories(repo);
    GitRepos.git(runner, dir, "init", "-q", "-b", "main", repo.toString());
    Files.writeString(repo.resolve("A.java"), "class A {}");
    GitRepos.commit(runner, repo, "init");
    Files.writeString(repo.resolve("New.java"), "class New {}");

    assertThat(git.ownDiffId(repo, repo, "main")).isEmpty();
  }

  @Test
  void listsTheCommitsAndFilesTheTaskAddedSinceTheBase() throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("a.txt"), "base\n");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    Files.writeString(repo.resolve("a.txt"), "committed\n");
    GitRepos.git(runner, repo, "commit", "-qam", "ABC-42 Widen the column");

    assertThat(git.commitsSince(repo, repo, "main"))
        .contains("ABC-42 Widen the column")
        .doesNotContain("init");
    assertThat(git.statSince(repo, repo, "main")).contains("a.txt");
  }

  @Test
  void namesEveryChangedPathCommittedUncommittedNewAndBothSidesOfRenameButNotJagtsOwn()
      throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.createDirectories(repo.resolve("auth"));
    Files.writeString(repo.resolve("auth/Login.java"), "class Login {}\n");
    Files.writeString(repo.resolve("b.txt"), "base\n");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    GitRepos.git(runner, repo, "mv", "auth/Login.java", "Login.java");
    GitRepos.git(runner, repo, "commit", "-qm", "ABC-42 Move the login");
    Files.writeString(repo.resolve("b.txt"), "uncommitted\n");
    Files.writeString(repo.resolve("new ü.txt"), "fresh\n");
    Files.writeString(repo.resolve("task_context.md"), "the round brief");

    List<String> paths = git.changedPaths(repo, repo, "main");

    assertThat(paths)
        .containsExactlyInAnyOrder("auth/Login.java", "Login.java", "b.txt", "new ü.txt");
  }

  @Test
  void countsTheLinesTheTaskAddedToTheAgentFileCommittedAndNot() throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("AGENTS.md"), "build: ./gradlew test\n");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    Files.writeString(repo.resolve("AGENTS.md"), "build: ./gradlew test\nregistry: local\n");
    GitRepos.git(runner, repo, "commit", "-qam", "ABC-42 Name the registry");
    Files.writeString(
        repo.resolve("AGENTS.md"), "build: ./gradlew test\nregistry: local\ninfra: its own repo\n");
    Files.writeString(repo.resolve("other.md"), "not the agent file\n");

    int added = git.agentFileLinesAdded(repo, repo, "main");

    assertThat(added).isEqualTo(2);
  }

  @Test
  void readsBranchAsAheadOfItsTargetOnlyOnceItCarriesCommitTheTargetLacks() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");

    assertThat(git.aheadOfTarget(repo, repo, "main")).isFalse();

    Files.writeString(repo.resolve("f.txt"), "changed");
    GitRepos.commit(runner, repo, "ABC-42 Widen the column");

    assertThat(git.aheadOfTarget(repo, repo, "main")).isTrue();
  }

  @Test
  void readsBranchAsUnpublishedUntilItsRemoteBranchHoldsItsHead() throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("f.txt"), "base");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-42");

    assertThat(git.unpublished(repo, repo)).isTrue();

    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-42");

    assertThat(git.unpublished(repo, repo)).isFalse();

    Files.writeString(repo.resolve("f.txt"), "changed");
    GitRepos.git(runner, repo, "commit", "-qam", "ABC-42 Widen the column");

    assertThat(git.unpublished(repo, repo)).isTrue();
  }

  @Test
  void namesTheCommitTheRemoteBranchHoldsOnceThePushLanded() throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("f.txt"), "base");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-42");

    assertThat(git.pushedTip(repo, repo)).isEmpty();

    GitRepos.git(runner, repo, "push", "-q", "origin", "ABC-42");
    String head = GitRepos.git(runner, repo, "rev-parse", "HEAD").stdout().strip();

    assertThat(git.pushedTip(repo, repo)).contains(head);
  }

  @Test
  void readsOneBranchNamePerLineIgnoringBlanks() {
    assertThat(WorktreeInspection.branchNames("ABC-40\nABC-41\n\n  main  \n"))
        .containsExactly("ABC-40", "ABC-41", "main");
    assertThat(WorktreeInspection.branchNames("")).isEmpty();
  }

  @Test
  void identifiesTheOwnDiffWhileOnlyJagtsOwnFilesAreUntracked() throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("a.txt"), "1\n");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    Files.writeString(repo.resolve("task_context.md"), "the round brief");

    assertThat(git.ownDiffId(repo, repo, "main")).isPresent();
  }

  @Test
  void countsNoLinesForAnAgentFileThatTurnedBinary() throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("AGENTS.md"), "build: ./gradlew test\n");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    Files.write(repo.resolve("AGENTS.md"), new byte[] {0, 1, 2, 0});

    int added = git.agentFileLinesAdded(repo, repo, "main");

    assertThat(added).isZero();
  }

  @Test
  void readsFileRenamedIntoJagtsOwnFileAsNoUncommittedWork() throws Exception {
    Path repo = GitRepos.bareClone(runner, dir, "repo");
    Files.writeString(repo.resolve("brief.md"), "the round brief");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "mv", "brief.md", "task_context.md");

    assertThat(git.hasUncommittedChanges(repo, repo)).isFalse();
  }

  @Test
  void readsBranchMergedByMergeCommitAsMerged() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-42");
    Files.writeString(repo.resolve("g.txt"), "work");
    GitRepos.commit(runner, repo, "work");
    GitRepos.git(runner, repo, "checkout", "-q", "main");
    GitRepos.git(runner, repo, "merge", "-q", "--no-ff", "-m", "Merge ABC-42", "ABC-42");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    GitRepos.git(runner, repo, "checkout", "-q", "ABC-42");

    assertThat(git.mergedIntoTarget(repo, repo, "main")).isTrue();
  }

  @Test
  void readsBranchWithCommitsTheTargetLacksAsNotMerged() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-42");
    Files.writeString(repo.resolve("g.txt"), "work");
    GitRepos.commit(runner, repo, "work");

    assertThat(git.mergedIntoTarget(repo, repo, "main")).isFalse();
  }

  @Test
  void readsBranchThatNeverDivergedAsNotMerged() throws Exception {
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-42");
    GitRepos.git(runner, repo, "checkout", "-q", "main");
    Files.writeString(repo.resolve("g.txt"), "later");
    GitRepos.commit(runner, repo, "later");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    GitRepos.git(runner, repo, "checkout", "-q", "ABC-42");

    assertThat(git.mergedIntoTarget(repo, repo, "main")).isFalse();
  }

  @Test
  void failsToReadMergedWhenTheTargetCannotBeFetched() throws Exception {
    Path repo = dir.resolve("repo");
    GitRepos.git(runner, dir, "init", "-q", "-b", "main", repo.toString());
    Files.writeString(repo.resolve("f.txt"), "base");
    GitRepos.commit(runner, repo, "init");

    assertThatThrownBy(() -> git.mergedIntoTarget(repo, repo, "main"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void readsWorktreeAheadOfItsTargetAndNeverUnpublishedWhereTheRepositoryHasNoRemote()
      throws Exception {
    Path repo = dir.resolve("repo");
    Files.createDirectories(repo);
    GitRepos.git(runner, dir, "init", "-q", "-b", "main", repo.toString());
    Files.writeString(repo.resolve("A.java"), "class A {}");
    GitRepos.commit(runner, repo, "init");
    GitRepos.git(runner, repo, "branch", "dev");
    GitRepos.git(runner, repo, "checkout", "-q", "-b", "ABC-1");
    Files.writeString(repo.resolve("B.java"), "class B {}");
    GitRepos.commit(runner, repo, "task");
    WorktreeInspection git =
        new WorktreeInspection(
            new GitCommands(
                runner,
                new LsofWorktreeProcesses(runner),
                path -> java.util.Optional.of(dev.jagt.orchestrator.task.repo.Remote.LOCAL),
                stopped -> {}),
            new StubAgentRuntime(StubAgentProperties.defaults()));

    assertThat(git.aheadOfTarget(repo, repo, "dev")).isTrue();
    assertThat(git.unpublished(repo, repo)).isFalse();
  }

  @Test
  void fetchesTheTargetOnceForTwoTasksOfOneRepositoryInOneSweep() throws Exception {
    Processes runner = spy(new ProcessRunner());
    Path repo = GitRepos.clone(runner, dir, "repo", "base");
    WorktreeInspection git =
        new WorktreeInspection(
            GitRepos.commands(runner), new StubAgentRuntime(StubAgentProperties.defaults()));

    try (OriginContext.Scope ignored = SweepFetches.create().open()) {
      git.mergedIntoTarget(repo, repo, "main");
      git.mergedIntoTarget(repo, repo, "main");
    }

    verify(runner, times(1)).run(any(), any(), argThat(command -> command.contains("fetch")));
  }
}
