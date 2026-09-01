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
 * One JSON object per line, an assistant turn carrying {@code message.usage}. Cache WRITES bill at input rates
 * and cache reads do not; the log prices nothing, so a call read from it carries no cost.
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
            JsonNode usage = mapper.readTree(line).path("message").path("usage");
            return TokenUsage.ofCall(
                    usage.path("input_tokens").asLong(0) + usage.path("cache_creation_input_tokens").asLong(0),
                    usage.path("cache_read_input_tokens").asLong(0),
                    usage.path("output_tokens").asLong(0),
                    0);
        } catch (RuntimeException unreadable) {
            return TokenUsage.NONE;
        }
    }
}
