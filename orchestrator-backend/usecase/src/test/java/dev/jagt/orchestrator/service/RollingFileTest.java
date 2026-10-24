package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RollingFileTest {

    @Test
    void keepsAFileThatReachedExactlyTheLimit(@TempDir Path dir) throws IOException {
        RollingFile file = new RollingFile(dir.resolve("log.jsonl"));

        file.append("x".repeat(1024 * 1024));

        assertThat(dir.resolve("log.jsonl")).exists();
        assertThat(dir.resolve("log.jsonl.1")).doesNotExist();
    }

    @Test
    void rollsAFilePastTheLimitIntoItsDotOneSiblingReplacingTheLastOne(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("log.jsonl.1"), "older");
        RollingFile file = new RollingFile(dir.resolve("log.jsonl"));

        file.append("x".repeat(1024 * 1024 + 1));

        assertThat(dir.resolve("log.jsonl")).doesNotExist();
        assertThat(Files.size(dir.resolve("log.jsonl.1"))).isEqualTo(1024 * 1024 + 1);
    }

    @Test
    void namesTheFileItWritesTo(@TempDir Path dir) {
        assertThat(new RollingFile(dir.resolve("log.jsonl")).path()).isEqualTo(dir.resolve("log.jsonl"));
    }
}
