package dev.jagt.orchestrator.surface.board.filter;

import dev.jagt.orchestrator.surface.board.auth.attempts.LoginAttempts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** A peer that failed the password too often is refused before the password is read. */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class LoginBackoffFilter extends OncePerRequestFilter {

  private final LoginAttempts attempts;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod()) || !"/login".equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Duration left = attempts.remaining(request.getRemoteAddr());
    if (left.isZero()) {
      chain.doFilter(request, response);
      return;
    }
    log.atWarn()
        .setMessage("login refused")
        .addKeyValue("peer", request.getRemoteAddr())
        .addKeyValue("cause", "backed off after failed logins")
        .log();
    response.setHeader("Retry-After", String.valueOf(left.toSeconds() + 1));
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
  }
}
