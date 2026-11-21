package dev.jagt.orchestrator.surface.board.filter;

import dev.jagt.orchestrator.surface.mcp.refusal.RootToken;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** A session on this machine passes by its worktree header; any other peer needs the root token. */
@RequiredArgsConstructor
final class LoopbackOrRootToken implements AuthorizationManager<RequestAuthorizationContext> {

  private final RootToken rootToken;

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    HttpServletRequest request = context.getRequest();
    return new AuthorizationDecision(
        onThisMachine(request) || rootToken.presentedBy(request::getHeader));
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
