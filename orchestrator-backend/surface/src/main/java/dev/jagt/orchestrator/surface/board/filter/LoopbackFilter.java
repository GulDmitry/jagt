package dev.jagt.orchestrator.surface.board.filter;

import dev.jagt.orchestrator.service.log.BoardUrl;
import dev.jagt.orchestrator.surface.board.auth.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A page on another site can reach loopback: by its Origin, or by a name rebound to 127.0.0.1 in
 * its Host.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoopbackFilter extends OncePerRequestFilter {

  private final AuthProperties auth;
  private final BoardUrl boardUrl;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<String> foreign = foreign(request);
    if (foreign.isPresent()) {
      log.atWarn()
          .setMessage("request refused")
          .addKeyValue("url", request.getRequestURI())
          .addKeyValue("cause", foreign.get())
          .log();
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    chain.doFilter(request, response);
  }

  private Optional<String> foreign(HttpServletRequest request) {
    if (!listed(request.getServerName())
        && (!own(request.getServerName(), request)
            || request.getServerPort() != request.getLocalPort())) {
      return Optional.of("foreign host " + request.getServerName() + ":" + request.getServerPort());
    }
    String origin = request.getHeader("Origin");
    return origin == null || ownOrigin(origin, request)
        ? Optional.empty()
        : Optional.of("foreign origin " + origin);
  }

  /**
   * The address the connection arrived on is no name a page can rebind: it keeps `server.address`
   * usable.
   */
  private static boolean own(String host, HttpServletRequest request) {
    return BoardUrl.LOOPBACK.contains(host) || host.equals(request.getLocalAddr());
  }

  /** A name an ingress forwards under: the owner named it, so its port is the ingress's. */
  private boolean listed(String host) {
    return host.equalsIgnoreCase(boardUrl.host())
        || auth.allowedHosts().stream().anyMatch(host::equalsIgnoreCase);
  }

  private boolean ownOrigin(String origin, HttpServletRequest request) {
    try {
      URI uri = URI.create(origin);
      if (uri.getHost() == null) {
        return false;
      }
      boolean web = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
      return (web && listed(uri.getHost()))
          || ("http".equals(uri.getScheme())
              && own(uri.getHost(), request)
              && uri.getPort() == request.getLocalPort());
    } catch (IllegalArgumentException unparsable) {
      return false;
    }
  }
}
