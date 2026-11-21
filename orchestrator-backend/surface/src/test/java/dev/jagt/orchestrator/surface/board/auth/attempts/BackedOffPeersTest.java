package dev.jagt.orchestrator.surface.board.auth.attempts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

class BackedOffPeersTest {

  private final LoginAttempts attempts = mock(LoginAttempts.class);
  private final BackedOffPeers provider = new BackedOffPeers(attempts);

  @Test
  void refusesLoginOfBackedOffPeerWithTheTimeToWait() {
    when(attempts.remaining("10.0.0.1")).thenReturn(Duration.ofSeconds(90));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
    request.setRemoteAddr("10.0.0.1");
    UsernamePasswordAuthenticationToken login =
        UsernamePasswordAuthenticationToken.unauthenticated("owner", "secret");
    login.setDetails(new WebAuthenticationDetails(request));

    assertThatThrownBy(() -> provider.authenticate(login))
        .isInstanceOfSatisfying(
            BackedOffPeers.BackedOff.class,
            backedOff -> {
              assertThat(backedOff.getLeft()).isEqualTo(Duration.ofSeconds(90));
              assertThat(backedOff.getPeer()).isEqualTo("10.0.0.1");
            });
  }

  @Test
  void leavesLoginOfPeerWithNoBackoffToThePasswordCheck() {
    when(attempts.remaining("10.0.0.2")).thenReturn(Duration.ZERO);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
    request.setRemoteAddr("10.0.0.2");
    UsernamePasswordAuthenticationToken login =
        UsernamePasswordAuthenticationToken.unauthenticated("owner", "secret");
    login.setDetails(new WebAuthenticationDetails(request));

    assertThat(provider.authenticate(login)).isNull();
  }

  @Test
  void checksOnlyPasswordLogins() {
    assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
    assertThat(provider.supports(AnonymousAuthenticationToken.class)).isFalse();
  }
}
