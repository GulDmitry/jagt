package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.surface.mcp.MasterToken;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class BoardWriteFilterTest {

    @Test
    void refusesAWriteFromAProcessThatIsNeitherThePageNorTheMaster() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks/actions/deploy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new BoardWriteFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void refusesAWriteCarryingOnlyTheMastersToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks/say");
        request.addHeader(MasterToken.HEADER, "t0ken");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new BoardWriteFilter().doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void leavesASessionsHookToItsWorktree() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/session/gone");
        request.setServletPath("/api/agent/session/gone");
        MockFilterChain chain = new MockFilterChain();

        new BoardWriteFilter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }
}
