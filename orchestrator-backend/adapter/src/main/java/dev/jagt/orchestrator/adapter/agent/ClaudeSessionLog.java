package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SessionLog;
import dev.jagt.orchestrator.task.TokenUsage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One JSON object per line, an assistant turn carrying {@code message.usage}. The log prices
 * nothing itself: a turn is priced by the model it names, at list price.
 */
@Component
@ConditionalOnProperty(
    name = "orchestrator.agent.cli",
    havingValue = "claude",
    matchIfMissing = true)
@Slf4j
public class ClaudeSessionLog implements SessionLog {

  private final JsonMapper mapper = new JsonMapper();
  private final Set<String> warned = ConcurrentHashMap.newKeySet();

  @Override
  public Spent spent(Path log, long from, long limit) {
    try {
      return counted(log, from, limit);
    } catch (IOException | RuntimeException e) {
      ClaudeSessionLog.log
          .atDebug()
          .setMessage("session log unread")
          .addKeyValue("file", log)
          .addKeyValue("cause", e.toString())
          .log();
      return Spent.nothing(from);
    }
  }

  private Spent counted(Path log, long from, long limit) throws IOException {
    JsonlWindow window = JsonlWindow.of(log, from, limit);
    String[] lines = window.lines().toArray(String[]::new);
    if (lines.length == 0) {
      return new Spent(TokenUsage.NONE, from + window.bytes());
    }
    int counted = linesBeforeAnOpenMessage(lines);
    TokenUsage total = TokenUsage.NONE;
    boolean firstTurn = from == 0;
    @Nullable String previousId = null;
    for (int at = 0; at < counted; at++) {
      String id = messageIdIn(lines[at]);
      TokenUsage turn =
          id != null && id.equals(previousId) ? TokenUsage.NONE : usageIn(lines[at], firstTurn);
      previousId = id == null ? previousId : id;
      firstTurn &= turn.isNone();
      total = total.plus(turn);
    }
    return new Spent(total, from + bytesOf(lines, counted, window.bytes()));
  }

  /**
   * Claude writes one line per content block of a message, each repeating its usage, so a message
   * is counted once. A message at the window's end may still be getting blocks: it is left for the
   * next read, unless it is all the window holds.
   */
  private int linesBeforeAnOpenMessage(String[] lines) {
    String last = messageIdIn(lines[lines.length - 1]);
    if (last == null) {
      return lines.length;
    }
    int start = lines.length - 1;
    while (start > 0 && last.equals(messageIdIn(lines[start - 1]))) {
      start--;
    }
    return start == 0 ? lines.length : start;
  }

  private static long bytesOf(String[] lines, int counted, long whole) {
    if (counted == lines.length) {
      return whole;
    }
    long bytes = 0;
    for (int at = 0; at < counted; at++) {
      bytes += lines[at].getBytes(StandardCharsets.UTF_8).length + 1;
    }
    return bytes;
  }

  private @Nullable String messageIdIn(String line) {
    if (!line.contains("\"usage\"")) {
      return null;
    }
    try {
      return mapper.readTree(line).path("message").path("id").asString(null);
    } catch (RuntimeException unreadable) {
      return null;
    }
  }

  /**
   * One unreadable line costs one turn, never the whole log: throwing would stop the mark
   * advancing.
   */
  private TokenUsage usageIn(String line, boolean firstTurn) {
    if (!line.contains("\"usage\"")) {
      return TokenUsage.NONE;
    }
    try {
      JsonNode message = mapper.readTree(line).path("message");
      JsonNode usage = message.path("usage");
      long input = usage.path("input_tokens").asLong(0);
      long cacheWrite = usage.path("cache_creation_input_tokens").asLong(0);
      long cacheRead = usage.path("cache_read_input_tokens").asLong(0);
      long output = usage.path("output_tokens").asLong(0);
      String model = message.path("model").asString(null);
      TokenUsage turn =
          priceOf(model, usage, input, cacheRead, output)
              .map(cost -> TokenUsage.ofCall(input + cacheWrite, cacheRead, output, cost))
              .orElseGet(
                  () ->
                      unpriced(
                          model, TokenUsage.ofUnpricedCall(input + cacheWrite, cacheRead, output)));
      return firstTurn && usage.has("cache_read_input_tokens") ? turn.asStart() : turn;
    } catch (RuntimeException unreadable) {
      return TokenUsage.NONE;
    }
  }

  /**
   * A cache write without its TTL split is priced as the five-minute kind, the cheaper of the two.
   */
  private static Optional<Double> priceOf(
      @Nullable String model, JsonNode usage, long input, long cacheRead, long output) {
    JsonNode split = usage.path("cache_creation");
    long write1h = split.path("ephemeral_1h_input_tokens").asLong(0);
    long write5m =
        split.isMissingNode()
            ? usage.path("cache_creation_input_tokens").asLong(0)
            : split.path("ephemeral_5m_input_tokens").asLong(0);
    return ClaudePrices.of(model)
        .map(prices -> prices.costOf(input, write5m, write1h, cacheRead, output));
  }

  private TokenUsage unpriced(@Nullable String model, TokenUsage turn) {
    if (!turn.isNone() && warned.add(String.valueOf(model))) {
      log.atWarn()
          .setMessage("model unpriced")
          .addKeyValue("model", model)
          .addKeyValue("effect", "cost counted as a lower bound")
          .addKeyValue("fix", "ClaudePrices")
          .log();
    }
    return turn;
  }
}
