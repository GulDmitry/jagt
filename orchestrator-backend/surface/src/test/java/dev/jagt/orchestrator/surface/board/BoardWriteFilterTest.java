package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.surface.mcp.MasterToken;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardWriteFilterTest {

    @Test
    void refusesAWriteFromAProcessThatIsNeitherThePageNorTheMaster() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks/actions/deploy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new BoardWriteFilter(mock(MasterToken.class)).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void admitsAWriteCarryingTheMastersToken() throws Exception {
        MasterToken token = mock(MasterToken.class);
        when(token.matches("t0ken")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks/say");
        request.addHeader(MasterToken.HEADER, "t0ken");
        MockFilterChain chain = new MockFilterChain();

        new BoardWriteFilter(token).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void leavesASessionsHookToItsWorktree() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/session/gone");
        MockFilterChain chain = new MockFilterChain();

        new BoardWriteFilter(mock(MasterToken.class)).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }
}
