package dev.jagt.orchestrator.surface.board;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LoopbackFilterTest {

    @ParameterizedTest
    @CsvSource({"evil.example, 8290", "127.0.0.1, 80"})
    void refusesARequestWhoseHostIsNotThisServer(String host, int port) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks/actions/deploy");
        request.setServerName(host);
        request.setServerPort(port);
        request.setLocalPort(8290);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new LoopbackFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"https://evil.example", "null", "http://127.0.0.1:9999"})
    void refusesARequestSentFromAnotherOrigin(String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setServerName("127.0.0.1");
        request.setServerPort(8290);
        request.setLocalPort(8290);
        request.addHeader("Origin", origin);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new LoopbackFilter().doFilter(request, response, chain);

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

        new LoopbackFilter().doFilter(request, new MockHttpServletResponse(), chain);

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

        new LoopbackFilter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }
}
