package dev.jagt.orchestrator.adapter.agent;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.port.SessionLog;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaudeSessionLogTest {

  @Test
  void countsCacheWritesAsInputAndCacheReadsApart(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"user","message":{"content":"go"}}
        {"type":"assistant","message":{"usage":{"input_tokens":3,\
        "cache_creation_input_tokens":1000,"cache_read_input_tokens":20000,"output_tokens":150}}}
        {"type":"assistant","message":{"usage":{"input_tokens":2,\
        "cache_read_input_tokens":21000,"output_tokens":40}}}
        """);

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.usage()).isEqualTo(new TokenUsage(2, 1005, 41000, 190, 0, 0, 1, 2));
    assertThat(spent.upTo()).isEqualTo(Files.size(log));
  }

  @Test
  void countsMessageOnceThoughEachOfItsBlocksRepeatsItsUsage(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"id":"msg_1","usage":{"input_tokens":3,"output_tokens":100}}}
        {"type":"assistant","message":{"id":"msg_1","usage":{"input_tokens":3,"output_tokens":100}}}
        {"type":"user","message":{"content":"tool result"}}
        """);

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.usage().total()).isEqualTo(103);
  }

  @Test
  void leavesMessageStillBeingWrittenForTheNextReadSoItsLaterBlocksAreNotCountedAgain(
      @TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    String first =
        "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":1,\"o"
            + "utput_tokens\":9}}}\n";
    String open =
        "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_2\",\"usage\":{\"input_tokens\":2,\"o"
            + "utput_tokens\":40}}}\n";
    Files.writeString(log, first + open);
    SessionLog.Spent before = new ClaudeSessionLog().spent(log, 0, Files.size(log));
    Files.writeString(
        log, first + open + open + "{\"type\":\"user\",\"message\":{\"content\":\"ok\"}}\n");

    SessionLog.Spent after = new ClaudeSessionLog().spent(log, before.upTo(), Files.size(log));

    assertThat(before.usage().total() + after.usage().total()).isEqualTo(52);
  }

  @Test
  void pricesTurnAtTheListPriceOfTheModelItNames(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"model":"claude-opus-5","usage":{"input_tokens":1000000,\
        "cache_creation_input_tokens":1000000,"cache_read_input_tokens":1000000,"output_tokens":1000000,\
        "cache_creation":{"ephemeral_5m_input_tokens":0,"ephemeral_1h_input_tokens":1000000}}}}
        """);

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.usage().costUsd()).isEqualTo(5 + 10 + 0.5 + 25);
  }

  @Test
  void pricesCacheWriteWithoutItsTtlAsTheFiveMinuteKind(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"model":"claude-haiku-4-5","usage":{"input_tokens":0,\
        "cache_creation_input_tokens":1000000,"output_tokens":0}}}
        """);

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.usage().costUsd()).isEqualTo(1.25);
  }

  @Test
  void pricesNothingForModelNoRowNames(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"model":"claude-unknown-9","usage":{"input_tokens":1000000,\
        "output_tokens":1000000}}}
        """);

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.usage().costUsd()).isZero();
    assertThat(spent.usage().total()).isEqualTo(2000000);
  }

  @Test
  void keepsCountingPastLineItCannotRead(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"usage":{"input_tokens":5,"output_tokens":10}}}
        {"type":"assistant","message":{"usage":{"input_tok
        {"type":"assistant","message":{"usage":{"input_tokens":7,"output_tokens":20}}}
        """);

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.usage()).isEqualTo(new TokenUsage(2, 12, 0, 30, 0, 0, 0, 2));
    assertThat(spent.upTo()).isEqualTo(Files.size(log));
  }

  @Test
  void leavesHalfWrittenLastLineForTheNextRead(@TempDir Path dir) throws IOException {
    String complete =
        """
        {"type":"assistant","message":{"usage":{"input_tokens":5,"output_tokens":10}}}
        """;
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log, complete + "{\"type\":\"assistant\",\"message\":{\"usage\":{\"input_tok");

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.usage()).isEqualTo(new TokenUsage(1, 5, 0, 10, 0, 0, 0, 1));
    assertThat(spent.upTo()).isEqualTo(complete.getBytes(UTF_8).length);
  }

  @Test
  void readsNoFurtherThanTheWindowItWasGiven(@TempDir Path dir) throws IOException {
    String first =
        """
        {"type":"assistant","message":{"usage":{"input_tokens":5,"output_tokens":10}}}
        """;
    Path log = dir.resolve("session.jsonl");
    String next =
        """
        {"type":"assistant","message":{"usage":{"input_tokens":7,"output_tokens":20}}}
        """;
    Files.writeString(log, first + next);

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, first.getBytes(UTF_8).length);

    assertThat(spent.usage()).isEqualTo(new TokenUsage(1, 5, 0, 10, 0, 0, 0, 1));
    assertThat(spent.upTo()).isEqualTo(first.getBytes(UTF_8).length);
  }

  @Test
  void answersNothingForLogThatIsNotThere(@TempDir Path dir) {
    SessionLog.Spent spent = new ClaudeSessionLog().spent(dir.resolve("gone.jsonl"), 40, 100);

    assertThat(spent.usage()).isEqualTo(TokenUsage.NONE);
    assertThat(spent.upTo()).isEqualTo(40);
  }

  @Test
  void stepsOverRecordTooLongForTheWindow(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"usage":{"input_tokens":5,"output_tokens":10,"note":"%s"}}}
        """
            .formatted("x".repeat(200)));

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, 50);

    assertThat(spent.usage()).isEqualTo(TokenUsage.NONE);
    assertThat(spent.upTo()).isEqualTo(50);
  }

  @Test
  void countsOnlyTheFirstTurnOfLogAsStart(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
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
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"usage":{"input_tokens":3,"output_tokens":150}}}
        """);

    assertThat(new ClaudeSessionLog().spent(log, 0, Files.size(log)).usage().measuredStarts())
        .isZero();
  }

  @Test
  void countsNoStartForTurnsReadFromMark(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"user","message":{"content":"go"}}
        {"type":"assistant","message":{"usage":{"input_tokens":3,\
        "cache_read_input_tokens":0,"output_tokens":150}}}
        """);
    long mark = Files.readAllLines(log).get(0).length() + 1L;

    assertThat(
            new ClaudeSessionLog()
                .spent(log, mark, Files.size(log) - mark)
                .usage()
                .measuredStarts())
        .isZero();
  }

  @Test
  void countsOnlyWhatLiesPastTheMark(@TempDir Path dir) throws IOException {
    String before =
        """
        {"type":"assistant","message":{"usage":{"input_tokens":5,"output_tokens":10}}}
        """;
    Path log = dir.resolve("session.jsonl");
    String next =
        """
        {"type":"assistant","message":{"usage":{"input_tokens":7,"output_tokens":20}}}
        """;
    Files.writeString(log, before + next);
    long mark = before.getBytes(UTF_8).length;

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, mark, Files.size(log) - mark);

    assertThat(spent.usage()).isEqualTo(new TokenUsage(1, 7, 0, 20, 0, 0, 0, 1));
    assertThat(spent.upTo()).isEqualTo(Files.size(log));
  }

  @Test
  void movesTheMarkPastLeadingBlankLine(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(log, "\n{\"type\":\"assistant\",\"message\":{\"usage\":{\"input_tok");

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.upTo()).isEqualTo(1);
  }

  @Test
  void marksTheEndOfTheLastClosedMessageAndLeavesTheOpenOneAfterIt(@TempDir Path dir)
      throws IOException {
    String closed =
        "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":1,\"o"
            + "utput_tokens\":9}}}\n";
    String open =
        "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_2\",\"usage\":{\"input_tokens\":2,\"o"
            + "utput_tokens\":40}}}\n";
    Path log = dir.resolve("session.jsonl");
    Files.writeString(log, closed + open);

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.usage().total()).isEqualTo(10);
    assertThat(spent.upTo()).isEqualTo(closed.getBytes(UTF_8).length);
  }

  @Test
  void countsMessageWhoseBlocksAreAllThatTheWindowHolds(@TempDir Path dir) throws IOException {
    String block =
        "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":3,\"o"
            + "utput_tokens\":100}}}\n";
    Path log = dir.resolve("session.jsonl");
    Files.writeString(log, block + block);

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.usage().total()).isEqualTo(103);
    assertThat(spent.upTo()).isEqualTo(Files.size(log));
  }

  @Test
  void countsEveryLineWhereTheLastOneCarriesNoUsage(@TempDir Path dir) throws IOException {
    String closed =
        "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":1,\"o"
            + "utput_tokens\":9}}}\n";
    String user = "{\"type\":\"user\",\"message\":{\"content\":\"ok\"}}\n";
    Path log = dir.resolve("session.jsonl");
    Files.writeString(log, closed + user);

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.usage().total()).isEqualTo(10);
    assertThat(spent.upTo()).isEqualTo(Files.size(log));
  }

  @Test
  void countsLastLineThatCarriesUsageButCannotBeRead(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"usage":{"input_tokens":5,"output_tokens":10}}}
        {"type":"assistant","message":{"usage":{"input_tok
        """);

    SessionLog.Spent spent = new ClaudeSessionLog().spent(log, 0, Files.size(log));

    assertThat(spent.usage()).isEqualTo(new TokenUsage(1, 5, 0, 10, 0, 0, 0, 1));
    assertThat(spent.upTo()).isEqualTo(Files.size(log));
  }

  @Test
  void countsTurnOfModelWithNoKnownPriceAsUnpricedNotAsFree(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"model":"claude-nova-9","usage":{"input_tokens":100,"output_tokens":10}}}
        {"type":"assistant","message":{"model":"claude-opus-5","usage":{"input_tokens":1000000,"output_tokens":0}}}
        """);

    TokenUsage usage = new ClaudeSessionLog().spent(log, 0, Files.size(log)).usage();

    assertThat(usage.unpricedCalls()).isEqualTo(1);
    assertThat(usage.costUsd()).isEqualTo(5);
    assertThat(usage.costIsLowerBound()).isTrue();
  }

  @Test
  void leavesTheCostCompleteWhenEveryModelIsPriced(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"model":"claude-opus-5","usage":{"input_tokens":100,"output_tokens":10}}}
        """);

    assertThat(new ClaudeSessionLog().spent(log, 0, Files.size(log)).usage().costIsLowerBound())
        .isFalse();
  }

  @Test
  void warnsOncePerUnpricedModelNotOncePerTurn(@TempDir Path dir) throws IOException {
    try (FakeLogCapture appender = new FakeLogCapture(ClaudeSessionLog.class)) {
      Path log = dir.resolve("session.jsonl");
      Files.writeString(
          log,
          """
          {"type":"assistant","message":{"model":"claude-nova-9","usage":{"input_tokens":100,"output_tokens":10}}}
          {"type":"assistant","message":{"model":"claude-nova-9","usage":{"input_tokens":100,"output_tokens":10}}}
          {"type":"assistant","message":{"model":"claude-nova-8","usage":{"input_tokens":100,"output_tokens":10}}}
          """);

      new ClaudeSessionLog().spent(log, 0, Files.size(log));

      assertThat(appender.events())
          .extracting(ILoggingEvent::getMessage)
          .containsExactly("model unpriced", "model unpriced");
    }
  }

  @Test
  void countsTheCacheWritesOfAnUnpricedTurnAsInput(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"model":"claude-nova-9","usage":{"input_tokens":100,\
        "cache_creation_input_tokens":50,"output_tokens":10}}}
        """);

    assertThat(new ClaudeSessionLog().spent(log, 0, Files.size(log)).usage().inputTokens())
        .isEqualTo(150);
  }

  @Test
  void countsTheCacheWritesOfPricedTurnAsInput(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("session.jsonl");
    Files.writeString(
        log,
        """
        {"type":"assistant","message":{"model":"claude-opus-5","usage":{"input_tokens":100,\
        "cache_creation_input_tokens":50,"output_tokens":10}}}
        """);

    assertThat(new ClaudeSessionLog().spent(log, 0, Files.size(log)).usage().inputTokens())
        .isEqualTo(150);
  }
}
