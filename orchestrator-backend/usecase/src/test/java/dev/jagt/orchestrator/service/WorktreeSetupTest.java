package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorktreeSetupTest {

    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final ConfigService config = mock(ConfigService.class);
    private final SubAgentBriefing briefing = mock(SubAgentBriefing.class);

    @Test
    void handsTheTaskItsInstructionsAndItsBriefingInTheWorktreeOfTheRepositoryTheSessionRunsIn(@TempDir Path root)
            throws Exception {
        Path project = Files.createDirectories(root.resolve("repo"));
        Path worktree = Files.createDirectories(root.resolve("ABC-1-repo"));
        Path knowledge = worktree.resolve("AGENTS.md");
        NewRepo repo = new NewRepo("repo", new ProjectConfig(project.toString(), "origin/main", null, List.of()),
                project, worktree, Files.createDirectories(project.resolve(".git")), "origin/main", "git@host:g/r.git", true);
        NewTask request = NewTask.builder("ABC-1", "repo").instructions("Implement ABC-1.").build();
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.systemKnowledgeFile(worktree)).thenReturn(knowledge);
        when(briefing.of(any(), any(), any(), any(), any(), any())).thenReturn("the briefing");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        new WorktreeSetup(runtime, paths, config, briefing).fill(request, repo, List.of(repo));

        assertThat(worktree.resolve("task_context.md")).hasContent("Implement ABC-1.");
        assertThat(worktree.resolve("task_request.md")).hasContent("Implement ABC-1.");
        assertThat(knowledge).hasContent("the briefing");
    }

    @Test
    void letsTheSessionReachTheDeployWorktreeItsConflictsWouldWaitIn(@TempDir Path root) throws Exception {
        Path project = Files.createDirectories(root.resolve("repo"));
        Path worktree = Files.createDirectories(root.resolve("ABC-1-repo"));
        NewRepo repo = new NewRepo("repo", new ProjectConfig(project.toString(), "origin/main", "dev", List.of()),
                project, worktree, Files.createDirectories(project.resolve(".git")), "origin/main", "git@host:g/r.git", true);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.systemKnowledgeFile(worktree)).thenReturn(worktree.resolve("AGENTS.md"));
        when(briefing.of(any(), any(), any(), any(), any(), any())).thenReturn("the briefing");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        new WorktreeSetup(runtime, paths, config, briefing).fill(NewTask.builder("ABC-1", "repo").build(), repo,
                List.of(repo));

        verify(runtime).admit(worktree, root.resolve("ABC-1-deploy"));
    }

    @Test
    void writesNoInstructionsIntoTheWorktreeOfARepositoryTheSessionDoesNotRunIn(@TempDir Path root) throws Exception {
        Path project = Files.createDirectories(root.resolve("repo"));
        Path worktree = Files.createDirectories(root.resolve("ABC-1-repo"));
        Path knowledge = worktree.resolve("AGENTS.md");
        NewRepo repo = new NewRepo("repo", new ProjectConfig(project.toString(), "origin/main", null, List.of()),
                project, worktree, Files.createDirectories(project.resolve(".git")), "origin/main", "git@host:g/r.git", false);
        NewTask request = NewTask.builder("ABC-1", "repo").instructions("Implement ABC-1.").build();
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.systemKnowledgeFile(worktree)).thenReturn(knowledge);
        when(briefing.of(any(), any(), any(), any(), any(), any())).thenReturn("the briefing");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        new WorktreeSetup(runtime, paths, config, briefing).fill(request, repo, List.of(repo));

        assertThat(worktree.resolve("task_context.md")).doesNotExist();
        assertThat(worktree.resolve("task_request.md")).doesNotExist();
    }

    @Test
    void writesNoInstructionsWhereTheTaskCarriesNone(@TempDir Path root) throws Exception {
        Path project = Files.createDirectories(root.resolve("repo"));
        Path worktree = Files.createDirectories(root.resolve("ABC-1-repo"));
        Path knowledge = worktree.resolve("AGENTS.md");
        NewRepo repo = new NewRepo("repo", new ProjectConfig(project.toString(), "origin/main", null, List.of()),
                project, worktree, Files.createDirectories(project.resolve(".git")), "origin/main", "git@host:g/r.git", true);
        NewTask request = NewTask.builder("ABC-1", "repo").instructions(" ").build();
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.systemKnowledgeFile(worktree)).thenReturn(knowledge);
        when(briefing.of(any(), any(), any(), any(), any(), any())).thenReturn("the briefing");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        new WorktreeSetup(runtime, paths, config, briefing).fill(request, repo, List.of(repo));

        assertThat(worktree.resolve("task_context.md")).doesNotExist();
    }

    @Test
    void equipsAFreshWorktreeWithTheProjectsUntrackedFilesAndTheGuardsThatKeepItSafe(@TempDir Path root)
            throws Exception {
        Path project = Files.createDirectories(root.resolve("repo"));
        Files.writeString(Files.createDirectories(project.resolve(".vscode")).resolve("settings.json"), "{}");
        Files.writeString(Files.createDirectories(project.resolve("app")).resolve(".env"), "KEY=1");
        Path worktree = Files.createDirectories(root.resolve("ABC-1-repo"));
        Path gitDir = Files.createDirectories(project.resolve(".git"));
        NewRepo repo = new NewRepo("repo", new ProjectConfig(project.toString(), "origin/main", null, List.of()),
                project, worktree, gitDir, "origin/main", "git@host:g/r.git", true);
        NewTask request = NewTask.builder("ABC-1", "repo").build();
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.systemKnowledgeFile(worktree)).thenReturn(worktree.resolve("AGENTS.md"));
        when(briefing.of(any(), any(), any(), any(), any(), any())).thenReturn("the briefing");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        new WorktreeSetup(runtime, paths, config, briefing).fill(request, repo, List.of(repo));

        assertThat(worktree.resolve(".vscode/settings.json")).hasContent("{}");
        assertThat(worktree.resolve("app/.env")).hasContent("KEY=1");
        assertThat(worktree.resolve(".jagt/hooks/pre-push")).exists();
        assertThat(gitDir.resolve("info/exclude")).content().contains("task_context.md");
        verify(runtime).provisionWorktree(any());
    }

    @Test
    void letsTheSessionEditTheWorktreeOfARepositoryAddedToItsTask(@TempDir Path root) throws Exception {
        Path apiProject = Files.createDirectories(root.resolve("api-repo"));
        Path registryProject = Files.createDirectories(root.resolve("registry-repo"));
        Path apiWorktree = Files.createDirectories(root.resolve("ABC-1-api"));
        Path registryWorktree = Files.createDirectories(root.resolve("ABC-1-registry"));
        NewRepo api = new NewRepo("api", new ProjectConfig(apiProject.toString(), "origin/main", null, List.of()),
                apiProject, apiWorktree, Files.createDirectories(apiProject.resolve(".git")), "origin/main", "u", true);
        NewRepo registry = new NewRepo("registry", new ProjectConfig(registryProject.toString(), "origin/main", null,
                List.of()), registryProject, registryWorktree, Files.createDirectories(registryProject.resolve(".git")),
                "origin/main", "u", false);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.systemKnowledgeFile(apiWorktree)).thenReturn(apiWorktree.resolve("AGENTS.md"));
        when(runtime.systemKnowledgeFile(registryWorktree)).thenReturn(registryWorktree.resolve("AGENTS.md"));
        when(briefing.of(any(), any(), any(), any(), any(), any())).thenReturn("the briefing");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        new WorktreeSetup(runtime, paths, config, briefing).attach(NewTask.builder("ABC-1", "registry").build(),
                registry, List.of(api, registry));

        verify(runtime).admit(apiWorktree, registryWorktree);
    }

    @Test
    void writesTheBriefingOfEveryWorktreeAgainForTheSetThatHoldsTheAddedRepository(@TempDir Path root)
            throws Exception {
        Path apiProject = Files.createDirectories(root.resolve("api-repo"));
        Path registryProject = Files.createDirectories(root.resolve("registry-repo"));
        Path apiWorktree = Files.createDirectories(root.resolve("ABC-1-api"));
        Path registryWorktree = Files.createDirectories(root.resolve("ABC-1-registry"));
        NewRepo api = new NewRepo("api", new ProjectConfig(apiProject.toString(), "origin/main", null, List.of()),
                apiProject, apiWorktree, Files.createDirectories(apiProject.resolve(".git")), "origin/main", "u", true);
        NewRepo registry = new NewRepo("registry", new ProjectConfig(registryProject.toString(), "origin/main", null,
                List.of()), registryProject, registryWorktree, Files.createDirectories(registryProject.resolve(".git")),
                "origin/main", "u", false);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        when(runtime.systemKnowledgeFile(apiWorktree)).thenReturn(apiWorktree.resolve("AGENTS.md"));
        when(runtime.systemKnowledgeFile(registryWorktree)).thenReturn(registryWorktree.resolve("AGENTS.md"));
        when(briefing.of(any(), eq(api), eq(List.of(api, registry)), any(), any(), any())).thenReturn("api sees both");
        when(briefing.of(any(), eq(registry), eq(List.of(api, registry)), any(), any(), any()))
                .thenReturn("registry sees both");
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString()));

        new WorktreeSetup(runtime, paths, config, briefing).attach(NewTask.builder("ABC-1", "registry").build(),
                registry, List.of(api, registry));

        assertThat(apiWorktree.resolve("AGENTS.md")).hasContent("api sees both");
        assertThat(registryWorktree.resolve("AGENTS.md")).hasContent("registry sees both");
    }
}
