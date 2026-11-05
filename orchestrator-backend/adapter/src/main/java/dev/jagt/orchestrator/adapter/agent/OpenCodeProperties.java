package dev.jagt.orchestrator.adapter.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The OpenCode runtime's binary and, optionally, the one OpenAI-compatible endpoint and model its sessions use. */
@ConfigurationProperties(prefix = "orchestrator.opencode")
public record OpenCodeProperties(String command, String endpoint, String model) {

    public OpenCodeProperties {
        command = command == null || command.isBlank() ? "opencode" : command.strip();
        endpoint = endpoint == null ? "" : endpoint.strip();
        model = model == null ? "" : model.strip();
    }

    public static OpenCodeProperties defaults() {
        return new OpenCodeProperties("", "", "");
    }

    public boolean hasEndpoint() {
        return !endpoint.isEmpty();
    }
}
