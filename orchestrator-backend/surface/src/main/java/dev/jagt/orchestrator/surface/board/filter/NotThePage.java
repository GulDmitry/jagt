package dev.jagt.orchestrator.surface.board.filter;

import dev.jagt.orchestrator.service.log.BoardUrl;
import dev.jagt.orchestrator.surface.board.auth.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * A page on another site can reach loopback: by its Origin, or by a name rebound to 127.0.0.1 in
 * its Host. A write, or a WebSocket upgrade whose frames type as the human, acts as the human, so
 * it carries the page's Origin, which a browser always sends.
 */
@RequiredArgsConstructor
final class NotThePage implements RequestMatcher {

  private final AuthProperties auth;
  private final BoardUrl boardUrl;

  @Override
  public boolean matches(HttpServletRequest request) {
    return cause(request).isPresent();
  }

  Optional<String> cause(HttpServletRequest request) {
    return foreign(request).or(() -> writeWithoutPage(request));
  }

  private Optional<String> writeWithoutPage(HttpServletRequest request) {
    return writes(request)
            && request.getHeader("Origin") == null
            && !AgentDoor.PATHS.matches(request)
        ? Optional.of("no Origin")
        : Optional.empty();
  }

  private static boolean writes(HttpServletRequest request) {
    return !("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
        || "websocket".equalsIgnoreCase(request.getHeader("Upgrade"));
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
