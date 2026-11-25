package dev.jagt.orchestrator.adapter.agent.log;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.port.agent.SessionLog;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexSessionLogTest {

  private static final String TURN_ONE =
      "{\"type\":\"event_msg\",\"payload\":{\"type\":\"token_count\",\"info\":{\"last_token_usag"
          + "e\":{\"input_tokens\":1000,\"cached_input_tokens\":800,\"output_tokens\":50}}}}\n";
  private static final String TURN_TWO =
      "{\"type\":\"event_msg\",\"payload\":{\"type\":\"token_count\",\"info\":{\"last_token_usag"
          + "e\":{\"input_tokens\":2000,\"cached_input_tokens\":1500,\"output_tokens\":70}}}}\n";

  @Test
  void booksEachTurnUnpricedWithItsCachedTokensApartFromItsInput(@TempDir Path dir)
      throws IOException {
    Path log = dir.resolve("rollout.jsonl");
    Files.writeString(
        log, "{\"type\":\"session_meta\",\"payload\":{\"cwd\":\"/wt\"}}\n" + TURN_ONE + TURN_TWO);

    SessionLog.Spent spent =
        new CodexSessionLog(new UnpricedModels()).spent(log, 0, Files.size(log));

    assertThat(spent.usage())
        .isEqualTo(
            TokenUsage.ofUnpricedCall(200, 800, 50).plus(TokenUsage.ofUnpricedCall(500, 1500, 70)));
    assertThat(spent.upTo()).isEqualTo(Files.size(log));
  }

  @Test
  void warnsOnceThatItsTurnsAreUnpricedNotOncePerTurn(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("rollout.jsonl");
    Files.writeString(log, TURN_ONE + TURN_TWO);

    try (FakeLogCapture appender = new FakeLogCapture(UnpricedModels.class)) {
      new CodexSessionLog(new UnpricedModels()).spent(log, 0, Files.size(log));

      assertThat(appender.events())
          .extracting(ILoggingEvent::getMessage)
          .containsExactly("model unpriced");
    }
  }

  @Test
  void countsNothingForTokenCountWithoutInfo(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("rollout.jsonl");
    Files.writeString(
        log, "{\"type\":\"event_msg\",\"payload\":{\"type\":\"token_count\",\"info\":null}}\n");

    SessionLog.Spent spent =
        new CodexSessionLog(new UnpricedModels()).spent(log, 0, Files.size(log));

    assertThat(spent.usage().isNone()).isTrue();
    assertThat(spent.upTo()).isEqualTo(Files.size(log));
  }

  @Test
  void leavesHalfWrittenLastLineForTheNextRead(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("rollout.jsonl");
    Files.writeString(log, TURN_ONE + TURN_TWO.substring(0, 60));

    SessionLog.Spent spent =
        new CodexSessionLog(new UnpricedModels()).spent(log, 0, Files.size(log));

    assertThat(spent.usage()).isEqualTo(TokenUsage.ofUnpricedCall(200, 800, 50));
    assertThat(spent.upTo()).isEqualTo(TURN_ONE.length());
  }

  @Test
  void readsNothingAndKeepsItsMarkFromLogItCannotRead(@TempDir Path dir) {
    SessionLog.Spent spent =
        new CodexSessionLog(new UnpricedModels()).spent(dir.resolve("gone.jsonl"), 7, 100);

    assertThat(spent).isEqualTo(SessionLog.Spent.nothing(7));
  }
}
