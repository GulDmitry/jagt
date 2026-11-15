package dev.jagt.orchestrator.surface.ui.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.protocol.debug.RecordTail;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.log.DebugReads;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class LogReadsTest {

  @Test
  void returnsTheLinesStdoutCarriedUnderConsoleLogging() {
    LoggerContext context = new LoggerContext();
    context.setMDCAdapter(new LogbackMDCAdapter());
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern("%level %msg%n");
    encoder.start();
    RecentLines recent = new RecentLines();
    recent.setContext(context);
    recent.setName(LogReads.APPENDER);
    recent.setEncoder(encoder);
    recent.start();
    Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
    root.addAppender(recent);
    root.info("first");
    root.warn("second");
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(LogTarget.CONSOLE_PROFILE);

    String read =
        new LogReads(context, environment, mock(DebugReads.class))
            .read(new RecordTail("backend-log", 10));

    assertThat(read)
        .describedAs(
            "started="
                + recent.isStarted()
                + " status="
                + context.getStatusManager().getCopyOfStatusList()
                + " tail="
                + recent.last(5))
        .isEqualTo("INFO first\nWARN second");
  }

  @Test
  void returnsTheLogFilesTailUnderFileLogging(@TempDir Path root) throws IOException {
    Path file = root.resolve("jagt-backend.log");
    Files.writeString(file, "a\nb\nc\n");
    MockEnvironment environment =
        new MockEnvironment().withProperty("logging.file.name", file.toString());

    String read =
        new LogReads(
                new LoggerContext(),
                environment,
                new DebugReads(
                    mock(OrchestratorPaths.class),
                    mock(StateService.class),
                    mock(AgentRuntime.class)))
            .read(new RecordTail("backend-log", 2));

    assertThat(read).isEqualTo("b\nc");
  }
}
