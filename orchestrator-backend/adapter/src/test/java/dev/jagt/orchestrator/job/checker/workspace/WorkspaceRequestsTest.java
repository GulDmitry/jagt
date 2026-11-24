package dev.jagt.orchestrator.job.checker.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.CodeHost;
import dev.jagt.orchestrator.port.codehost.RequestDraft;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.git.GitCommands;
import dev.jagt.orchestrator.service.git.GitRepos;
import dev.jagt.orchestrator.service.git.GitWorktrees;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceRequestsTest {

  private static final String BRANCH = "jagt/acceptance-acceptance";

  @TempDir Path dir;
  private final Processes runner = new ProcessRunner();
  private final ConfigService config = mock(ConfigService.class);
  private final CodeHost host = mock(CodeHost.class);
  private WorkspaceRequests requests;
  private Path repo;
  private Path workspace;

  @BeforeEach
  void setUp() throws Exception {
    repo = GitRepos.clone(runner, dir, "repo", "base");
    workspace = repo.resolve("workspace");
    Files.createDirectories(workspace.resolve("flows"));
    Files.writeString(workspace.resolve("flows/login.md"), "login flow");
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "repo",
                        new ProjectConfig(repo.toString(), "origin/main", null, List.of()))));
    GitCommands commands = GitRepos.commands(runner);
    requests =
        new WorkspaceRequests(
            config,
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(dir.toString())
                    .withData(dir.resolve("data").toString())),
            new GitWorktrees(commands),
            host);
    when(host.openRequest(eq("repo"), any(), any()))
        .thenReturn(
            new Answer<>(
                Optional.of("https://host.test/repo/-/merge_requests/7"), TokenUsage.NONE));
    when(host.openRequestOf(eq("repo"), any(), eq(BRANCH)))
        .thenReturn(
            new Answer<>(
                Optional.of("https://host.test/repo/-/merge_requests/7"), TokenUsage.NONE));
  }

  @Test
  void oneRequestCarriesNewFlowAndTheBaseBranchIsUntouched() {
    final String baseBefore = GitRepos.git(runner, repo, "rev-parse", "origin/main").stdout();

    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(
            GitRepos.git(runner, repo, "ls-tree", "-r", "--name-only", "origin/" + BRANCH).stdout())
        .contains("workspace/flows/login.md");
    assertThat(GitRepos.git(runner, repo, "rev-parse", "origin/main").stdout())
        .isEqualTo(baseBefore);
    verify(host)
        .openRequest(
            eq("repo"),
            any(),
            eq(
                new RequestDraft(
                    BRANCH,
                    "main",
                    "Acceptance acceptance workspace after ABC-42",
                    "Written by the acceptance role of jagt. Review before merging.")));
  }

  @Test
  void noCheckoutAndNoLocalBranchRemainAfterwards() {
    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(dir.resolve("data/role-branches/repo-" + BRANCH.replace('/', '-'))).doesNotExist();
    assertThat(GitRepos.git(runner, repo, "branch", "--list", BRANCH).stdout()).isBlank();
  }

  @Test
  void laterCheckAddsOneCommitToTheSameBranch() throws Exception {
    requests.propose("acceptance", workspace, "ABC-42");
    Files.writeString(workspace.resolve("flows/login.md"), "login flow, changed");

    requests.propose("acceptance", workspace, "ABC-43");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(
            GitRepos.git(runner, repo, "rev-list", "--count", "origin/main..origin/" + BRANCH)
                .stdout()
                .trim())
        .isEqualTo("2");
    verify(host, times(2)).openRequest(eq("repo"), any(), any());
  }

  @Test
  void checkThatChangedNothingAddsNoCommit() {
    requests.propose("acceptance", workspace, "ABC-42");

    requests.propose("acceptance", workspace, "ABC-43");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(
            GitRepos.git(runner, repo, "rev-list", "--count", "origin/main..origin/" + BRANCH)
                .stdout()
                .trim())
        .isEqualTo("1");
  }

  @Test
  void workspaceEqualToTheBaseBranchPushesAndOpensNothing() throws Exception {
    GitRepos.commit(runner, repo, "workspace");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");

    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(GitRepos.git(runner, repo, "ls-remote", "origin", BRANCH).stdout()).isBlank();
    verify(host, never()).openRequest(eq("repo"), any(), any());
  }

  @Test
  void gitignoredFileIsNeverCommitted() throws Exception {
    Files.writeString(workspace.resolve(".gitignore"), ".env\n");
    Files.writeString(workspace.resolve(".env"), "LOGIN=secret");

    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(
            GitRepos.git(runner, repo, "ls-tree", "-r", "--name-only", "origin/" + BRANCH).stdout())
        .contains("workspace/.gitignore")
        .doesNotContain(".env\n");
  }

  @Test
  void branchMergedIntoTheBaseRestartsFromTheBaseAndCarriesNoOneElsesFiles() throws Exception {
    requests.propose("acceptance", workspace, "ABC-42");
    Path other = GitRepos.siblingClone(runner, dir, GitRepos.originOf(dir, "repo"), "other");
    GitRepos.git(runner, other, "merge", "-q", "--no-ff", "-m", "merge", "origin/" + BRANCH);
    Files.writeString(other.resolve("other.txt"), "another change");
    GitRepos.git(runner, other, "add", "other.txt");
    GitRepos.git(runner, other, "commit", "-qm", "other");
    GitRepos.git(runner, other, "push", "-q", "origin", "main");
    Files.writeString(workspace.resolve("flows/login.md"), "login flow, changed");

    requests.propose("acceptance", workspace, "ABC-43");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(
            GitRepos.git(runner, repo, "diff", "--name-only", "origin/main", "origin/" + BRANCH)
                .stdout()
                .trim())
        .isEqualTo("workspace/flows/login.md");
  }

  @Test
  void humansLocalBranchOfTheSameNameWithUnpushedCommitSurvivesCheck() throws Exception {
    Path human = dir.resolve("human");
    GitRepos.addWorktree(runner, repo, human, BRANCH);
    Files.writeString(human.resolve("mine.txt"), "unpushed");
    GitRepos.commit(runner, human, "mine");
    GitRepos.git(runner, repo, "worktree", "remove", "--force", human.toString());
    final String before = GitRepos.git(runner, repo, "rev-parse", BRANCH).stdout();

    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(GitRepos.git(runner, repo, "rev-parse", BRANCH).stdout()).isEqualTo(before);
    assertThat(
            GitRepos.git(runner, repo, "ls-tree", "-r", "--name-only", "origin/" + BRANCH).stdout())
        .contains("workspace/flows/login.md");
  }

  @Test
  void humansWorktreeHoldingTheBranchOfTheSameNameDoesNotBlockCheck() {
    GitRepos.addWorktree(runner, repo, dir.resolve("human"), BRANCH);

    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(
            GitRepos.git(runner, repo, "ls-tree", "-r", "--name-only", "origin/" + BRANCH).stdout())
        .contains("workspace/flows/login.md");
  }

  @Test
  void symbolicLinkToFileOutsideTheRepositoryIsNeverCommitted() throws Exception {
    Path outside = Files.writeString(dir.resolve("outside.txt"), "not for the repository");
    Files.createSymbolicLink(workspace.resolve("flows/outside.md"), outside);

    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(
            GitRepos.git(runner, repo, "ls-tree", "-r", "--name-only", "origin/" + BRANCH).stdout())
        .contains("workspace/flows/login.md")
        .doesNotContain("outside.md");
  }

  @Test
  void branchSquashMergedAndLeftStandingRestartsFromTheBaseWhereNoRequestIsOpen() throws Exception {
    requests.propose("acceptance", workspace, "ABC-42");
    Path other = GitRepos.siblingClone(runner, dir, GitRepos.originOf(dir, "repo"), "other");
    GitRepos.git(runner, other, "merge", "-q", "--squash", "origin/" + BRANCH);
    GitRepos.git(runner, other, "commit", "-qm", "squashed");
    GitRepos.git(runner, other, "push", "-q", "origin", "main");
    Files.writeString(workspace.resolve("flows/login.md"), "login flow, changed");
    when(host.openRequestOf(eq("repo"), any(), eq(BRANCH)))
        .thenReturn(new Answer<>(Optional.of(""), TokenUsage.NONE));

    requests.propose("acceptance", workspace, "ABC-43");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(
            GitRepos.git(runner, repo, "rev-list", "--count", "origin/main..origin/" + BRANCH)
                .stdout()
                .trim())
        .isEqualTo("1");
  }

  @Test
  void requestLookupThatFailsPushesNothing() {
    when(host.openRequestOf(eq("repo"), any(), eq(BRANCH)))
        .thenReturn(Answer.failed(TokenUsage.NONE, "glab exited 1"));

    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(GitRepos.git(runner, repo, "ls-remote", "origin", BRANCH).stdout()).isBlank();
    verify(host, never()).openRequest(eq("repo"), any(), any());
  }

  @Test
  void folderOutsideEveryProjectIsLeftAlone() throws Exception {
    Path outside = Files.createDirectories(dir.resolve("elsewhere"));

    requests.propose("acceptance", outside, "ABC-42");

    verifyNoInteractions(host);
    assertThat(GitRepos.git(runner, repo, "ls-remote", "origin", BRANCH).stdout()).isBlank();
  }

  @Test
  void projectsOwnRootIsNotWorkspaceInsideIt() {
    requests.propose("acceptance", repo, "ABC-42");

    verifyNoInteractions(host);
  }

  @Test
  void projectKeptOnThisMachineGetsNothing() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "repo",
                        new ProjectConfig(
                            new ProjectConfig.Repository(
                                repo.toString(), List.of(), null, List.of(), null, null),
                            new ProjectConfig.Branches("main", null, "none"),
                            ProjectConfig.CodeHost.defaults(),
                            new ProjectConfig.Verify(List.of(), null),
                            ProjectConfig.Setup.NONE))));

    requests.propose("acceptance", workspace, "ABC-42");

    verifyNoInteractions(host);
    assertThat(GitRepos.git(runner, repo, "ls-remote", "origin", BRANCH).stdout()).isBlank();
  }

  @Test
  void branchNamedLikeTheBaseBranchIsRefused() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "repo",
                        new ProjectConfig(repo.toString(), "origin/" + BRANCH, null, List.of()))));

    requests.propose("acceptance", workspace, "ABC-42");

    verify(host, never()).openRequest(eq("repo"), any(), any());
    assertThat(GitRepos.git(runner, repo, "ls-remote", "origin", BRANCH).stdout()).isBlank();
  }

  @Test
  void branchNamedLikeTheDeployBranchIsRefused() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "repo",
                        new ProjectConfig(repo.toString(), "origin/main", BRANCH, List.of()))));

    requests.propose("acceptance", workspace, "ABC-42");

    verify(host, never()).openRequest(eq("repo"), any(), any());
    assertThat(GitRepos.git(runner, repo, "ls-remote", "origin", BRANCH).stdout()).isBlank();
  }

  @Test
  void hostThatRefusesTheRequestLeavesTheBranchPushedForTheNextCheck() {
    when(host.openRequest(eq("repo"), any(), any()))
        .thenReturn(Answer.failed(TokenUsage.NONE, "glab exited 1"));

    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(
            GitRepos.git(runner, repo, "ls-tree", "-r", "--name-only", "origin/" + BRANCH).stdout())
        .contains("workspace/flows/login.md");
  }

  @Test
  void hostThatOpensNoRequestStillGetsTheBranch() {
    when(host.openRequest(eq("repo"), any(), any())).thenReturn(Answer.unavailable());

    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(
            GitRepos.git(runner, repo, "ls-tree", "-r", "--name-only", "origin/" + BRANCH).stdout())
        .contains("workspace/flows/login.md");
  }

  @Test
  void pushThatFailsOpensNoRequest() {
    GitRepos.refusePushes(runner, repo, dir);

    requests.propose("acceptance", workspace, "ABC-42");

    verify(host, never()).openRequest(eq("repo"), any(), any());
  }

  @Test
  void deletedFlowLeavesTheBranchToo() throws Exception {
    requests.propose("acceptance", workspace, "ABC-42");
    Files.writeString(workspace.resolve("flows/logout.md"), "logout flow");
    requests.propose("acceptance", workspace, "ABC-43");
    Files.delete(workspace.resolve("flows/login.md"));

    requests.propose("acceptance", workspace, "ABC-44");

    assertThat(
            GitRepos.git(runner, repo, "ls-tree", "-r", "--name-only", "origin/" + BRANCH).stdout())
        .contains("workspace/flows/logout.md")
        .doesNotContain("workspace/flows/login.md");
  }

  @Test
  void trackedFileDeletedFromTheWorkspaceLeavesTheBranchToo() throws Exception {
    Files.writeString(workspace.resolve("flows/logout.md"), "logout flow");
    GitRepos.commit(runner, repo, "workspace");
    GitRepos.git(runner, repo, "push", "-q", "origin", "main");
    Files.delete(workspace.resolve("flows/login.md"));
    Files.writeString(workspace.resolve("flows/logout.md"), "logout flow, changed");

    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(
            GitRepos.git(runner, repo, "ls-tree", "-r", "--name-only", "origin/" + BRANCH).stdout())
        .contains("workspace/flows/logout.md")
        .doesNotContain("workspace/flows/login.md");
  }

  @Test
  void checkoutLeftByCrashedRunIsReplaced() throws Exception {
    Path stale =
        Files.createDirectories(dir.resolve("data/role-branches/repo-" + BRANCH.replace('/', '-')));
    Files.writeString(stale.resolve("stale.txt"), "left behind");

    requests.propose("acceptance", workspace, "ABC-42");

    assertThat(
            GitRepos.git(runner, repo, "ls-tree", "-r", "--name-only", "origin/" + BRANCH).stdout())
        .contains("workspace/flows/login.md");
  }

  @Test
  void projectWhosePathIsGoneDoesNotHideTheOneHoldingTheFolder() {
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withProjects(
                    new TreeMap<>(
                        Map.of(
                            "gone",
                            new ProjectConfig(
                                dir.resolve("missing").toString(), "origin/main", null, List.of()),
                            "repo",
                            new ProjectConfig(repo.toString(), "origin/main", null, List.of())))));

    requests.propose("acceptance", workspace, "ABC-42");

    verify(host).openRequest(eq("repo"), any(), any());
  }

  @Test
  void localCommitOfTheHumansCheckoutIsNotCarriedOntoTheBranch() throws Exception {
    Files.writeString(repo.resolve("local.txt"), "unpushed");
    GitRepos.git(runner, repo, "add", "local.txt");
    GitRepos.git(runner, repo, "commit", "-qm", "local");
    when(host.openRequestOf(eq("repo"), any(), eq(BRANCH)))
        .thenReturn(new Answer<>(Optional.of(""), TokenUsage.NONE));

    requests.propose("acceptance", workspace, "ABC-42");

    GitRepos.git(runner, repo, "fetch", "-q");
    assertThat(
            GitRepos.git(runner, repo, "diff", "--name-only", "origin/main", "origin/" + BRANCH)
                .stdout()
                .trim())
        .isEqualTo("workspace/flows/login.md");
  }
}
