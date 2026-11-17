package dev.jagt.orchestrator.service.task;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchivedTaskFolderTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T10:15:30Z"), ZoneOffset.UTC);

  @TempDir Path root;

  @Test
  void movesWhatFinishedTaskLeftIntoTheSnapshotItWasGiven() throws Exception {
    Path snapshot = Files.createDirectories(root.resolve("archive/261028-101530-ABC-42"));
    Files.createDirectories(root.resolve("tasks/ABC-42/worktrees"));
    Files.writeString(root.resolve("tasks/ABC-42/notes.md"), "kept");

    new ArchivedTaskFolder(orchestratorPaths().withRoot(root).build(), clock)
        .shelve("ABC-42", snapshot);

    assertThat(snapshot.resolve("task/notes.md")).hasContent("kept");
    assertThat(root.resolve("tasks/ABC-42")).doesNotExist();
  }

  @Test
  void dropsTheFolderOfFinishedTaskThatLeftNothing() throws Exception {
    Files.createDirectories(root.resolve("tasks/ABC-42/worktrees"));

    new ArchivedTaskFolder(orchestratorPaths().withRoot(root).build(), clock)
        .shelve("ABC-42", null);

    assertThat(root.resolve("tasks/ABC-42")).doesNotExist();
    assertThat(root.resolve("archive")).doesNotExist();
  }

  @Test
  void leavesTheFolderWhereWorktreeIsStillCheckedOut() throws Exception {
    Path snapshot = Files.createDirectories(root.resolve("archive/261028-101530-ABC-42"));
    Files.createDirectories(root.resolve("tasks/ABC-42/worktrees/proj"));

    new ArchivedTaskFolder(orchestratorPaths().withRoot(root).build(), clock)
        .shelve("ABC-42", snapshot);

    assertThat(root.resolve("tasks/ABC-42/worktrees/proj")).isDirectory();
    assertThat(snapshot.resolve("task")).doesNotExist();
  }

  @Test
  void cutsTheSnapshotItselfWhereTheTaskKeptNone() throws Exception {
    Files.createDirectories(root.resolve("tasks/ABC-42"));
    Files.writeString(root.resolve("tasks/ABC-42/notes.md"), "kept");

    new ArchivedTaskFolder(orchestratorPaths().withRoot(root).build(), clock)
        .shelve("ABC-42", null);

    assertThat(root.resolve("archive/261117-101530-ABC-42/task/notes.md")).hasContent("kept");
    assertThat(root.resolve("tasks/ABC-42")).doesNotExist();
  }

  @Test
  void neverLandsInTheSnapshotOfAnotherTaskWhoseNameEndsWithItsOwn() throws Exception {
    Files.createDirectories(root.resolve("archive/261116-101530-big-fix"));
    Files.createDirectories(root.resolve("tasks/fix"));
    Files.writeString(root.resolve("tasks/fix/notes.md"), "kept");

    new ArchivedTaskFolder(orchestratorPaths().withRoot(root).build(), clock).shelve("fix", null);

    assertThat(root.resolve("archive/261116-101530-big-fix/task")).doesNotExist();
    assertThat(root.resolve("archive/261117-101530-fix/task/notes.md")).hasContent("kept");
  }

  @Test
  void cutsFreshSnapshotRatherThanOverwriteTheTaskFolderSnapshotHoldsAlready() throws Exception {
    Path snapshot = Files.createDirectories(root.resolve("archive/261028-101530-ABC-42/task"));
    Files.writeString(snapshot.resolve("first.md"), "first");
    Files.createDirectories(root.resolve("tasks/ABC-42"));
    Files.writeString(root.resolve("tasks/ABC-42/second.md"), "second");

    new ArchivedTaskFolder(orchestratorPaths().withRoot(root).build(), clock)
        .shelve("ABC-42", snapshot.getParent());

    assertThat(snapshot.resolve("first.md")).hasContent("first");
    assertThat(snapshot.resolve("second.md")).doesNotExist();
    assertThat(root.resolve("archive/261117-101530-ABC-42/task/second.md")).hasContent("second");
  }

  @Test
  void movesTheFolderOfTaskWhoseWorktreesFolderIsGone() throws Exception {
    Path snapshot = Files.createDirectories(root.resolve("archive/261028-101530-ABC-42"));
    Files.createDirectories(root.resolve("tasks/ABC-42"));
    Files.writeString(root.resolve("tasks/ABC-42/notes.md"), "kept");

    new ArchivedTaskFolder(orchestratorPaths().withRoot(root).build(), clock)
        .shelve("ABC-42", snapshot);

    assertThat(snapshot.resolve("task/notes.md")).hasContent("kept");
  }
}
