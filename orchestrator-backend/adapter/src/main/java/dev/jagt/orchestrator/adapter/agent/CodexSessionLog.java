package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SessionLog;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * One JSON object per line, a turn being a {@code token_count} event with a non-null {@code info}. Its input
 * count includes the cached tokens. No price is known, so every turn is unpriced.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "codex")
@Slf4j
public class CodexSessionLog implements SessionLog {

    private final JsonMapper mapper = new JsonMapper();

    @Override
    public Spent spent(Path log, long from, long limit) {
        try {
            JsonlWindow window = JsonlWindow.of(log, from, limit);
            TokenUsage total = TokenUsage.NONE;
            for (String line : window.lines()) {
                total = total.plus(turnIn(line));
            }
            return new Spent(total, from + window.bytes());
        } catch (IOException | RuntimeException e) {
            CodexSessionLog.log.atDebug().setMessage("session log unread").addKeyValue("file", log)
                    .addKeyValue("cause", e.toString()).log();
            return Spent.nothing(from);
        }
    }

    /** One unreadable line costs one turn, never the whole log: throwing would stop the mark advancing. */
    private TokenUsage turnIn(String line) {
        if (!line.contains("\"token_count\"")) {
            return TokenUsage.NONE;
        }
        try {
            JsonNode payload = mapper.readTree(line).path("payload");
            JsonNode last = payload.path("info").path("last_token_usage");
            if (!"token_count".equals(payload.path("type").asString("")) || !last.isObject()) {
                return TokenUsage.NONE;
            }
            long cached = last.path("cached_input_tokens").asLong(0);
            return TokenUsage.ofUnpricedCall(last.path("input_tokens").asLong(0) - cached, cached,
                    last.path("output_tokens").asLong(0));
        } catch (RuntimeException unreadable) {
            return TokenUsage.NONE;
        }
    }
}
