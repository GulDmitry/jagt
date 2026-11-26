package dev.jagt.orchestrator.surface.board.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatchers;

/** The paths of an OAuth flow jagt does not have: a client reaches them only by a stale token. */
@Slf4j
final class NoOauth implements AuthenticationEntryPoint {

  static final RequestMatcher PATHS =
      RequestMatchers.anyOf(
          PathPatternRequestMatcher.withDefaults().matcher("/register"),
          PathPatternRequestMatcher.withDefaults().matcher("/.well-known/oauth-*"),
          PathPatternRequestMatcher.withDefaults().matcher("/.well-known/oauth-*/**"));

  static final String BODY =
      "jagt has no OAuth. A stale root token after a jagt restart sends a client here:"
          + " restart the session.";

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException denied)
      throws IOException {
    log.atWarn()
        .setMessage("oauth discovery refused")
        .addKeyValue("url", request.getRequestURI())
        .addKeyValue("cause", "stale root token after a jagt restart")
        .log();
    response.setStatus(HttpStatus.NOT_FOUND.value());
    response.setContentType(MediaType.TEXT_PLAIN_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(BODY);
  }
}
