package dev.jagt.orchestrator.surface.board.filter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.log.BoardUrl;
import dev.jagt.orchestrator.surface.board.auth.AuthProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoopbackFilterTest {

  @ParameterizedTest
  @CsvSource({"evil.example, 8290", "127.0.0.1, 80"})
  void refusesRequestWhoseHostIsNotThisServer(String host, int port) throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/tasks/actions/deploy");
    request.setServerName(host);
    request.setServerPort(port);
    request.setLocalPort(8290);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    new LoopbackFilter(
            new AuthProperties(AuthProperties.Mode.NONE, null, List.of()), new BoardUrl("8290", ""))
        .doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(chain.getRequest()).isNull();
  }

  @ParameterizedTest
  @CsvSource({"https://evil.example", "null", "http://127.0.0.1:9999", "http://bad host:8290"})
  void refusesRequestSentFromAnotherOrigin(String origin) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setServerName("127.0.0.1");
    request.setServerPort(8290);
    request.setLocalPort(8290);
    request.addHeader("Origin", origin);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    AuthProperties auth = new AuthProperties(AuthProperties.Mode.NONE, null, List.of());

    new LoopbackFilter(auth, new BoardUrl("8290", "")).doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void admitsTheBoardsOwnPage() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/commands/do");
    request.setServerName("localhost");
    request.setServerPort(8290);
    request.setLocalPort(8290);
    request.addHeader("Origin", "http://localhost:8290");
    MockFilterChain chain = new MockFilterChain();

    new LoopbackFilter(
            new AuthProperties(AuthProperties.Mode.NONE, null, List.of()), new BoardUrl("8290", ""))
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void admitsTheAddressTheServerIsBoundTo() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/state");
    request.setServerName("192.168.1.20");
    request.setServerPort(8290);
    request.setLocalPort(8290);
    request.setLocalAddr("192.168.1.20");
    MockFilterChain chain = new MockFilterChain();

    new LoopbackFilter(
            new AuthProperties(AuthProperties.Mode.NONE, null, List.of()), new BoardUrl("8290", ""))
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void admitsHostAndOriginOnTheAllowList() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/commands/do");
    request.setServerName("jagt.example.test");
    request.setServerPort(80);
    request.setLocalPort(8290);
    request.addHeader("Origin", "https://jagt.example.test");
    MockFilterChain chain = new MockFilterChain();
    AuthProperties auth =
        new AuthProperties(AuthProperties.Mode.NONE, null, List.of("jagt.example.test"));

    new LoopbackFilter(auth, new BoardUrl("8290", ""))
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void refusesAllowListedNameSentFromAnotherScheme() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/commands/do");
    request.setServerName("jagt.example.test");
    request.setServerPort(80);
    request.setLocalPort(8290);
    request.addHeader("Origin", "ftp://jagt.example.test");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthProperties auth =
        new AuthProperties(AuthProperties.Mode.NONE, null, List.of("jagt.example.test"));

    new LoopbackFilter(auth, new BoardUrl("8290", ""))
        .doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void admitsHostAndOriginOfThePublicUrl() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/commands/do");
    request.setServerName("jagt.example.com");
    request.setServerPort(443);
    request.setLocalPort(8290);
    request.addHeader("Origin", "https://jagt.example.com");
    MockFilterChain chain = new MockFilterChain();
    AuthProperties auth = new AuthProperties(AuthProperties.Mode.PASSWORD, null, List.of());

    new LoopbackFilter(auth, new BoardUrl("8290", "https://jagt.example.com"))
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void refusesHostThatIsNeitherThePublicUrlNorListed() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/state");
    request.setServerName("evil.example");
    request.setServerPort(443);
    request.setLocalPort(8290);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthProperties auth = new AuthProperties(AuthProperties.Mode.PASSWORD, null, List.of());

    new LoopbackFilter(auth, new BoardUrl("8290", "https://jagt.example.com"))
        .doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(403);
  }
}
