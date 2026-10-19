package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Processes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** The login Claude Code stored: the macOS Keychain first, else its credentials file. Never refreshed here. */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "claude", matchIfMissing = true)
public class ClaudeCredentials {

    private static final JsonMapper JSON = new JsonMapper();
    private static final String KEYCHAIN_SERVICE = "Claude Code-credentials";
    private static final int KEYCHAIN_ITEM_MISSING = 44;
    private static final Duration KEYCHAIN_TIMEOUT = Duration.ofSeconds(10);

    private final Processes processes;
    private final boolean macos;
    private final Path file;

    public ClaudeCredentials(Processes processes, Host host, @Value("${orchestrator.platform:macos}") String platform) {
        this.processes = processes;
        this.macos = "macos".equals(platform);
        this.file = host.home().resolve(".claude").resolve(".credentials.json");
    }

    OAuthCredential read(Instant now) {
        if (macos) {
            Processes.Result keychain;
            try {
                keychain = processes.run(null, KEYCHAIN_TIMEOUT, List.of(Executables.resolve("security"),
                        "find-generic-password", "-s", KEYCHAIN_SERVICE, "-w"));
            } catch (IllegalStateException e) {
                return new OAuthCredential.Failed("keychain read failed " + e.getMessage());
            }
            if (keychain.exitCode() == 0) {
                return parse(keychain.stdout(), now);
            }
            if (keychain.exitCode() != KEYCHAIN_ITEM_MISSING) {
                return new OAuthCredential.Failed("keychain read exited " + keychain.exitCode() + " "
                        + keychain.stderr());
            }
        }
        if (!Files.exists(file)) {
            return new OAuthCredential.None();
        }
        try {
            return parse(Files.readString(file), now);
        } catch (IOException e) {
            return new OAuthCredential.Failed("credentials file unreadable " + e.getClass().getSimpleName());
        }
    }

    static OAuthCredential parse(String stored, Instant now) {
        JsonNode login;
        try {
            login = JSON.readTree(stored).path("claudeAiOauth");
        } catch (JacksonException e) {
            return new OAuthCredential.Failed("stored login is not JSON");
        }
        if (login.isMissingNode() || login.isNull()) {
            return new OAuthCredential.None();
        }
        String token = login.path("accessToken").asString("");
        if (token.isBlank()) {
            return new OAuthCredential.Failed("stored login carries no access token");
        }
        JsonNode expiresAt = login.path("expiresAt");
        if (expiresAt.isNumber() && !Instant.ofEpochMilli(expiresAt.longValue()).isAfter(now)) {
            return new OAuthCredential.Failed("token expired at " + Instant.ofEpochMilli(expiresAt.longValue())
                    + ", running claude refreshes it");
        }
        return new OAuthCredential.Token(token, "");
    }
}
