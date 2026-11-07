package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.port.Processes;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "orchestrator.agent.cli",
    havingValue = "claude",
    matchIfMissing = true)
@ConditionalOnProperty(
    prefix = "orchestrator",
    name = "platform",
    havingValue = "macos",
    matchIfMissing = true)
@RequiredArgsConstructor
class ClaudeKeychain {

  private static final String KEYCHAIN_SERVICE = "Claude Code-credentials";
  private static final int KEYCHAIN_ITEM_MISSING = 44;
  private static final Duration KEYCHAIN_TIMEOUT = Duration.ofSeconds(10);

  private final Processes processes;

  Optional<OauthCredential> read(Instant now) {
    Processes.Result keychain;
    try {
      keychain =
          processes.run(
              null,
              KEYCHAIN_TIMEOUT,
              List.of(
                  Executables.resolve("security"),
                  "find-generic-password",
                  "-s",
                  KEYCHAIN_SERVICE,
                  "-w"));
    } catch (IllegalStateException e) {
      return Optional.of(new OauthCredential.Failed("keychain read failed " + e.getMessage()));
    }
    if (keychain.exitCode() == 0) {
      return Optional.of(ClaudeCredentials.parse(keychain.stdout(), now));
    }
    if (keychain.exitCode() != KEYCHAIN_ITEM_MISSING) {
      return Optional.of(
          new OauthCredential.Failed(
              "keychain read exited " + keychain.exitCode() + " " + keychain.stderr()));
    }
    return Optional.empty();
  }
}
