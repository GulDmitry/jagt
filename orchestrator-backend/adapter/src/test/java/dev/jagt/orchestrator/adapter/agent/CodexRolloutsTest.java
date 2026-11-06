package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;

class CodexRolloutsTest {

    @Test
    void namesTheNewestRolloutAcrossDayDirectories(@TempDir Path home) throws Exception {
        Path older = Files.createDirectories(home.resolve("sessions/2026/11/05")).resolve("rollout-a.jsonl");
        Path newer = Files.createDirectories(home.resolve("sessions/2026/11/06")).resolve("rollout-b.jsonl");
        Files.setLastModifiedTime(Files.writeString(older, "{}"), FileTime.fromMillis(1_700_000_000_000L));
        Files.setLastModifiedTime(Files.writeString(newer, "{}"), FileTime.fromMillis(1_700_000_600_000L));

        assertThat(CodexRollouts.newest(home)).contains(newer);
    }

    @Test
    void namesNoneWhereTheHomeHoldsNoSessions(@TempDir Path home) {
        assertThat(CodexRollouts.newest(home)).isEmpty();
    }
}
