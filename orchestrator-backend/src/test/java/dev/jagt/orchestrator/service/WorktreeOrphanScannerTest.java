package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.model.ProjectConfig;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.platform.UserNotifier;
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
    void reportsALeftoverWorktreeAndTheSecretsStillSittingInIt(@TempDir Path root) throws IOException {
        Path repo = Files.createDirectories(root.resolve("demo-repo"));
        Path orphan = Files.createDirectories(root.resolve("ABC-40-demo"));
        Files.createDirectories(orphan.resolve("app"));
        // `**/.env` needs a directory component to match — a root-level .env is NOT copied by that glob
        // either, which is how copyLocalFiles behaves in production.
        Files.writeString(orphan.resolve("app").resolve(".env"), "TOKEN=secret");
        Files.writeString(orphan.resolve("app").resolve("key.pem"), "-----BEGIN KEY-----");
        WorktreeOrphanScanner scanner = scannerFor(root, repo, List.of("**/.env", "**/*.pem"));

        var orphans = scanner.scan();

        assertThat(orphans).singleElement().satisfies(found -> {
            assertThat(found.path()).isEqualTo(orphan);
            assertThat(found.projectKey()).isEqualTo("demo");
            assertThat(found.secretFiles()).isEqualTo(2);
        });
        assertThat(scanner.report()).contains("ABC-40-demo", "2 copied secret file(s)", "never deletes");
    }

    @Test
    void leavesTheWorktreeOfALiveTaskAloneAndSaysSoWhenThereIsNothingToReport(@TempDir Path root)
            throws IOException {
        Path repo = Files.createDirectories(root.resolve("demo-repo"));
        Path live = Files.createDirectories(root.resolve("ABC-41-demo"));
        WorktreeOrphanScanner scanner = scannerFor(root, repo, List.of("**/.env"),
                Map.of("ABC-41", TaskState.builder("demo", live.toString(), TaskStatus.IN_PROGRESS).build()));

        assertThat(scanner.scan()).isEmpty();
        assertThat(scanner.report()).contains("no orphaned worktrees");
    }

    @Test
    void staysSilentAtStartupWhenNothingIsRotting(@TempDir Path root) throws IOException {
        Path repo = Files.createDirectories(root.resolve("demo-repo"));
        UserNotifier notifier = mock(UserNotifier.class);
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withProjects(Map.of("demo", new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));

        new WorktreeOrphanScanner(config, stateWith(root, Map.of()), notifier).reportOnStartup();

        verifyNoInteractions(notifier);
    }

    @Test
    void recognisesBothATaskWorktreeAndAnAbandonedDeployWorktree() {
        List<String> onDisk = List.of("ABC-40-demo", "ABC-41-demo", "ABC-41-deploy", "demo-repo",
                "something-else", "XYZ-1-other");

        Set<String> orphans = WorktreeOrphanScanner.orphanNames(onDisk, "demo", Set.of("ABC-41-demo", "ABC-41-deploy"));

        // ABC-41 is live, so neither its worktree nor its deploy leftover is an orphan; another project's
        // worktree and unrelated directories are none of this project's business.
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
        return new WorktreeOrphanScanner(config, stateWith(root, tasks), mock(UserNotifier.class));
    }

    private static StateService stateWith(Path root, Map<String, TaskState> tasks) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        tasks.forEach(state::putTask);
        return state;
    }
}
