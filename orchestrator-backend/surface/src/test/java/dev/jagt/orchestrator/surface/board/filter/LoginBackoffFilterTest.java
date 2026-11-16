package dev.jagt.orchestrator.surface.board.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.surface.board.auth.attempts.LoginAttempts;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginBackoffFilterTest {

  private final LoginAttempts attempts = mock(LoginAttempts.class);
  private final LoginBackoffFilter filter = new LoginBackoffFilter(attempts);

  @Test
  void refusesLoginOfBackedOffPeerWithTheTimeToWait() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
    request.setRemoteAddr("10.0.0.1");
    when(attempts.remaining("10.0.0.1")).thenReturn(Duration.ofSeconds(90));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isEqualTo("91");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void letsLoginOfPeerWithNoBackoffThrough() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
    request.setRemoteAddr("10.0.0.2");
    when(attempts.remaining("10.0.0.2")).thenReturn(Duration.ZERO);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void leavesTheLoginPageOfBackedOffPeerReadable() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login.html");
    request.setRemoteAddr("10.0.0.1");
    when(attempts.remaining("10.0.0.1")).thenReturn(Duration.ofSeconds(90));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
  }
}
