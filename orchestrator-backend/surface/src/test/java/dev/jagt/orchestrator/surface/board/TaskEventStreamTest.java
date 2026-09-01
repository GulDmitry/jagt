package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.StateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TaskEventStreamTest {

    @Test
    void handsTheBroadcastOffInsteadOfWritingOnTheThreadThatChangedTheState() {
        StateService stateService = mock(StateService.class);
        ExecutorService broadcaster = mock(ExecutorService.class);
        TaskEventStream stream = new TaskEventStream(stateService, broadcaster);
        stream.followStateChanges();
        ArgumentCaptor<Consumer<StateService.StateFile>> listener = ArgumentCaptor.captor();
        verify(stateService).onChange(listener.capture());
        stream.open();

        listener.getValue().accept(new StateService.StateFile(null));

        verify(broadcaster).execute(any());
    }

    @Test
    void keepsOnlyOnePendingBroadcastForABurstOfChanges() {
        StateService stateService = mock(StateService.class);
        ExecutorService broadcaster = mock(ExecutorService.class);
        TaskEventStream stream = new TaskEventStream(stateService, broadcaster);
        stream.followStateChanges();
        ArgumentCaptor<Consumer<StateService.StateFile>> listener = ArgumentCaptor.captor();
        verify(stateService).onChange(listener.capture());
        ArgumentCaptor<Runnable> queued = ArgumentCaptor.captor();

        listener.getValue().accept(new StateService.StateFile(null));
        listener.getValue().accept(new StateService.StateFile(null));
        listener.getValue().accept(new StateService.StateFile(null));

        verify(broadcaster).execute(queued.capture());
        queued.getValue().run();
        listener.getValue().accept(new StateService.StateFile(null));
        verify(broadcaster, times(2)).execute(any());
    }

    @Test
    void handsEachBrowserItsOwnStream() {
        TaskEventStream stream = new TaskEventStream(mock(StateService.class), mock(ExecutorService.class));

        assertThat(stream.open()).isNotSameAs(stream.open());
    }

    @Test
    void endsEveryBoardConnectionWhenTheBackendShutsDown() {
        TaskEventStream stream = new TaskEventStream(mock(StateService.class), mock(ExecutorService.class));
        SseEmitter browser = stream.open();

        stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

        assertThatIllegalStateException().isThrownBy(() -> browser.send("late"));
    }

    @Test
    void handsBackAnAlreadyEndedStreamToATabThatReconnectsDuringShutdown() {
        TaskEventStream stream = new TaskEventStream(mock(StateService.class), mock(ExecutorService.class));
        stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

        assertThatIllegalStateException().isThrownBy(() -> stream.open().send("reconnected"));
    }

    @Test
    void endsAConnectionWhoseWriteFailedInsteadOfMerelyForgettingIt() {
        TaskEventStream stream = new TaskEventStream(mock(StateService.class), mock(ExecutorService.class));
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
