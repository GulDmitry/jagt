package dev.jagt.orchestrator.surface.board;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * A write acts as the human, so it carries the page's Origin, which a browser always sends and
 * {@link LoopbackFilter} has already judged. The Master acts through {@code /mcp}; a session's hooks post under
 * {@code /api/agent/} as their worktree. Those two are exempt only when the raw path is the one routed:
 * {@code ;params} and {@code %xx} are resolved before routing.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BoardWriteFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean resolved = path.equals(request.getServletPath() + Objects.toString(request.getPathInfo(), ""));
        return "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())
                || resolved && (path.equals("/mcp") || path.startsWith("/api/agent/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader("Origin") == null) {
            log.atWarn().setMessage("request refused")
                    .addKeyValue("url", request.getRequestURI())
                    .addKeyValue("cause", "no Origin")
                    .log();
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
