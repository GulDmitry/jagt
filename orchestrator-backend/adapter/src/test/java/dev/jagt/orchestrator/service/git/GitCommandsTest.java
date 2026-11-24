package dev.jagt.orchestrator.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.jagt.orchestrator.adapter.worktree.NoWorktreeContainers;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.worktree.WorktreeProcesses;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitCommandsTest {

  @Test
  void readsTwoSpellingsOfPathThatDoesNotExistAsTheSameDirectory(@TempDir Path dir) {
    boolean same = GitCommands.sameDirectory(dir.resolve("a/../missing"), dir.resolve("missing"));

    assertThat(same).isTrue();
  }

  @Test
  void readsTwoDifferentPathsThatDoNotExistAsDifferentDirectories(@TempDir Path dir) {
    boolean same = GitCommands.sameDirectory(dir.resolve("one"), dir.resolve("other"));

    assertThat(same).isFalse();
  }

  @Test
  void killsTheProcessesRootedInDirectoryOnEveryOfFourPassesWhenItWillNotGo(@TempDir Path root)
      throws Exception {
    Path dir = Files.createDirectories(root.resolve("held"));
    Files.writeString(dir.resolve("kept.txt"), "held by a process");
    Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
    WorktreeProcesses reaper = mock(WorktreeProcesses.class);
    GitCommands git =
        new GitCommands(
            mock(Processes.class),
            reaper,
            path -> java.util.Optional.empty(),
            new NoWorktreeContainers());

    git.forceDeleteDir(dir);

    verify(reaper, times(4)).reap(dir);
  }

  @Test
  void letsSecondCallerIntoRepositoryOnceTheFirstActionReturned(@TempDir Path repo) {
    GitCommands git =
        new GitCommands(
            mock(Processes.class),
            mock(WorktreeProcesses.class),
            path -> java.util.Optional.empty(),
            new NoWorktreeContainers());
    git.locked(repo, () -> "first");

    String answered = git.locked(repo, () -> "second");

    assertThat(answered).isEqualTo("second");
  }
}
