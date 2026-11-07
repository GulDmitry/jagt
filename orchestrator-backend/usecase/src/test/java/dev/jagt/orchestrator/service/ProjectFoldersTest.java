package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFoldersTest {

  @TempDir Path root;

  @Test
  void listsRepositoriesBeforeOtherFoldersAndLeavesOutFilesAndHiddenFolders() throws Exception {
    Files.createDirectories(root.resolve("notes"));
    Files.createDirectories(root.resolve("billing/.git"));
    Files.createDirectories(root.resolve(".cache"));
    Files.writeString(root.resolve("todo.txt"), "");

    assertThat(new ProjectFolders().under(root + "/"))
        .containsExactly(
            new ProjectFolders.Folder("billing", true), new ProjectFolders.Folder("notes", false));
  }

  @Test
  void listsOnlyTheFoldersStartingWithWhatFollowsTheLastSlash() throws Exception {
    Files.createDirectories(root.resolve("billing"));
    Files.createDirectories(root.resolve("Billboard"));
    Files.createDirectories(root.resolve("notes"));

    assertThat(new ProjectFolders().under(root + "/bil"))
        .extracting(ProjectFolders.Folder::name)
        .containsExactly("Billboard", "billing");
  }

  @Test
  void listsNothingWhereTheTypedFolderDoesNotExistOrNoSlashWasTyped() {
    assertThat(new ProjectFolders().under(root + "/missing/x")).isEmpty();
    assertThat(new ProjectFolders().under("wor")).isEmpty();
  }

  @Test
  void listsHiddenFoldersOnlyWhereTheTypedPrefixStartsWithADot() throws Exception {
    Files.createDirectories(root.resolve(".config"));

    assertThat(new ProjectFolders().under(root + "/.c"))
        .extracting(ProjectFolders.Folder::name)
        .containsExactly(".config");
  }
}
