package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@With
@ConfigurationProperties(prefix = "orchestrator.assistant.local")
public record LocalModelProperties(String endpoint, String model, Duration timeout, List<AssistantCallKind> kinds) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    public LocalModelProperties {
        endpoint = endpoint == null ? "" : endpoint.strip();
        model = model == null ? "" : model.strip();
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
        kinds = kinds == null ? List.of(AssistantCallKind.COMMAND_MAP) : List.copyOf(kinds);
    }

    public boolean on() {
        return !endpoint.isEmpty();
    }

    public boolean serves(AssistantCallKind kind) {
        return on() && kinds.contains(kind);
    }

    public static LocalModelProperties off() {
        return new LocalModelProperties("", "", DEFAULT_TIMEOUT, List.of(AssistantCallKind.COMMAND_MAP));
    }
}
