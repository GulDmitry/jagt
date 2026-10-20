package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.WorktreeProcesses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GitCommandsTest {

    @Test
    void readsTwoSpellingsOfAPathThatDoesNotExistAsTheSameDirectory(@TempDir Path dir) {
        boolean same = GitCommands.sameDirectory(dir.resolve("a/../missing"), dir.resolve("missing"));

        assertThat(same).isTrue();
    }

    @Test
    void readsTwoDifferentPathsThatDoNotExistAsDifferentDirectories(@TempDir Path dir) {
        boolean same = GitCommands.sameDirectory(dir.resolve("one"), dir.resolve("other"));

        assertThat(same).isFalse();
    }

    @Test
    void killsTheProcessesRootedInADirectoryOnEveryOfFourPassesWhenItWillNotGo(@TempDir Path root)
            throws Exception {
        Path dir = Files.createDirectories(root.resolve("held"));
        Files.writeString(dir.resolve("kept.txt"), "held by a process");
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
        WorktreeProcesses reaper = mock(WorktreeProcesses.class);
        GitCommands git = new GitCommands(mock(Processes.class), reaper);

        git.forceDeleteDir(dir);

        verify(reaper, times(4)).reap(dir);
    }

    @Test
    void letsASecondCallerIntoARepositoryOnceTheFirstActionReturned(@TempDir Path repo) throws Exception {
        GitCommands git = new GitCommands(mock(Processes.class), mock(WorktreeProcesses.class));
        CompletableFuture<String> first = new CompletableFuture<>();
        Thread.startVirtualThread(() -> first.complete(git.locked(repo, () -> "first")));
        first.get(5, TimeUnit.SECONDS);
        CompletableFuture<String> second = new CompletableFuture<>();
        Thread.startVirtualThread(() -> second.complete(git.locked(repo, () -> "second")));

        String answered = second.get(5, TimeUnit.SECONDS);

        assertThat(answered).isEqualTo("second");
    }
}
