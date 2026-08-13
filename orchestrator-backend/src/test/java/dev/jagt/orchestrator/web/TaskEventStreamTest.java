package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.service.StateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskEventStreamTest {

    @Test
    void followsStateChangesAndSurvivesBrowsersThatHaveGoneAway() {
        // The listener runs on whatever thread wrote the state — an agent's MCP call. If a broadcast to a
        // closed tab escaped, that write path would take the exception.
        StateService stateService = mock(StateService.class);
        TaskEventStream stream = new TaskEventStream(stateService);
        stream.followStateChanges();

        ArgumentCaptor<Consumer<StateService.StateFile>> listener = ArgumentCaptor.captor();
        verify(stateService).onChange(listener.capture());
        stream.open();                                  // a tab that is not attached to a real response

        assertThatCode(() -> listener.getValue().accept(new StateService.StateFile(null)))
                .doesNotThrowAnyException();
    }

    @Test
    void handsEachBrowserItsOwnStream() {
        TaskEventStream stream = new TaskEventStream(mock(StateService.class));

        assertThat(stream.open()).isNotSameAs(stream.open());
    }
}
