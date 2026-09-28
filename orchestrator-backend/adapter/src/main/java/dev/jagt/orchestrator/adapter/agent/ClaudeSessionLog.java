package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SessionLog;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One JSON object per line, an assistant turn carrying {@code message.usage}. The log prices nothing itself: a
 * turn is priced by the model it names, at list price.
 */
@Component
@Slf4j
public class ClaudeSessionLog implements SessionLog {

    private final JsonMapper mapper = new JsonMapper();

    @Override
    public Spent spent(Path log, long from, long limit) {
        try {
            return counted(log, from, limit);
        } catch (IOException | RuntimeException e) {
            ClaudeSessionLog.log.atDebug().setMessage("session log unread").addKeyValue("file", log)
                    .addKeyValue("cause", e.toString()).log();
            return Spent.nothing(from);
        }
    }

    /** A session appends WHILE this reads, so the last line is usually half-written: the window ends at the last
     *  newline, and so does the mark. */
    private Spent counted(Path log, long from, long limit) throws IOException {
        byte[] window;
        try (InputStream in = Files.newInputStream(log)) {
            in.skipNBytes(from);
            window = in.readNBytes((int) Math.min(limit, Integer.MAX_VALUE));
        }
        int lastLine = lastNewlineIn(window);
        if (lastLine < 0) {
            // A record longer than the window: waiting for a newline that is not coming would freeze the spend
            // for good. A SHORT window is the tail being written.
            return window.length < limit ? Spent.nothing(from)
                    : new Spent(TokenUsage.NONE, from + window.length);
        }
        TokenUsage total = TokenUsage.NONE;
        for (String line : new String(window, 0, lastLine, StandardCharsets.UTF_8).split("\n")) {
            total = total.plus(usageIn(line));
        }
        return new Spent(total, from + lastLine + 1);
    }

    private static int lastNewlineIn(byte[] window) {
        for (int at = window.length - 1; at >= 0; at--) {
            if (window[at] == '\n') {
                return at;
            }
        }
        return -1;
    }

    /** One unreadable line costs one turn, never the whole log: throwing would stop the mark advancing. */
    private TokenUsage usageIn(String line) {
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
            return TokenUsage.ofCall(input + cacheWrite, cacheRead, output,
                    priceOf(message.path("model").asString(null), usage, input, cacheRead, output));
        } catch (RuntimeException unreadable) {
            return TokenUsage.NONE;
        }
    }

    /** A cache write without its TTL split is priced as the five-minute kind, the cheaper of the two. */
    private static double priceOf(String model, JsonNode usage, long input, long cacheRead, long output) {
        JsonNode split = usage.path("cache_creation");
        long write1h = split.path("ephemeral_1h_input_tokens").asLong(0);
        long write5m = split.isMissingNode()
                ? usage.path("cache_creation_input_tokens").asLong(0)
                : split.path("ephemeral_5m_input_tokens").asLong(0);
        return ClaudePrices.of(model)
                .map(prices -> prices.costOf(input, write5m, write1h, cacheRead, output))
                .orElse(0d);
    }
}
