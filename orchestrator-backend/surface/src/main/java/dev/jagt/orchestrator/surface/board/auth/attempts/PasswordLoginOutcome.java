package dev.jagt.orchestrator.surface.board.auth.attempts;

import dev.jagt.orchestrator.service.Causes;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/** What a password login's outcome owes the peer's count and the audit line. */
@Component
@RequiredArgsConstructor
public class PasswordLoginOutcome {

  private final LoginAttempts attempts;
  private final LoginAudit audit;

  public void failed(String peer, AuthenticationException failure) {
    attempts.failed(peer);
    audit.failed(peer, "password", Causes.of(failure));
  }

  public void succeeded(String peer) {
    attempts.succeeded(peer);
    audit.succeeded(peer, "password");
  }
}
