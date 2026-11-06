package dev.jagt.orchestrator.surface.ui;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.KeyValuePair;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskAliasFeedTest {

    @Test
    void aTaskStoredBeforeStartLogsWithItsAlias() {
        StateService stateService = mock(StateService.class);
        when(stateService.tasks()).thenReturn(
                Map.of("ABC-44", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("p5").build()));
        var event = new LoggingEvent("x", new LoggerContext().getLogger("x"), Level.INFO, "master presses", null, null);
        event.addKeyValuePair(new KeyValuePair("task", "ABC-44"));

        new TaskAliasFeed(stateService).follow();
        new TaskAliasLogFilter().decide(event);

        assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key + "=" + pair.value).contains("alias=p5");
    }

    @Test
    void aTaskStoredAfterStartLogsWithItsAlias() {
        StateService stateService = mock(StateService.class);
        ArgumentCaptor<Consumer<StateService.StateFile>> listener = ArgumentCaptor.captor();
        var event = new LoggingEvent("x", new LoggerContext().getLogger("x"), Level.INFO, "master presses", null, null);
        event.addKeyValuePair(new KeyValuePair("task", "ABC-45"));

        new TaskAliasFeed(stateService).follow();
        verify(stateService).onChange(listener.capture());
        listener.getValue().accept(new StateService.StateFile(
                Map.of("ABC-45", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("p6").build())));
        new TaskAliasLogFilter().decide(event);

        assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key + "=" + pair.value).contains("alias=p6");
    }
}
