package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.HumanWait;
import dev.jagt.orchestrator.task.HumanWait.Why;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class HumanWaitRecorderTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path root;

  private final ConfigService config = mock(ConfigService.class);
  private final AnnotationConfigApplicationContext context =
      new AnnotationConfigApplicationContext();
  private final TaskState holding =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
          .report(AgentReport.QUESTION)
          .waits(List.of(new HumanWait(1_000, 0, Why.QUESTION)))
          .build();
  private final TaskState left =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
          .waits(List.of(new HumanWait(1_000, 0, Why.QUESTION)))
          .build();
  private OrchestratorPaths paths;
  private StateService state;
  private Clock clock = Clock.fixed(Instant.ofEpochMilli(9_000), ZoneOffset.UTC);

  @BeforeEach
  void registersTheRecorderOverTheTempState() {
    paths =
        new OrchestratorPaths(
            OrchestratorProperties.defaults()
                .withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    state = new StateService(new JsonMapper(), paths, reducer, context::publishEvent);
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    context.registerBean(
        HumanWaitRecorder.class,
        () ->
            new HumanWaitRecorder(
                state,
                new Rounds(
                    config,
                    ignored -> false,
                    () -> false,
                    () -> false,
                    new Checkers(new TaskChart(Composition.builtIn()))),
                clock,
                new Checkers(new TaskChart(Composition.builtIn()))));
  }

  @ResourceLock(Resources.GLOBAL)
  @Test
  void recordsTheIntervalQuestionHoldsTheHumanAndLogsItOnceTheSessionIsAnswered() {
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    ((Logger) LoggerFactory.getLogger(HumanWaitRecorder.class)).addAppender(log);
    clock = Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC);
    context.refresh();
    state.putTask("ABC-77", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

    state.updateTask("ABC-77", task -> task.withReport(AgentReport.QUESTION));
    state.updateTask("ABC-77", task -> task.withReport(AgentReport.PLAIN));

    assertThat(state.task("ABC-77").orElseThrow().waits())
        .containsExactly(new HumanWait(5_000, 5_000, Why.QUESTION));
    assertThat(List.copyOf(log.list))
        .filteredOn(
            event ->
                "human wait closed".equals(event.getMessage())
                    && event.getKeyValuePairs().contains(new KeyValuePair("task", "ABC-77")))
        .singleElement()
        .extracting(ILoggingEvent::getKeyValuePairs)
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .contains(
            new KeyValuePair("task", "ABC-77"),
            new KeyValuePair("why", Why.QUESTION),
            new KeyValuePair("millis", 0L));
  }

  @Test
  void keepsAnIntervalOpenAcrossRestartWhileTheHumanStillHoldsTheTurn() {
    new StateService(new JsonMapper(), paths, reducer, event -> {})
        .putTask(
            "ABC-42",
            holding);
    state = new StateService(new JsonMapper(), paths, reducer, context::publishEvent);
    context.refresh();

    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());

    assertThat(state.task("ABC-42").orElseThrow().waits())
        .containsExactly(new HumanWait(1_000, 0, Why.QUESTION));
  }

  @Test
  void closesAnIntervalAfterRestartOnceTheTurnHasLeftTheHuman() {
    new StateService(new JsonMapper(), paths, reducer, event -> {})
        .putTask(
            "ABC-42",
            left);
    state = new StateService(new JsonMapper(), paths, reducer, context::publishEvent);
    context.refresh();

    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());

    assertThat(state.task("ABC-42").orElseThrow().waits())
        .containsExactly(new HumanWait(1_000, 9_000, Why.QUESTION));
  }
}
