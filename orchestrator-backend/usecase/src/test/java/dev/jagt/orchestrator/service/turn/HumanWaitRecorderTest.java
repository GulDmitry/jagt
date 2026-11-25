package dev.jagt.orchestrator.service.turn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.review.ReachOffers;
import dev.jagt.orchestrator.service.review.Rounds;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.HumanWait;
import dev.jagt.orchestrator.task.turn.HumanWait.Why;
import dev.jagt.orchestrator.testing.FakeHost;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
                .withStateFile(root.resolve("state.json").toString()),
            FakeHost.machine());
    state = new StateService(new JsonMapper(), paths, reducer, context::publishEvent);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults().withArbiter(ConfigFile.ArbiterConfig.defaults().withMode("off")));
    context.registerBean(
        HumanWaitRecorder.class,
        () ->
            new HumanWaitRecorder(
                state,
                new Rounds(
                    config,
                    ignored -> false,
                    new ReachOffers(() -> false, () -> false),
                    unheld -> null,
                    new Checkers(new TaskChart(Composition.builtIn()))),
                clock,
                new Checkers(new TaskChart(Composition.builtIn()))));
  }

  @Test
  void recordsTheIntervalQuestionHoldsTheHumanAndLogsItOnceTheSessionIsAnswered() {
    try (FakeLogCapture log = new FakeLogCapture(HumanWaitRecorder.class)) {
      clock = Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC);
      context.refresh();
      state.putTask("ABC-77", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());

      state.updateTask("ABC-77", task -> task.withReport(AgentReport.QUESTION));
      state.updateTask("ABC-77", task -> task.withReport(AgentReport.PLAIN));

      assertThat(state.task("ABC-77").orElseThrow().work().waits())
          .containsExactly(new HumanWait(5_000, 5_000, Why.QUESTION));
      assertThat(log.events())
          .filteredOn(event ->
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
  }

  @Test
  void keepsAnIntervalOpenAcrossRestartWhileTheHumanStillHoldsTheTurn() {
    new StateService(new JsonMapper(), paths, reducer, event -> {}).putTask("ABC-42", holding);
    state = new StateService(new JsonMapper(), paths, reducer, context::publishEvent);
    context.refresh();

    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());

    assertThat(state.task("ABC-42").orElseThrow().work().waits())
        .containsExactly(new HumanWait(1_000, 0, Why.QUESTION));
  }

  @Test
  void closesAnIntervalAfterRestartOnceTheTurnHasLeftTheHuman() {
    new StateService(new JsonMapper(), paths, reducer, event -> {}).putTask("ABC-42", left);
    state = new StateService(new JsonMapper(), paths, reducer, context::publishEvent);
    context.refresh();

    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());

    var waits = state.task("ABC-42").orElseThrow().work().waits();

    assertThat(waits).containsExactly(new HumanWait(1_000, 9_000, Why.QUESTION));
  }
}
