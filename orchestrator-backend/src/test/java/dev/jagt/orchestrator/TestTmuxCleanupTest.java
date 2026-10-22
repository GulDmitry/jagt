package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TestTmuxCleanupTest {

    @Test
    void stopsOnlyTheSuitesOwnServerWhenItsSocketDirectoryIsAlreadyGone(@TempDir Path dir) throws Exception {
        Path bin = Files.createDirectory(dir.resolve("bin"));
        Path calls = dir.resolve("calls");
        Path fakeTmux = Files.writeString(bin.resolve("tmux"), "#!/bin/sh\necho \"$@\" >> " + calls + "\n");
        Files.setPosixFilePermissions(fakeTmux, PosixFilePermissions.fromString("rwx------"));
        Path gone = dir.resolve("gone");
        ProcessBuilder cleanup = new ProcessBuilder("sh", Path.of("gradle/stop-test-tmux.sh").toAbsolutePath().toString());
        cleanup.environment().put("PATH", bin + ":/usr/bin:/bin");
        cleanup.environment().put("TMUX_TMPDIR", gone.toString());
        cleanup.environment().remove("TMUX");

        Process stopped = cleanup.start();

        assertThat(stopped.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(Files.readString(calls)).startsWith("-S " + gone + "/tmux-").endsWith("/default kill-server\n");
    }
}
