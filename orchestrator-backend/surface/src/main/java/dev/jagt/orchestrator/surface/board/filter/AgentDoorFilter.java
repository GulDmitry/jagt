package dev.jagt.orchestrator.surface.board.filter;

import dev.jagt.orchestrator.surface.board.auth.AuthProperties;
import dev.jagt.orchestrator.surface.mcp.MasterToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Raw and resolved path are both judged: routing resolves {@code ;params} and {@code %xx}. */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class AgentDoorFilter extends OncePerRequestFilter {

  private final AuthProperties auth;
  private final MasterToken masterToken;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String resolved = request.getServletPath() + Objects.toString(request.getPathInfo(), "");
    return auth.mode() == AuthProperties.Mode.NONE
        || !(door(request.getRequestURI()) || door(resolved));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!onThisMachine(request) && !masterToken.matches(request.getHeader(MasterToken.HEADER))) {
      log.atWarn()
          .setMessage("request refused")
          .addKeyValue("url", request.getRequestURI())
          .addKeyValue("cause", "no Master token from a peer off loopback")
          .log();
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean door(String path) {
    return path.startsWith("/mcp") || path.startsWith("/api/agent/");
  }

  /** A proxy may rewrite the peer address from a forwarded header, which the caller writes. */
  private static boolean onThisMachine(HttpServletRequest request) {
    return request.getHeader("Forwarded") == null
        && request.getHeader("X-Forwarded-For") == null
        && loopback(request.getRemoteAddr());
  }

  private static boolean loopback(String address) {
    try {
      return InetAddress.getByName(address).isLoopbackAddress();
    } catch (UnknownHostException unparsable) {
      return false;
    }
  }
}
