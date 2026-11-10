package dev.jagt.orchestrator.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.GitCommands;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.WorktreeOrphans;
import dev.jagt.orchestrator.task.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class WorktreeOrphanScannerTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @Test
  void staysSilentAtStartupWhenNothingIsRotting(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Notifications notifications = mock(Notifications.class);
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));

    new WorktreeOrphanScanner(
            new WorktreeOrphans(
                config,
                new StateService(
                    new JsonMapper(),
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults()
                            .withRoot(root.toString())
                            .withStateFile(root.resolve("state.json").toString())),
                    reducer),
                mock(GitCommands.class)),
            notifications)
        .run();

    verifyNoInteractions(notifications);
  }

  @Test
  void deletesAnOrphanHoldingNothingButTheFilesTheIdeWroteBackAfterTheCheckoutWasGone(
      @TempDir Path root) throws IOException {
    final Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path husk = Files.createDirectories(root.resolve("ABC-42-demo"));
    Files.createDirectories(husk.resolve(".idea"));
    Files.writeString(husk.resolve(".idea").resolve("workspace.xml"), "<project/>");
    Files.createDirectories(husk.resolve(".run"));
    Files.writeString(husk.resolve(".run").resolve("app.run.xml"), "<component/>");
    Files.createFile(husk.resolve("N"));
    Notifications notifications = mock(Notifications.class);
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));

    new WorktreeOrphanScanner(
            new WorktreeOrphans(
                config,
                new StateService(
                    new JsonMapper(),
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults()
                            .withRoot(root.toString())
                            .withStateFile(root.resolve("state.json").toString())),
                    reducer),
                mock(GitCommands.class)),
            notifications)
        .run();

    assertThat(husk).doesNotExist();
    verifyNoInteractions(notifications);
  }

  @Test
  void deletesAnOrphanHoldingTheIdesFilesAndWhatTheOsWroteBesideThem(@TempDir Path root)
      throws IOException {
    final Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path husk = Files.createDirectories(root.resolve("ABC-42-demo"));
    Files.createDirectories(husk.resolve(".idea"));
    Files.writeString(husk.resolve(".idea").resolve("workspace.xml"), "<project/>");
    Files.writeString(husk.resolve(".DS_Store"), "Bud1");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of()))));

    new WorktreeOrphanScanner(
            new WorktreeOrphans(
                config,
                new StateService(
                    new JsonMapper(),
                    new OrchestratorPaths(
                        OrchestratorProperties.defaults()
                            .withRoot(root.toString())
                            .withStateFile(root.resolve("state.json").toString())),
                    reducer),
                mock(GitCommands.class)),
            mock(Notifications.class))
        .run();

    assertThat(husk).doesNotExist();
  }

  @Test
  void keepsAnOrphanStillHoldingItsCheckout(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-43-demo"));
    Files.writeString(
        orphan.resolve(".git"), "gitdir: " + repo.resolve(".git/worktrees/ABC-43-demo") + "\n");
    Files.createDirectories(orphan.resolve(".idea"));
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    WorktreeOrphanScanner scanner =
        new WorktreeOrphanScanner(
            new WorktreeOrphans(config, state, mock(GitCommands.class)), mock(Notifications.class));

    scanner.run();

    assertThat(orphan).exists();
  }

  @Test
  void keepsAnOrphanHoldingFileWithSomethingInIt(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-44-demo"));
    Files.writeString(orphan.resolve("Notes.md"), "half a day of work");
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    WorktreeOrphanScanner scanner =
        new WorktreeOrphanScanner(
            new WorktreeOrphans(config, state, mock(GitCommands.class)), mock(Notifications.class));

    scanner.run();

    assertThat(orphan).exists();
  }

  @Test
  void keepsAnOrphanHoldingCopiedSecretEvenWhenItIsEmpty(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-45-demo"));
    Files.createFile(orphan.resolve(".env"));
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    WorktreeOrphanScanner scanner =
        new WorktreeOrphanScanner(
            new WorktreeOrphans(config, state, mock(GitCommands.class)), mock(Notifications.class));

    scanner.run();

    assertThat(orphan).exists();
  }

  @Test
  void pingsTheCountOfCopiedSecretsLeftInAnOrphan(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-48-demo"));
    Files.writeString(orphan.resolve(".env"), "TOKEN=secret");
    Notifications notifications = mock(Notifications.class);
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);

    new WorktreeOrphanScanner(
            new WorktreeOrphans(config, state, mock(GitCommands.class)), notifications)
        .run();

    verify(notifications)
        .send(
            argThat(
                sent ->
                    "1 orphaned worktree(s)".equals(sent.title())
                        && "1 copied secret file(s) left on disk — `worktrees` lists them"
                            .equals(sent.body())));
  }

  @Test
  void pingsThatAnOrphanHoldingWorkWasLeftByCrashedTask(@TempDir Path root) throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path orphan = Files.createDirectories(root.resolve("ABC-48-demo"));
    Files.writeString(orphan.resolve("notes.txt"), "unsaved work");
    Notifications notifications = mock(Notifications.class);
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);

    new WorktreeOrphanScanner(
            new WorktreeOrphans(config, state, mock(GitCommands.class)), notifications)
        .run();

    verify(notifications)
        .send(
            argThat(
                sent ->
                    "1 orphaned worktree(s)".equals(sent.title())
                        && "left over from a crashed or abandoned task — `worktrees` lists them"
                            .equals(sent.body())));
  }

  @Test
  void reportsAnEmptyHuskItCouldNotDeleteInsteadOfCallingItGone(@TempDir Path root)
      throws IOException {
    Path repo = Files.createDirectories(root.resolve("demo-repo"));
    Path husk = Files.createDirectories(root.resolve("ABC-49-demo"));
    Files.createFile(husk.resolve("empty"));
    Files.setPosixFilePermissions(husk, PosixFilePermissions.fromString("r-xr-xr-x"));
    Notifications notifications = mock(Notifications.class);
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of(
                        "demo",
                        new ProjectConfig(repo.toString(), "origin/main", "dev", List.of())))
                .withWorktree(
                    ConfigService.ConfigFile.WorktreeConfig.defaults()
                        .withCopyGlobs(List.of("**/.env"))));
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);

    new WorktreeOrphanScanner(
            new WorktreeOrphans(config, state, mock(GitCommands.class)), notifications)
        .run();

    verify(notifications).send(argThat(sent -> "1 orphaned worktree(s)".equals(sent.title())));
  }
}
