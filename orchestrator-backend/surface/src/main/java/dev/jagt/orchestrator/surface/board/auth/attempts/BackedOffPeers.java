package dev.jagt.orchestrator.surface.board.auth.attempts;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Spring Security has no per-peer lockout: a peer that failed the password too often is refused
 * before the password is checked, and the refusal is no new failure.
 */
@RequiredArgsConstructor
public final class BackedOffPeers implements AuthenticationProvider {

  private final LoginAttempts attempts;

  @Override
  public @Nullable Authentication authenticate(Authentication attempt) {
    if (attempt.getDetails() instanceof WebAuthenticationDetails web) {
      Duration left = attempts.remaining(web.getRemoteAddress());
      if (!left.isZero()) {
        throw new BackedOff(web.getRemoteAddress(), left);
      }
    }
    return null;
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }

  /** An account status failure ends the provider chain at once, unlike a bad credential. */
  @Getter
  public static final class BackedOff extends AccountStatusException {

    private static final long serialVersionUID = 1L;

    private final String peer;
    private final Duration left;

    BackedOff(String peer, Duration left) {
      super("backed off after failed logins");
      this.peer = peer;
      this.left = left;
    }
  }
}
