package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeTranscriptsTest {

    @Test
    void answersWhenTheNewestOfASessionsLogsWasLastAppendedTo(@TempDir Path root) throws Exception {
        Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
        Files.setLastModifiedTime(Files.writeString(logs.resolve("earlier.jsonl"), "{}"),
                FileTime.fromMillis(1_700_000_000_000L));
        Files.setLastModifiedTime(Files.writeString(logs.resolve("current.jsonl"), "{}"),
                FileTime.fromMillis(1_700_000_600_000L));

        long at = ClaudeTranscripts.lastEntryMillis(root, Path.of("/wt/ABC-1-proj"));

        assertThat(at).isEqualTo(1_700_000_600_000L);
    }

    /** Being wrong about the derived name must cost the caller a sign, never hand it a false one. */
    @Test
    void answersNothingWhereNoLogsForThatDirectoryExist(@TempDir Path root) {
        long at = ClaudeTranscripts.lastEntryMillis(root, Path.of("/wt/ABC-1-proj"));

        assertThat(at).isZero();
    }

    /** Claude names that directory after the physical path, so a worktree under a symlink must resolve to it. */
    @Test
    void findsTheLogsOfASessionDirectoryReachedThroughASymlink(@TempDir Path root) throws Exception {
        Path real = Files.createDirectories(root.resolve("real").resolve("ABC-1-proj"));
        Files.createSymbolicLink(root.resolve("link"), root.resolve("real"));

        String throughTheLink = ClaudeTranscripts.slug(root.resolve("link").resolve("ABC-1-proj"));

        assertThat(throughTheLink).isEqualTo(ClaudeTranscripts.slug(real));
    }
}
