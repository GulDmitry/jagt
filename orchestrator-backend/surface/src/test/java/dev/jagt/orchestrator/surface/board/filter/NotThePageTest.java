package dev.jagt.orchestrator.surface.board.filter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.log.BoardUrl;
import dev.jagt.orchestrator.surface.board.auth.AuthProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

class NotThePageTest {

  private static final AuthProperties NONE =
      new AuthProperties(AuthProperties.Mode.NONE, null, List.of());

  private final NotThePage refused = new NotThePage(NONE, new BoardUrl("8290", ""));

  @ParameterizedTest
  @CsvSource({"evil.example, 8290", "127.0.0.1, 80"})
  void refusesRequestWhoseHostIsNotThisServer(String host, int port) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/tasks/actions/deploy");
    request.setServerName(host);
    request.setServerPort(port);
    request.setLocalPort(8290);
    request.addHeader("Origin", "http://localhost:8290");

    assertThat(refused.matches(request)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"https://evil.example", "null", "http://127.0.0.1:9999", "http://bad host:8290"})
  void refusesRequestSentFromAnotherOrigin(String origin) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setServerName("127.0.0.1");
    request.setServerPort(8290);
    request.setLocalPort(8290);
    request.addHeader("Origin", origin);

    assertThat(refused.matches(request)).isTrue();
  }

  @Test
  void admitsTheBoardsOwnPage() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/commands/do");
    request.setServerName("localhost");
    request.setServerPort(8290);
    request.setLocalPort(8290);
    request.addHeader("Origin", "http://localhost:8290");

    assertThat(refused.matches(request)).isFalse();
  }

  @Test
  void admitsTheAddressTheServerIsBoundTo() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/state");
    request.setServerName("192.168.1.20");
    request.setServerPort(8290);
    request.setLocalPort(8290);
    request.setLocalAddr("192.168.1.20");

    assertThat(refused.matches(request)).isFalse();
  }

  @Test
  void admitsHostAndOriginOnTheAllowList() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/commands/do");
    request.setServerName("jagt.example.test");
    request.setServerPort(80);
    request.setLocalPort(8290);
    request.addHeader("Origin", "https://jagt.example.test");
    AuthProperties auth =
        new AuthProperties(AuthProperties.Mode.NONE, null, List.of("jagt.example.test"));

    assertThat(new NotThePage(auth, new BoardUrl("8290", "")).matches(request)).isFalse();
  }

  @Test
  void refusesAllowListedNameSentFromAnotherScheme() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/commands/do");
    request.setServerName("jagt.example.test");
    request.setServerPort(80);
    request.setLocalPort(8290);
    request.addHeader("Origin", "ftp://jagt.example.test");
    AuthProperties auth =
        new AuthProperties(AuthProperties.Mode.NONE, null, List.of("jagt.example.test"));

    assertThat(new NotThePage(auth, new BoardUrl("8290", "")).matches(request)).isTrue();
  }

  @Test
  void admitsHostAndOriginOfThePublicUrl() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/commands/do");
    request.setServerName("jagt.example.com");
    request.setServerPort(443);
    request.setLocalPort(8290);
    request.addHeader("Origin", "https://jagt.example.com");
    AuthProperties auth = new AuthProperties(AuthProperties.Mode.PASSWORD, null, List.of());

    NotThePage door = new NotThePage(auth, new BoardUrl("8290", "https://jagt.example.com"));

    assertThat(door.matches(request)).isFalse();
  }

  @Test
  void refusesHostThatIsNeitherThePublicUrlNorListed() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/state");
    request.setServerName("evil.example");
    request.setServerPort(443);
    request.setLocalPort(8290);
    AuthProperties auth = new AuthProperties(AuthProperties.Mode.PASSWORD, null, List.of());

    NotThePage door = new NotThePage(auth, new BoardUrl("8290", "https://jagt.example.com"));

    assertThat(door.matches(request)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"POST, /api/tasks/actions/deploy", "POST, /api/tasks/say", "DELETE, /api/tasks/x"})
  void refusesWriteFromProcessThatIsNeitherThePageNorTheMaster(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.addHeader("X-Jagt-Master", "t0ken");

    assertThat(refused.cause(request)).contains("no Origin");
  }

  @Test
  void refusesWebSocketUpgradeCarryingNoOriginBecauseItTypesAsTheHuman() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/windows/stream");
    request.addHeader("Upgrade", "websocket");

    assertThat(refused.cause(request)).contains("no Origin");
  }

  @Test
  void admitsWriteCarryingTheBoardsOrigin() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks/say");
    request.addHeader("Origin", "http://localhost:8290");

    assertThat(refused.matches(request)).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"/mcp", "/api/agent/session/gone"})
  void leavesTheAgentDoorToItsToken(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);

    assertThat(refused.matches(request)).isFalse();
  }
}
