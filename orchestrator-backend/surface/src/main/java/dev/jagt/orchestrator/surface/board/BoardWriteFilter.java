package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.surface.mcp.MasterToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * A write to the board acts as the human, so it carries the page's Origin, which a browser always sends and
 * {@link LoopbackFilter} has already judged, or the Master's token. A session's hooks post under
 * {@code /api/agent/} as their worktree instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BoardWriteFilter extends OncePerRequestFilter {

    private final MasterToken masterToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())
                || !path.startsWith("/api/") || path.startsWith("/api/agent/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader("Origin") == null && !masterToken.matches(request.getHeader(MasterToken.HEADER))) {
            log.atWarn().setMessage("request refused")
                    .addKeyValue("url", request.getRequestURI())
                    .addKeyValue("cause", "no Origin and no Master token")
                    .log();
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
