package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtomicFilesTest {

    @Test
    void replacesTheWholeContentLeavingNoTemporaryFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("state.json");
        Files.writeString(file, "old and longer");

        AtomicFiles.write(file, "new".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.readString(file)).isEqualTo("new");
        assertThat(dir.toFile().list()).containsExactly("state.json");
    }

    @Test
    void leavesNoTemporaryFileWhenTheMoveFails(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("target");
        Files.createDirectories(target.resolve("child"));

        assertThatThrownBy(() -> AtomicFiles.write(target, "x".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class);

        assertThat(dir.toFile().list()).containsExactly("target");
    }
}
