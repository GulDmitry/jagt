package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.ActionOrigin;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OriginFilterTest {

    private final AtomicReference<ActionOrigin> seen = new AtomicReference<>();
    private final FilterChain chain = (request, response) -> seen.set(OriginContext.current());

    @Test
    void attributesAnUnmarkedCallToWhoeverIsAtTheKeyboard() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");

        new OriginFilter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seen.get()).isEqualTo(ActionOrigin.MCP);
    }
}
