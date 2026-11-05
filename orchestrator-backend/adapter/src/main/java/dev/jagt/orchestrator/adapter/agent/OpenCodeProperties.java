package dev.jagt.orchestrator.adapter.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The OpenCode runtime's binary and, optionally, the one OpenAI-compatible endpoint and model its sessions use.
 * {@code reads} names the roles whose read runs there too, each with its model; blank means {@code model}.
 */
@ConfigurationProperties(prefix = "orchestrator.opencode")
public record OpenCodeProperties(String command, String endpoint, String model, Map<String, String> reads) {

    public OpenCodeProperties {
        command = command == null || command.isBlank() ? "opencode" : command.strip();
        endpoint = endpoint == null ? "" : endpoint.strip();
        model = model == null ? "" : model.strip();
        Map<String, String> stripped = new LinkedHashMap<>();
        if (reads != null) {
            reads.forEach((role, readModel) -> stripped.put(role.strip(), readModel == null ? "" : readModel.strip()));
        }
        reads = Map.copyOf(stripped);
    }

    public static OpenCodeProperties defaults() {
        return new OpenCodeProperties("", "", "", Map.of());
    }

    public boolean hasEndpoint() {
        return !endpoint.isEmpty();
    }

    /** The model a role's read runs on; empty where the role's read stays on the default CLI. */
    public Optional<String> readModel(String role) {
        String named = reads.get(role);
        return named == null ? Optional.empty() : Optional.of(named.isEmpty() ? model : named);
    }
}
