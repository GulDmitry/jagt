package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.notify.Notifications;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorktreeOrphanScannerTest {

    @Test
    void findsALeftoverWorktreeAndCountsTheSecretsStillSittingInIt(@TempDir Path root) throws IOException {
        Path repo = Files.createDirectories(root.resolve("demo-repo"));
        Path orphan = Files.createDirectories(root.resolve("ABC-40-demo"));
        Files.createDirectories(orphan.resolve("app"));
        Files.writeString(orphan.resolve("app").resolve(".env"), "TOKEN=secret");
        Files.writeString(orphan.resolve("app").resolve("key.pem"), "-----BEGIN KEY-----");
        WorktreeOrphanScanner scanner = scannerFor(root, repo, List.of("**/.env", "**/*.pem"));

        var orphans = scanner.scan();

        assertThat(orphans).singleElement().satisfies(found -> {
            assertThat(found.path()).isEqualTo(orphan);
            assertThat(found.projectKey()).isEqualTo("demo");
            assertThat(found.secretFiles()).isEqualTo(2);
        });
    }

    @Test
    void leavesEveryRepositoryOfALiveTaskAloneNotJustItsFirst(@TempDir Path root) throws IOException {
        Path repo = Files.createDirectories(root.resolve("demo-repo"));
        Path agentRuns = Files.createDirectories(root.resolve("ABC-1-alpha"));
        Path alsoEdited = Files.createDirectories(root.resolve("ABC-1-demo"));
        WorktreeOrphanScanner scanner = scannerFor(root, repo, List.of("**/.env"),
                Map.of("ABC-1", TaskState.builder(List.of(TaskRepo.of("alpha", agentRuns.toString()),
                        TaskRepo.of("demo", alsoEdited.toString())), TaskStatus.IN_PROGRESS).build()));

        assertThat(scanner.scan()).isEmpty();
    }

    @Test
    void countsASecretLeftAtTheRootOfAnOrphanTheSameWayItWasCopiedThere(@TempDir Path root) throws IOException {
        Path repo = Files.createDirectories(root.resolve("demo-repo"));
        Path orphan = Files.createDirectories(root.resolve("ABC-42-demo"));
        Files.writeString(orphan.resolve(".env"), "TOKEN=secret");
        WorktreeOrphanScanner scanner = scannerFor(root, repo, List.of("**/.env"));

        assertThat(scanner.scan()).singleElement()
                .satisfies(found -> assertThat(found.secretFiles()).isEqualTo(1));
    }

    @Test
    void leavesTheWorktreeOfALiveTaskAlone(@TempDir Path root) throws IOException {
        Path repo = Files.createDirectories(root.resolve("demo-repo"));
        Path live = Files.createDirectories(root.resolve("ABC-41-demo"));
        WorktreeOrphanScanner scanner = scannerFor(root, repo, List.of("**/.env"),
                Map.of("ABC-41", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build()));

        assertThat(scanner.scan()).isEmpty();
    }

    @Test
    void staysSilentAtStartupWhenNothingIsRotting(@TempDir Path root) throws IOException {
        Path repo = Files.createDirectories(root.resolve("demo-repo"));
        Notifications notifications = mock(Notifications.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("demo", new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));

        new WorktreeOrphanScanner(config, stateWith(root, Map.of()), notifications).run();

        verifyNoInteractions(notifications);
    }

    /** Another project's worktree and unrelated directories are none of this project's business. */
    @Test
    void recognisesBothATaskWorktreeAndAnAbandonedDeployWorktree() {
        List<String> onDisk = List.of("ABC-40-demo", "ABC-41-demo", "ABC-41-deploy", "demo-repo",
                "something-else", "XYZ-1-other");

        Set<String> orphans = WorktreeOrphanScanner.orphanNames(onDisk, "demo",
                Set.of("ABC-41-demo", "ABC-41-deploy"));

        assertThat(orphans).containsExactly("ABC-40-demo");
    }

    @Test
    void treatsADeployLeftoverOfARetiredTaskAsAnOrphan() {
        Set<String> orphans = WorktreeOrphanScanner.orphanNames(
                List.of("ABC-40-deploy", "demo-repo"), "demo", Set.of());

        assertThat(orphans).containsExactly("ABC-40-deploy");
    }

    private static WorktreeOrphanScanner scannerFor(Path root, Path repo, List<String> copyGlobs) {
        return scannerFor(root, repo, copyGlobs, Map.of());
    }

    private static WorktreeOrphanScanner scannerFor(Path root, Path repo, List<String> copyGlobs,
                                                    Map<String, TaskState> tasks) {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("demo", new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(ConfigService.ConfigFile.WorktreeConfig.defaults().withCopyGlobs(copyGlobs)));
        return new WorktreeOrphanScanner(config, stateWith(root, tasks), mock(Notifications.class));
    }

    private static StateService stateWith(Path root, Map<String, TaskState> tasks) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        tasks.forEach(state::putTask);
        return state;
    }
}
