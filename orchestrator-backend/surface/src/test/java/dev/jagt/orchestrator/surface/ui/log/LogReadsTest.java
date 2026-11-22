package dev.jagt.orchestrator.surface.ui.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.protocol.debug.RecordTail;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.log.DebugReads;
import dev.jagt.orchestrator.service.role.RoleLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        new LogReads(context, environment, mock(DebugReads.class), List.of())
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
                    mock(AgentRuntime.class)),
                List.of())
            .read(new RecordTail("backend-log", 2));

    assertThat(read).isEqualTo("b\nc");
  }

  @Test
  void returnsTheTailOfTheFileTheRoleNamesForItsRecord(@TempDir Path root) throws IOException {
    Path file = root.resolve("role.log");
    Files.writeString(file, "a\nb\nc\n");
    RoleLog other = mock(RoleLog.class);
    when(other.record()).thenReturn("other-log");
    RoleLog log = mock(RoleLog.class);
    when(log.record()).thenReturn("role-log");
    when(log.fileKey()).thenReturn("logging.role.name");
    MockEnvironment environment =
        new MockEnvironment().withProperty("logging.role.name", file.toString());
    DebugReads reads = mock(DebugReads.class);
    when(reads.tail(file, 2)).thenReturn("b\nc");

    String read =
        new LogReads(new LoggerContext(), environment, reads, List.of(other, log))
            .read(new RecordTail("role-log", 2));

    assertThat(read).isEqualTo("b\nc");
  }

  @Test
  void readsTheStopRecordForStops() {
    DebugReads reads = mock(DebugReads.class);
    when(reads.stops(3)).thenReturn("stopped");

    String read =
        new LogReads(new LoggerContext(), new MockEnvironment(), reads, List.of())
            .read(new RecordTail("stops", 3));

    assertThat(read).isEqualTo("stopped");
  }

  @Test
  void readsTheReviewedStopRecordForStopsReviewed() {
    DebugReads reads = mock(DebugReads.class);
    when(reads.stopsReviewed(3)).thenReturn("reviewed");

    String read =
        new LogReads(new LoggerContext(), new MockEnvironment(), reads, List.of())
            .read(new RecordTail("stops-reviewed", 3));

    assertThat(read).isEqualTo("reviewed");
  }
}
