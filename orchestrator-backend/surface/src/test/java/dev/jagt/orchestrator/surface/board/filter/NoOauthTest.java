package dev.jagt.orchestrator.surface.board.filter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class NoOauthTest {

  private final NoOauth noOauth = new NoOauth();

  @ParameterizedTest
  @CsvSource({
    "POST, /register",
    "GET, /.well-known/oauth-authorization-server",
    "GET, /.well-known/oauth-protected-resource",
    "GET, /.well-known/oauth-protected-resource/mcp"
  })
  void claimsTheOauthPathsClientTriesAfterStaleToken(String method, String path) {
    assertThat(NoOauth.PATHS.matches(new MockHttpServletRequest(method, path))).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"POST, /mcp", "GET, /state", "POST, /api/tasks/say"})
  void leavesEveryOtherPathToItsOwnDoor(String method, String path) {
    assertThat(NoOauth.PATHS.matches(new MockHttpServletRequest(method, path))).isFalse();
  }

  @Test
  void answersNotFoundNamingTheStaleTokenWhereClientRegisters() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    noOauth.commence(
        new MockHttpServletRequest("POST", "/register"),
        response,
        new InsufficientAuthenticationException("denied"));

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getContentAsString())
        .isEqualTo(
            "jagt has no OAuth. A stale root token after a jagt restart sends a client here:"
                + " restart the session.");
  }

  @Test
  void logsOneStructuredWarnNamingTheUrlAndCause() throws Exception {
    try (FakeLogCapture log = new FakeLogCapture(NoOauth.class)) {
      noOauth.commence(
          new MockHttpServletRequest("GET", "/.well-known/oauth-authorization-server"),
          new MockHttpServletResponse(),
          new InsufficientAuthenticationException("denied"));

      assertThat(log.events()).hasSize(1);
      assertThat(log.events().getFirst().getLevel()).isEqualTo(Level.WARN);
      assertThat(log.events().getFirst().getMessage()).isEqualTo("oauth discovery refused");
      assertThat(log.events().getFirst().getKeyValuePairs())
          .extracting(pair -> pair.key + "=" + pair.value)
          .contains(
              "url=/.well-known/oauth-authorization-server",
              "cause=stale root token after a jagt restart");
    }
  }
}
