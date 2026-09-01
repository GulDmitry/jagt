package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.service.OriginContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class OriginFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ActionOrigin origin = originOf(request.getRequestURI());
        if (origin == null) {
            chain.doFilter(request, response);
            return;
        }
        try (var ignored = OriginContext.open(origin)) {
            chain.doFilter(request, response);
        }
    }

    private static ActionOrigin originOf(String path) {
        if (path.equals("/mcp")) {
            return ActionOrigin.MCP;
        }
        return path.startsWith("/api/") ? ActionOrigin.BOARD : null;
    }
}
