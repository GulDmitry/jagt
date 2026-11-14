package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.job.TestWorktreeOrphanScannerBuilder.worktreeOrphanScanner;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.git.GitCommands;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WorktreeOrphanScannerTest {

  private final Notifications notifications = mock(Notifications.class);
  private final ConfigService config = mock(ConfigService.class);

  @TempDir Path root;

  private Path repo;
  private WorktreeOrphanScanner scanner;

  @BeforeEach
  void repoWithWorktreesBesideIt() throws IOException {
    repo = Files.createDirectories(root.resolve("demo-repo"));
    StateService state = stateService().withRoot(root).build();
    when(config.load()).thenReturn(worktreeOrphanScanner().withRepo(repo).config());
    scanner = worktreeOrphanScanner().build(config, state, mock(GitCommands.class), notifications);
  }

  static Stream<Arguments> orphansLeftOnDisk() {
    return Stream.of(
        arguments(
            "a copied secret",
            ".env",
            "TOKEN=secret",
            "1 copied secret file(s) left on disk — `worktrees` lists them"),
        arguments(
            "work of a crashed task",
            "notes.txt",
            "unsaved work",
            "left over from a crashed or abandoned task — `worktrees` lists them"));
  }

  static Stream<Arguments> orphansKept() {
    return Stream.of(
        arguments("file with something in it", "Notes.md", "half a day of work"),
        arguments("copied secret even when it is empty", ".env", ""));
  }

  @Test
  void staysSilentAtStartupWhenNothingIsRotting() {
    scanner.run();

    verifyNoInteractions(notifications);
  }

  @Test
  void deletesAnOrphanHoldingNothingButTheFilesTheIdeWroteBackAfterTheCheckoutWasGone()
      throws IOException {
    Path husk = Files.createDirectories(root.resolve("ABC-42-demo"));
    Files.createDirectories(husk.resolve(".idea"));
    Files.writeString(husk.resolve(".idea").resolve("workspace.xml"), "<project/>");
    Files.createDirectories(husk.resolve(".run"));
    Files.writeString(husk.resolve(".run").resolve("app.run.xml"), "<component/>");
    Files.createFile(husk.resolve("N"));

    scanner.run();

    assertThat(husk).doesNotExist();
    verifyNoInteractions(notifications);
  }

  @Test
  void deletesAnOrphanHoldingTheIdesFilesAndWhatTheOsWroteBesideThem() throws IOException {
    Path husk = Files.createDirectories(root.resolve("ABC-42-demo"));
    Files.createDirectories(husk.resolve(".idea"));
    Files.writeString(husk.resolve(".idea").resolve("workspace.xml"), "<project/>");
    Files.writeString(husk.resolve(".DS_Store"), "Bud1");

    scanner.run();

    assertThat(husk).doesNotExist();
  }

  @Test
  void keepsAnOrphanStillHoldingItsCheckout() throws IOException {
    Path orphan = Files.createDirectories(root.resolve("ABC-43-demo"));
    Files.writeString(
        orphan.resolve(".git"), "gitdir: " + repo.resolve(".git/worktrees/ABC-43-demo") + "\n");
    Files.createDirectories(orphan.resolve(".idea"));

    scanner.run();

    assertThat(orphan).exists();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("orphansKept")
  void keepsAnOrphanHoldingWork(String situation, String file, String content) throws IOException {
    Path orphan = Files.createDirectories(root.resolve("ABC-44-demo"));
    Files.writeString(orphan.resolve(file), content);

    scanner.run();

    assertThat(orphan).exists();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("orphansLeftOnDisk")
  void pingsWhatAnOrphanWasLeftHolding(String situation, String file, String content, String body)
      throws IOException {
    Path orphan = Files.createDirectories(root.resolve("ABC-48-demo"));
    Files.writeString(orphan.resolve(file), content);

    scanner.run();

    verify(notifications)
        .send(
            argThat(
                sent -> "1 orphaned worktree(s)".equals(sent.title()) && body.equals(sent.body())));
  }

  @Test
  void reportsAnEmptyHuskItCouldNotDeleteInsteadOfCallingItGone() throws IOException {
    Path husk = Files.createDirectories(root.resolve("ABC-49-demo"));
    Files.createFile(husk.resolve("empty"));
    Files.setPosixFilePermissions(husk, PosixFilePermissions.fromString("r-xr-xr-x"));

    scanner.run();

    verify(notifications).send(argThat(sent -> "1 orphaned worktree(s)".equals(sent.title())));
  }
}
