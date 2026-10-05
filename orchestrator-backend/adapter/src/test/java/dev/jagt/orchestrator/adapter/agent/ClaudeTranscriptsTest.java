package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;

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

    @Test
    void answersNothingWhereNoLogsForThatDirectoryExist(@TempDir Path root) {
        long at = ClaudeTranscripts.lastEntryMillis(root, Path.of("/wt/ABC-1-proj"));

        assertThat(at).isZero();
    }

    @Test
    void findsTheLogsOfASessionDirectoryReachedThroughASymlink(@TempDir Path root) throws Exception {
        Path real = Files.createDirectories(root.resolve("real").resolve("ABC-1-proj"));
        Files.createSymbolicLink(root.resolve("link"), root.resolve("real"));

        String throughTheLink = ClaudeTranscripts.slug(root.resolve("link").resolve("ABC-1-proj"));

        assertThat(throughTheLink).isEqualTo(ClaudeTranscripts.slug(real));
    }

    @Test
    void cutsTheLineJagtTypedOutOfWhatTheHumanWasComposing(@TempDir Path root) throws Exception {
        Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
        Files.writeString(logs.resolve("s.jsonl"), """
                {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"do itRe-read the file."}}
                {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"Re-read the file."}}
                """);

        var said = ClaudeTranscripts.humanSaid(root, Path.of("/wt/ABC-1-proj"), Set.of("Re-read the file."));

        assertThat(said).contains(List.of("do it"));
    }

    @Test
    void leavesOutWhatAHeadlessRunWasPromptedWith(@TempDir Path root) throws Exception {
        Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
        Files.writeString(logs.resolve("s.jsonl"), """
                {"type":"user","entrypoint":"sdk-cli","message":{"content":"You stand in for the human"}}
                """);

        var said = ClaudeTranscripts.humanSaid(root, Path.of("/wt/ABC-1-proj"), Set.of());

        assertThat(said).contains(List.of());
    }

    @Test
    void answersUnreadableRatherThanSilentWhereTheSessionKeptNoRecord(@TempDir Path root) {
        var said = ClaudeTranscripts.humanSaid(root, Path.of("/wt/ABC-1-proj"), Set.of());

        assertThat(said).isEmpty();
    }
}
