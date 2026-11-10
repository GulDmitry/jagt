package dev.jagt.orchestrator.adapter.tmux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.TerminalDriver;
import java.time.Duration;
import java.util.List;
import org.mockito.ArgumentCaptor;

final class TmuxFixtures {

  private TmuxFixtures() {}

  static Processes.Result ok(String out) {
    return new Processes.Result(0, out, "");
  }

  static Processes.Result failed(String err) {
    return new Processes.Result(1, "", err);
  }

  static Processes everyCommandAnswering(Processes.Result result) {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(result);
    return processes;
  }

  static void answer(Processes processes, String part, Processes.Result result) {
    when(processes.run(isNull(), any(Duration.class), containing(part))).thenReturn(result);
  }

  static List<String> containing(String... parts) {
    return argThat((List<String> command) -> command.containsAll(List.of(parts)));
  }

  static TmuxReads reads(Processes processes) {
    return new TmuxReads(processes, ToolchainProperties.defaults());
  }

  static TmuxSessionHost host(Processes processes) {
    return host(processes, mock(TerminalDriver.class), mock(AgentRuntime.class));
  }

  static TmuxSessionHost host(Processes processes, TmuxReads reads) {
    return new TmuxSessionHost(
        processes,
        reads,
        new OrchestratorPaths(OrchestratorProperties.defaults()),
        mock(TerminalDriver.class),
        mock(AgentRuntime.class));
  }

  static TmuxSessionHost host(Processes processes, TerminalDriver terminal) {
    return host(processes, terminal, mock(AgentRuntime.class));
  }

  static TmuxSessionHost host(Processes processes, AgentRuntime agent) {
    return host(processes, mock(TerminalDriver.class), agent);
  }

  static TmuxSessionHost host(
      Processes processes, TerminalDriver terminal, ToolchainProperties properties) {
    return new TmuxSessionHost(
        processes,
        new TmuxReads(processes, properties),
        new OrchestratorPaths(OrchestratorProperties.defaults()),
        terminal,
        mock(AgentRuntime.class));
  }

  static TmuxSessionHost host(Processes processes, TerminalDriver terminal, AgentRuntime agent) {
    return new TmuxSessionHost(
        processes,
        reads(processes),
        new OrchestratorPaths(OrchestratorProperties.defaults()),
        terminal,
        agent);
  }

  static List<List<String>> issued(Processes processes) {
    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    return commands.getAllValues();
  }

  static ListAppender<ILoggingEvent> attached(Logger logger) {
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    logger.addAppender(log);
    return log;
  }
}
