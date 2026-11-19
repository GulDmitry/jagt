package dev.jagt.orchestrator.surface.ui.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import dev.jagt.orchestrator.protocol.debug.RecordTail;
import dev.jagt.orchestrator.service.log.DebugReads;
import dev.jagt.orchestrator.service.role.RoleLog;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** The log as the run writes it: the file's tail, or under console logging what stdout carried. */
@Component
@RequiredArgsConstructor
public class LogReads {

  static final String APPENDER = "RECENT";

  private final LoggerContext context;
  private final Environment environment;
  private final DebugReads reads;
  private final List<RoleLog> roleLogs;

  public String read(RecordTail request) {
    int lines = request.count();
    String record = request.record();
    if (record.equals("backend-log")) {
      return of("logging.file.name", lines);
    }
    return roleLogs.stream()
        .filter(log -> log.record().equals(record))
        .findFirst()
        .map(log -> of(log.fileKey(), lines))
        .orElseGet(() -> record.equals("stops") ? reads.stops(lines) : reads.stopsReviewed(lines));
  }

  private String of(String fileKey, int lines) {
    if (environment.acceptsProfiles(Profiles.of(LogTarget.CONSOLE_PROFILE))) {
      return String.join("\n", recent().last(lines));
    }
    return reads.tail(Path.of(environment.getRequiredProperty(fileKey)), lines);
  }

  private RecentLines recent() {
    return (RecentLines) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender(APPENDER);
  }
}
