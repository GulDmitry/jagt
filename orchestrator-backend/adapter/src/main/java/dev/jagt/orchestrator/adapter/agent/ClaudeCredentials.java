package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.Host;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The login Claude Code stored: the macOS Keychain first, else its credentials file. Never
 * refreshed here.
 */
@Component
@ConditionalOnProperty(
    name = "orchestrator.agent.cli",
    havingValue = "claude",
    matchIfMissing = true)
public class ClaudeCredentials {

  private static final JsonMapper JSON = new JsonMapper();

  private final Optional<ClaudeKeychain> keychain;
  private final Path file;

  public ClaudeCredentials(Optional<ClaudeKeychain> keychain, Host host) {
    this.keychain = keychain;
    this.file = host.home().resolve(".claude").resolve(".credentials.json");
  }

  OauthCredential read(Instant now) {
    Optional<OauthCredential> stored = keychain.flatMap(k -> k.read(now));
    if (stored.isPresent()) {
      return stored.get();
    }
    if (!Files.exists(file)) {
      return new OauthCredential.None();
    }
    try {
      return parse(Files.readString(file), now);
    } catch (IOException e) {
      return new OauthCredential.Failed(
          "credentials file unreadable " + e.getClass().getSimpleName());
    }
  }

  static OauthCredential parse(String stored, Instant now) {
    JsonNode login;
    try {
      login = JSON.readTree(stored).path("claudeAiOauth");
    } catch (JacksonException e) {
      return new OauthCredential.Failed("stored login is not JSON");
    }
    if (login.isMissingNode() || login.isNull()) {
      return new OauthCredential.None();
    }
    String token = login.path("accessToken").asString("");
    if (token.isBlank()) {
      return new OauthCredential.Failed("stored login carries no access token");
    }
    JsonNode expiresAt = login.path("expiresAt");
    if (expiresAt.isNumber() && !Instant.ofEpochMilli(expiresAt.longValue()).isAfter(now)) {
      return new OauthCredential.Failed(
          "token expired at "
              + Instant.ofEpochMilli(expiresAt.longValue())
              + ", running claude refreshes it");
    }
    return new OauthCredential.Token(token, "");
  }
}
