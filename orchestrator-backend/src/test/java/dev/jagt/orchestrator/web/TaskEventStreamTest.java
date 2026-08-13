package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.service.StateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
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

    /**
     * An open board tab is an async request with no timeout, and Tomcat's stop waits for those: leave them
     * open and Ctrl-C never ends the process. A completed emitter refuses further sends — that is the proof.
     */
    @Test
    void endsEveryBoardConnectionWhenTheBackendShutsDown() {
        TaskEventStream stream = new TaskEventStream(mock(StateService.class));
        SseEmitter browser = stream.open();

        stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

        assertThatIllegalStateException().isThrownBy(() -> browser.send("late"));
    }

    /**
     * The servlet keeps answering until the web server actually stops, and the board's {@code EventSource}
     * reconnects as soon as its stream ends — so a tab can ask for a new one mid-shutdown. Handing it a live
     * endless stream would restore the hang the sweep just cleared.
     */
    @Test
    void handsBackAnAlreadyEndedStreamToATabThatReconnectsDuringShutdown() {
        TaskEventStream stream = new TaskEventStream(mock(StateService.class));
        stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

        assertThatIllegalStateException().isThrownBy(() -> stream.open().send("reconnected"));
    }

    /**
     * A write that fails mid-session (the laptop slept, the VPN dropped) leaves the async request registered
     * with the container — and the shutdown sweep can only end connections it still knows about. Forgetting
     * one without ending it is therefore a ^C that hangs again.
     */
    @Test
    void endsAConnectionWhoseWriteFailedInsteadOfMerelyForgettingIt() {
        TaskEventStream stream = new TaskEventStream(mock(StateService.class));
        AtomicBoolean ended = new AtomicBoolean();
        SseEmitter gone = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
                throw new IOException("socket gone");
            }

            @Override
            public void complete() {
                ended.set(true);
            }
        };

        stream.send(gone, "changed");

        assertThat(ended).isTrue();
    }
}
