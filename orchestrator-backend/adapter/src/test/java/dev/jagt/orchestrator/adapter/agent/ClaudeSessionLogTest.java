package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SessionLog;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeSessionLogTest {

    @Test
    void countsCacheWritesAsInputAndCacheReadsApart(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, """
                {"type":"user","message":{"content":"go"}}
                {"type":"assistant","message":{"usage":{"input_tokens":3,\
                "cache_creation_input_tokens":1000,"cache_read_input_tokens":20000,"output_tokens":150}}}
                {"type":"assistant","message":{"usage":{"input_tokens":2,\
                "cache_read_input_tokens":21000,"output_tokens":40}}}
                """);

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

        assertThat(spent.usage()).isEqualTo(new TokenUsage(2, 1005, 41000, 190, 0));
        assertThat(spent.upTo()).isEqualTo(Files.size(log));
    }

    /** A line that froze the mark used to freeze a task's spend for good, from the first bad byte onward. */
    @Test
    void keepsCountingPastALineItCannotRead(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, """
                {"type":"assistant","message":{"usage":{"input_tokens":5,"output_tokens":10}}}
                {"type":"assistant","message":{"usage":{"input_tok
                {"type":"assistant","message":{"usage":{"input_tokens":7,"output_tokens":20}}}
                """);

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

        assertThat(spent.usage()).isEqualTo(new TokenUsage(2, 12, 0, 30, 0));
        assertThat(spent.upTo()).isEqualTo(Files.size(log));
    }

    /** The session is writing while this reads, so the turn being written now belongs to the next read. */
    @Test
    void leavesAHalfWrittenLastLineForTheNextRead(@TempDir Path dir) throws IOException {
        String complete = """
                {"type":"assistant","message":{"usage":{"input_tokens":5,"output_tokens":10}}}
                """;
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, complete + "{\"type\":\"assistant\",\"message\":{\"usage\":{\"input_tok");

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

        assertThat(spent.usage()).isEqualTo(new TokenUsage(1, 5, 0, 10, 0));
        assertThat(spent.upTo()).isEqualTo(complete.getBytes().length);
    }

    @Test
    void readsNoFurtherThanTheWindowItWasGiven(@TempDir Path dir) throws IOException {
        String first = """
                {"type":"assistant","message":{"usage":{"input_tokens":5,"output_tokens":10}}}
                """;
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, first + """
                {"type":"assistant","message":{"usage":{"input_tokens":7,"output_tokens":20}}}
                """);

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, first.getBytes().length);

        assertThat(spent.usage()).isEqualTo(new TokenUsage(1, 5, 0, 10, 0));
        assertThat(spent.upTo()).isEqualTo(first.getBytes().length);
    }

    @Test
    void answersNothingForALogThatIsNotThere(@TempDir Path dir) {
        SessionLog.Spent spent = new ClaudeSessionLog().spent(dir.resolve("gone.jsonl"), 40, 100);

        assertThat(spent.usage()).isEqualTo(TokenUsage.NONE);
        assertThat(spent.upTo()).isEqualTo(40);
    }

    /** Waiting for a newline that is not coming froze a task's spend for good; one turn is the cheaper loss. */
    @Test
    void stepsOverARecordTooLongForTheWindow(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, """
                {"type":"assistant","message":{"usage":{"input_tokens":5,"output_tokens":10,"note":"%s"}}}
                """.formatted("x".repeat(200)));

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, 50);

        assertThat(spent.usage()).isEqualTo(TokenUsage.NONE);
        assertThat(spent.upTo()).isEqualTo(50);
    }
}
