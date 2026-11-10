package dev.jagt.orchestrator.adapter.assistant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.service.UsageTracker;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.mockito.ArgumentCaptor;

final class HeadlessClaudeFixtures {

  private HeadlessClaudeFixtures() {}

  static ProcessRunner answering(Processes.Result first, Processes.Result... then) {
    ProcessRunner runner = mock(ProcessRunner.class);
    when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(first, then);
    return runner;
  }

  static ProcessRunner answeringOutput(String output) {
    return answering(new Processes.Result(0, output, ""));
  }

  static ProcessRunner answeringNoFacts() {
    return answeringOutput("{\"structured_output\":{\"exists\":false}}");
  }

  static HeadlessClaude claude(ProcessRunner runner) {
    return claude(runner, AssistantProperties.empty());
  }

  static HeadlessClaude claude(ProcessRunner runner, AssistantProperties properties) {
    return claude(
        runner,
        AssistantProperties.empty(),
        properties,
        mock(UsageTracker.class),
        mock(ReadOnlyTools.class));
  }

  static HeadlessClaude claude(ProcessRunner runner, ReadOnlyTools reads) {
    return claude(
        runner,
        AssistantProperties.empty(),
        AssistantProperties.empty(),
        mock(UsageTracker.class),
        reads);
  }

  static HeadlessClaude claude(ProcessRunner runner, UsageTracker usage) {
    return claude(
        runner,
        AssistantProperties.empty(),
        AssistantProperties.empty(),
        usage,
        mock(ReadOnlyTools.class));
  }

  static HeadlessClaude claude(ProcessRunner runner, UsageTracker usage, ReadOnlyTools reads) {
    return claude(runner, AssistantProperties.empty(), AssistantProperties.empty(), usage, reads);
  }

  static HeadlessClaude claude(
      ProcessRunner runner,
      AssistantProperties breaker,
      AssistantProperties properties,
      UsageTracker usage,
      ReadOnlyTools reads) {
    return new HeadlessClaude(
        new AssistantProcess(
            runner, new AssistantBreaker(breaker, Clock.systemUTC(), new ReachLedger())),
        ClaudeProperties.defaults(),
        properties,
        usage,
        reads);
  }

  static List<String> commandOf(ProcessRunner runner) {
    ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
    verify(runner).run(any(Path.class), any(Duration.class), command.capture());
    return command.getValue();
  }

  static ListAppender<ILoggingEvent> attached(Logger logger) {
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    logger.addAppender(log);
    return log;
  }

  static List<String> loggedFields(ListAppender<ILoggingEvent> log, String message) {
    return List.copyOf(log.list).stream()
        .filter(event -> message.equals(event.getMessage()))
        .flatMap(event -> event.getKeyValuePairs().stream())
        .map(pair -> pair.key + "=" + pair.value)
        .toList();
  }
}
