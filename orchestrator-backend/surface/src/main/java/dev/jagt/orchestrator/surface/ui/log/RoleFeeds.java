package dev.jagt.orchestrator.surface.ui.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import dev.jagt.orchestrator.service.role.model.ModelRoles;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The feed of every model role: one in-memory appender per role, named {@code ROLE_<id>} in the
 * logback file, each announcing its new lines, and read by the board after a cursor.
 */
@RestController
@RequestMapping("/api/roles")
public class RoleFeeds {

  static final String PREFIX = "ROLE_";

  /** A role's feed gained a line. */
  public record RoleFeedWritten(String role) {}

  private final LoggerContext context;
  private final ModelRoles roles;

  public RoleFeeds(LoggerContext context, ApplicationEventPublisher events, ModelRoles roles) {
    this.context = context;
    this.roles = roles;
    for (Logger logger : context.getLoggerList()) {
      logger
          .iteratorForAppenders()
          .forEachRemaining(appender -> {
            if (appender instanceof RecentLines lines && appender.getName().startsWith(PREFIX)) {
              String role = appender.getName().substring(PREFIX.length());
              lines.onLine(() -> events.publishEvent(new RoleFeedWritten(role)));
            }
          });
    }
  }

  /** What the role's feed holds after {@code cursor}; empty where the role has no feed. */
  public Optional<RecentLines.Slice> after(String role, long cursor) {
    for (Logger logger : context.getLoggerList()) {
      if (logger.getAppender(PREFIX + role) instanceof RecentLines lines) {
        return Optional.of(lines.after(cursor));
      }
    }
    return Optional.empty();
  }

  @GetMapping("/{id}/log")
  public RecentLines.Slice log(
      @PathVariable String id, @RequestParam(defaultValue = "0") long after) {
    if (!roles.runs(id)) {
      throw new IllegalArgumentException("No running role '" + id + "'");
    }
    return after(id, after)
        .orElseThrow(() -> new IllegalArgumentException("No feed for '" + id + "'"));
  }
}
