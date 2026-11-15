package dev.jagt.orchestrator.surface.ui.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import dev.jagt.orchestrator.protocol.debug.RecordTail;
import dev.jagt.orchestrator.service.log.DebugReads;
import java.nio.file.Path;
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

  public String read(RecordTail request) {
    int lines = request.count();
    return switch (request.record()) {
      case "backend-log" -> of("logging.file.name", lines);
      case "master-log" -> of("logging.master.name", lines);
      case "stops" -> reads.stops(lines);
      default -> reads.stopsReviewed(lines);
    };
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
