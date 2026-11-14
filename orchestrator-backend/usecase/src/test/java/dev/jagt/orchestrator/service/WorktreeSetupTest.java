package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.worktree.SubAgentBriefing;
import dev.jagt.orchestrator.task.launch.NewTask;
import dev.jagt.orchestrator.task.repo.NewRepo;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorktreeSetupTest {

  @TempDir Path root;
  private final AgentRuntime runtime = mock(AgentRuntime.class);
  private final ConfigService config = mock(ConfigService.class);
  private final SubAgentBriefing briefing = mock(SubAgentBriefing.class);
  private WorktreeSetup setup;
  private Path project;
  private Path worktree;
  private Path knowledge;
  private NewRepo repo;
  private NewRepo deployRepo;
  private Path apiWorktree;
  private Path registryWorktree;
  private NewRepo api;
  private NewRepo registry;

  @BeforeEach
  void setUp() throws Exception {
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));
    setup = new WorktreeSetup(runtime, paths, config, briefing);
    project = Files.createDirectories(root.resolve("repo"));
    worktree = Files.createDirectories(root.resolve("ABC-1-repo"));
    knowledge = worktree.resolve("AGENTS.md");
    Path gitDir = Files.createDirectories(project.resolve(".git"));
    repo =
        new NewRepo(
            "repo",
            new ProjectConfig(project.toString(), "origin/main", null, List.of()),
            project,
            worktree,
            gitDir,
            "origin/main",
            "git@host:g/r.git",
            true);
    deployRepo =
        new NewRepo(
            "repo",
            new ProjectConfig(project.toString(), "origin/main", "dev", List.of()),
            project,
            worktree,
            gitDir,
            "origin/main",
            "git@host:g/r.git",
            true);
    Path apiProject = Files.createDirectories(root.resolve("api-repo"));
    apiWorktree = Files.createDirectories(root.resolve("ABC-1-api"));
    registryWorktree = Files.createDirectories(root.resolve("ABC-1-registry"));
    Path registryProject = Files.createDirectories(root.resolve("registry-repo"));
    api =
        new NewRepo(
            "api",
            new ProjectConfig(apiProject.toString(), "origin/main", null, List.of()),
            apiProject,
            apiWorktree,
            Files.createDirectories(apiProject.resolve(".git")),
            "origin/main",
            "u",
            true);
    registry =
        new NewRepo(
            "registry",
            new ProjectConfig(registryProject.toString(), "origin/main", null, List.of()),
            registryProject,
            registryWorktree,
            Files.createDirectories(registryProject.resolve(".git")),
            "origin/main",
            "u",
            false);
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(runtime.systemKnowledgeFile(worktree)).thenReturn(knowledge);
    when(runtime.systemKnowledgeFile(registryWorktree))
        .thenReturn(registryWorktree.resolve("AGENTS.md"));
    when(briefing.of(any(), any(), any(), any(), any(), any())).thenReturn("the briefing");
  }

  @Test
  void handsTheTaskItsInstructionsAndItsBriefingInTheWorktreeOfTheRepositoryTheSessionRunsIn()
      throws Exception {
    NewTask request = NewTask.builder("ABC-1", "repo").instructions("Implement ABC-1.").build();

    setup.fill(request, repo, List.of(repo));

    assertThat(worktree.resolve("task_context.md")).hasContent("Implement ABC-1.");
    assertThat(worktree.resolve("task_request.md")).hasContent("Implement ABC-1.");
    assertThat(knowledge).hasContent("the briefing");
  }

  @Test
  void letsTheSessionReachTheDeployWorktreeItsConflictsWouldWaitIn() throws Exception {
    setup.fill(NewTask.builder("ABC-1", "repo").build(), deployRepo, List.of(deployRepo));

    verify(runtime).admit(worktree, root.resolve("ABC-1-deploy"));
  }

  @Test
  void writesNoInstructionsIntoTheWorktreeOfRepositoryTheSessionDoesNotRunIn() throws Exception {
    NewRepo notPrimary = repo.besideTheSession();
    NewTask request = NewTask.builder("ABC-1", "repo").instructions("Implement ABC-1.").build();

    setup.fill(request, notPrimary, List.of(notPrimary));

    assertThat(worktree.resolve("task_context.md")).doesNotExist();
    assertThat(worktree.resolve("task_request.md")).doesNotExist();
  }

  @Test
  void writesNoInstructionsWhereTheTaskCarriesNone() throws Exception {
    NewTask request = NewTask.builder("ABC-1", "repo").instructions(" ").build();

    setup.fill(request, repo, List.of(repo));

    assertThat(worktree.resolve("task_context.md")).doesNotExist();
  }

  @Test
  void equipsFreshWorktreeWithTheProjectsUntrackedFilesAndTheGuardsThatKeepItSafe()
      throws Exception {
    Files.writeString(
        Files.createDirectories(project.resolve(".vscode")).resolve("settings.json"), "{}");
    Files.writeString(Files.createDirectories(project.resolve("app")).resolve(".env"), "KEY=1");

    setup.fill(NewTask.builder("ABC-1", "repo").build(), repo, List.of(repo));

    assertThat(worktree.resolve(".vscode/settings.json")).hasContent("{}");
    assertThat(worktree.resolve("app/.env")).hasContent("KEY=1");
    assertThat(worktree.resolve(".jagt/hooks/pre-push")).exists();
    assertThat(repo.gitCommonDir().resolve("info/exclude")).content().contains("task_context.md");
    verify(runtime).provisionWorktree(any());
  }

  @Test
  void letsTheSessionEditTheWorktreeOfRepositoryAddedToItsTask() throws Exception {
    when(runtime.briefedFile(apiWorktree)).thenReturn(apiWorktree.resolve("AGENTS.md"));

    setup.attach(NewTask.builder("ABC-1", "registry").build(), registry, List.of(api, registry));

    verify(runtime).admit(apiWorktree, registryWorktree);
  }

  @Test
  void rewritesHeldWorktreesBriefingWhereJagtWroteItRatherThanAskingForFreshName()
      throws Exception {
    when(runtime.systemKnowledgeFile(apiWorktree))
        .thenThrow(new IllegalStateException("no name left"));
    when(runtime.briefedFile(apiWorktree)).thenReturn(apiWorktree.resolve("CLAUDE.local.md"));
    when(briefing.of(any(), any(), any(), any(), any(), any())).thenReturn("api sees both");

    setup.attach(NewTask.builder("ABC-1", "registry").build(), registry, List.of(api, registry));

    assertThat(apiWorktree.resolve("CLAUDE.local.md")).hasContent("api sees both");
  }

  @Test
  void writesTheBriefingOfEveryWorktreeAgainForTheSetThatHoldsTheAddedRepository()
      throws Exception {
    when(runtime.briefedFile(apiWorktree)).thenReturn(apiWorktree.resolve("AGENTS.md"));
    when(briefing.of(any(), eq(api), eq(List.of(api, registry)), any(), any(), any()))
        .thenReturn("api sees both");
    when(briefing.of(any(), eq(registry), eq(List.of(api, registry)), any(), any(), any()))
        .thenReturn("registry sees both");

    setup.attach(NewTask.builder("ABC-1", "registry").build(), registry, List.of(api, registry));

    assertThat(apiWorktree.resolve("AGENTS.md")).hasContent("api sees both");
    assertThat(registryWorktree.resolve("AGENTS.md")).hasContent("registry sees both");
  }

  @Test
  void plansInFolderBesideTheStateFileThatHoldsNoGit() {
    Path folder = root.resolve("scratch/ABC-1");
    when(runtime.systemKnowledgeFile(folder)).thenReturn(folder.resolve("AGENTS.md"));
    when(briefing.ofScratch(any(), any(), any(), any())).thenReturn("the scratch briefing");

    Path session =
        setup.scratch(NewTask.unplacedBuilder("ABC-1").instructions("Plan ABC-1.").build());

    verify(runtime).provisionWorktree(any());
    assertThat(session).isEqualTo(folder);
    assertThat(folder.resolve(".git")).doesNotExist();
    assertThat(folder.resolve("AGENTS.md")).hasContent("the scratch briefing");
    assertThat(folder.resolve("task_context.md")).hasContent("Plan ABC-1.");
  }
}
