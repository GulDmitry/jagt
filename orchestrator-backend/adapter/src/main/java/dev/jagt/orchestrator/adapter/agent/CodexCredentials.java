package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.Host;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.stream.Stream;

/** The login Codex stored in the human's own {@code CODEX_HOME}, never a worktree's. Never refreshed here. */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "codex")
public class CodexCredentials {

    private static final JsonMapper JSON = new JsonMapper();
    private static final String AUTH_CLAIMS = "https://api.openai.com/auth";

    private final Path file;

    public CodexCredentials(Host host) {
        String configured = host.env("CODEX_HOME").orElse("");
        this.file = (configured.isBlank() ? host.home().resolve(".codex") : Path.of(configured)).resolve("auth.json");
    }

    OAuthCredential read(Instant now) {
        if (!Files.exists(file)) {
            return new OAuthCredential.None();
        }
        try {
            return parse(Files.readString(file), now);
        } catch (IOException e) {
            return new OAuthCredential.Failed("auth file unreadable " + e.getClass().getSimpleName());
        }
    }

    static OAuthCredential parse(String stored, Instant now) {
        JsonNode tokens;
        try {
            tokens = JSON.readTree(stored).path("tokens");
        } catch (JacksonException e) {
            return new OAuthCredential.Failed("stored login is not JSON");
        }
        if (!tokens.isObject()) {
            return new OAuthCredential.None();
        }
        String token = tokens.path("access_token").asString("");
        if (token.isBlank()) {
            return new OAuthCredential.Failed("stored login carries no access token");
        }
        JsonNode expiresAt = claims(token).path("exp");
        if (expiresAt.isNumber() && !Instant.ofEpochSecond(expiresAt.longValue()).isAfter(now)) {
            return new OAuthCredential.Failed("token expired at " + Instant.ofEpochSecond(expiresAt.longValue())
                    + ", running codex refreshes it");
        }
        String account = Stream.of(tokens.path("account_id").asString(""),
                        claims(tokens.path("id_token").asString("")).path(AUTH_CLAIMS).path("chatgpt_account_id").asString(""),
                        claims(token).path(AUTH_CLAIMS).path("chatgpt_account_id").asString(""))
                .filter(id -> !id.isBlank()).findFirst().orElse("");
        if (account.isBlank()) {
            return new OAuthCredential.Failed("stored login names no account");
        }
        return new OAuthCredential.Token(token, account);
    }

    private static JsonNode claims(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return JSON.missingNode();
        }
        try {
            return JSON.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | JacksonException e) {
            return JSON.missingNode();
        }
    }
}
