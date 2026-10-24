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

        assertThat(spent.usage()).isEqualTo(new TokenUsage(2, 1005, 41000, 190, 0, 0, 1));
        assertThat(spent.upTo()).isEqualTo(Files.size(log));
    }

    @Test
    void countsAMessageOnceThoughEachOfItsBlocksRepeatsItsUsage(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, """
                {"type":"assistant","message":{"id":"msg_1","usage":{"input_tokens":3,"output_tokens":100}}}
                {"type":"assistant","message":{"id":"msg_1","usage":{"input_tokens":3,"output_tokens":100}}}
                {"type":"user","message":{"content":"tool result"}}
                """);

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

        assertThat(spent.usage().total()).isEqualTo(103);
    }

    @Test
    void leavesAMessageStillBeingWrittenForTheNextReadSoItsLaterBlocksAreNotCountedAgain(@TempDir Path dir)
            throws IOException {
        Path log = dir.resolve("session.jsonl");
        String first = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":1,\"output_tokens\":9}}}\n";
        String open = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_2\",\"usage\":{\"input_tokens\":2,\"output_tokens\":40}}}\n";
        Files.writeString(log, first + open);
        SessionLog.Spent before = new ClaudeSessionLog().spent(log, 0, Files.size(log));
        Files.writeString(log, first + open + open + "{\"type\":\"user\",\"message\":{\"content\":\"ok\"}}\n");

        SessionLog.Spent after = new ClaudeSessionLog().spent(log, before.upTo(), Files.size(log));

        assertThat(before.usage().total() + after.usage().total()).isEqualTo(52);
    }

    @Test
    void pricesATurnAtTheListPriceOfTheModelItNames(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, """
                {"type":"assistant","message":{"model":"claude-opus-5","usage":{"input_tokens":1000000,\
                "cache_creation_input_tokens":1000000,"cache_read_input_tokens":1000000,"output_tokens":1000000,\
                "cache_creation":{"ephemeral_5m_input_tokens":0,"ephemeral_1h_input_tokens":1000000}}}}
                """);

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

        assertThat(spent.usage().costUsd()).isEqualTo(5 + 10 + 0.5 + 25);
    }

    @Test
    void pricesACacheWriteWithoutItsTtlAsTheFiveMinuteKind(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, """
                {"type":"assistant","message":{"model":"claude-haiku-4-5","usage":{"input_tokens":0,\
                "cache_creation_input_tokens":1000000,"output_tokens":0}}}
                """);

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

        assertThat(spent.usage().costUsd()).isEqualTo(1.25);
    }

    @Test
    void pricesNothingForAModelNoRowNames(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, """
                {"type":"assistant","message":{"model":"claude-unknown-9","usage":{"input_tokens":1000000,\
                "output_tokens":1000000}}}
                """);

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

        assertThat(spent.usage().costUsd()).isZero();
        assertThat(spent.usage().total()).isEqualTo(2000000);
    }

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

    @Test
    void countsOnlyTheFirstTurnOfALogAsAStart(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, """
                {"type":"assistant","message":{"usage":{"input_tokens":3,\
                "cache_creation_input_tokens":1000,"cache_read_input_tokens":0,"output_tokens":150}}}
                {"type":"assistant","message":{"usage":{"input_tokens":2,\
                "cache_read_input_tokens":21000,"output_tokens":40}}}
                """);

        TokenUsage usage = new ClaudeSessionLog().spent(log, 0, Files.size(log)).usage();

        assertThat(usage.coldStarts()).isEqualTo(1);
        assertThat(usage.warmStarts()).isZero();
    }

    @Test
    void countsNoStartWhenTheFirstTurnCarriesNoCacheFields(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, """
                {"type":"assistant","message":{"usage":{"input_tokens":3,"output_tokens":150}}}
                """);

        assertThat(new ClaudeSessionLog().spent(log, 0, Files.size(log)).usage().measuredStarts()).isZero();
    }

    @Test
    void countsNoStartForTurnsReadFromAMark(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, """
                {"type":"user","message":{"content":"go"}}
                {"type":"assistant","message":{"usage":{"input_tokens":3,\
                "cache_read_input_tokens":0,"output_tokens":150}}}
                """);
        long mark = Files.readAllLines(log).get(0).length() + 1;

        assertThat(new ClaudeSessionLog().spent(log, mark, Files.size(log) - mark).usage().measuredStarts()).isZero();
    }

    @Test
    void countsOnlyWhatLiesPastTheMark(@TempDir Path dir) throws IOException {
        String before = """
                {"type":"assistant","message":{"usage":{"input_tokens":5,"output_tokens":10}}}
                """;
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, before + """
                {"type":"assistant","message":{"usage":{"input_tokens":7,"output_tokens":20}}}
                """);
        long mark = before.getBytes().length;

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, mark, Files.size(log) - mark);

        assertThat(spent.usage()).isEqualTo(new TokenUsage(1, 7, 0, 20, 0));
        assertThat(spent.upTo()).isEqualTo(Files.size(log));
    }

    @Test
    void movesTheMarkPastALeadingBlankLine(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, "\n{\"type\":\"assistant\",\"message\":{\"usage\":{\"input_tok");

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

        assertThat(spent.upTo()).isEqualTo(1);
    }

    @Test
    void marksTheEndOfTheLastClosedMessageAndLeavesTheOpenOneAfterIt(@TempDir Path dir) throws IOException {
        String closed = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":1,\"output_tokens\":9}}}\n";
        String open = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_2\",\"usage\":{\"input_tokens\":2,\"output_tokens\":40}}}\n";
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, closed + open);

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

        assertThat(spent.usage().total()).isEqualTo(10);
        assertThat(spent.upTo()).isEqualTo(closed.getBytes().length);
    }

    @Test
    void countsAMessageWhoseBlocksAreAllThatTheWindowHolds(@TempDir Path dir) throws IOException {
        String block = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":3,\"output_tokens\":100}}}\n";
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, block + block);

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

        assertThat(spent.usage().total()).isEqualTo(103);
        assertThat(spent.upTo()).isEqualTo(Files.size(log));
    }

    @Test
    void countsEveryLineWhereTheLastOneCarriesNoUsage(@TempDir Path dir) throws IOException {
        String closed = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":1,\"output_tokens\":9}}}\n";
        String user = "{\"type\":\"user\",\"message\":{\"content\":\"ok\"}}\n";
        Path log = dir.resolve("session.jsonl");
        Files.writeString(log, closed + user);

        SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

        assertThat(spent.usage().total()).isEqualTo(10);
        assertThat(spent.upTo()).isEqualTo(Files.size(log));
    }
}
