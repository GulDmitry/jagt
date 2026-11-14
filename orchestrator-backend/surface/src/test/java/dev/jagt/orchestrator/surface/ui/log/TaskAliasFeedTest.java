package dev.jagt.orchestrator.surface.ui.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class TaskAliasFeedTest {

  @Test
  void taskStoredBeforeStartLogsWithItsAlias() {
    StateService stateService = mock(StateService.class);
    when(stateService.tasks())
        .thenReturn(
            Map.of(
                "ABC-44",
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("p5").build()));
    var event =
        new LoggingEvent(
            "x", new LoggerContext().getLogger("x"), Level.INFO, "master presses", null, null);
    event.addKeyValuePair(new KeyValuePair("task", "ABC-44"));
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean(TaskAliasFeed.class, () -> new TaskAliasFeed(stateService));
    context.refresh();

    context.publishEvent(
        new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
    new TaskAliasLogFilter().decide(event);

    assertThat(event.getKeyValuePairs())
        .extracting(pair -> pair.key + "=" + pair.value)
        .contains("alias=p5");
  }

  @Test
  void taskStoredAfterStartLogsWithItsAlias() {
    var event =
        new LoggingEvent(
            "x", new LoggerContext().getLogger("x"), Level.INFO, "master presses", null, null);
    event.addKeyValuePair(new KeyValuePair("task", "ABC-45"));
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean(TaskAliasFeed.class, () -> new TaskAliasFeed(mock(StateService.class)));
    context.refresh();

    context.publishEvent(
        new StateWritten(
            Map.of(),
            Map.of(
                "ABC-45",
                TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("p6").build())));
    new TaskAliasLogFilter().decide(event);

    assertThat(event.getKeyValuePairs())
        .extracting(pair -> pair.key + "=" + pair.value)
        .contains("alias=p6");
  }
}
