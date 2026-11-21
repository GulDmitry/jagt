package dev.jagt.orchestrator.surface.board.filter;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatchers;

/** The paths sessions and the Master reach by their own token and header, not by the page. */
final class AgentDoor {

  static final RequestMatcher PATHS =
      RequestMatchers.anyOf(
          PathPatternRequestMatcher.withDefaults().matcher("/mcp"),
          PathPatternRequestMatcher.withDefaults().matcher("/mcp/**"),
          PathPatternRequestMatcher.withDefaults().matcher("/api/agent/**"));

  private AgentDoor() {}
}
