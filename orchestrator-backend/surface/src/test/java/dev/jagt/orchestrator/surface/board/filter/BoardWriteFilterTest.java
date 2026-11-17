package dev.jagt.orchestrator.surface.board.filter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.surface.mcp.MasterToken;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BoardWriteFilterTest {

  @Test
  void refusesWriteFromProcessThatIsNeitherThePageNorTheMaster() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/tasks/actions/deploy");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    new BoardWriteFilter().doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void refusesWebSocketUpgradeCarryingNoOriginBecauseItTypesAsTheHuman() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/windows/stream");
    request.addHeader("Upgrade", "websocket");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new BoardWriteFilter().doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void refusesWriteCarryingOnlyTheMastersToken() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks/say");
    request.addHeader(MasterToken.HEADER, "t0ken");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new BoardWriteFilter().doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void admitsWriteCarryingTheBoardsOrigin() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks/say");
    request.addHeader("Origin", "http://localhost:8290");
    MockFilterChain chain = new MockFilterChain();

    new BoardWriteFilter().doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void leavesTheAgentsMcpEndpointToItsToken() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setServletPath("/mcp");
    MockFilterChain chain = new MockFilterChain();

    new BoardWriteFilter().doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void leavesSessionsHookToItsWorktree() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/session/gone");
    request.setServletPath("/api/agent/session/gone");
    MockFilterChain chain = new MockFilterChain();

    new BoardWriteFilter().doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }
}
