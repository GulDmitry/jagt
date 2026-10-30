package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.StateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.service.TaskViews;
import jakarta.servlet.AsyncEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TaskEventStreamTest {

    @Test
    void handsTheBroadcastOffInsteadOfWritingOnTheThreadThatChangedTheState() {
        StateService stateService = mock(StateService.class);
        ExecutorService broadcaster = mock(ExecutorService.class);
        TaskEventStream stream = new TaskEventStream(stateService, Clock.systemUTC(), mock(TaskScheduler.class), broadcaster);
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
        TaskEventStream stream = new TaskEventStream(stateService, Clock.systemUTC(), mock(TaskScheduler.class), broadcaster);
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
        TaskEventStream stream = new TaskEventStream(mock(StateService.class), Clock.systemUTC(), mock(TaskScheduler.class), mock(ExecutorService.class));

        assertThat(stream.open()).isNotSameAs(stream.open());
    }

    @Test
    void endsEveryBoardConnectionWhenTheBackendShutsDown() {
        TaskEventStream stream = new TaskEventStream(mock(StateService.class), Clock.systemUTC(), mock(TaskScheduler.class), mock(ExecutorService.class));
        SseEmitter browser = stream.open();

        stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

        assertThatIllegalStateException().isThrownBy(() -> browser.send("late"));
    }

    @Test
    void handsBackAnAlreadyEndedStreamToATabThatReconnectsDuringShutdown() {
        TaskEventStream stream = new TaskEventStream(mock(StateService.class), Clock.systemUTC(), mock(TaskScheduler.class), mock(ExecutorService.class));
        stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

        assertThatIllegalStateException().isThrownBy(() -> stream.open().send("reconnected"));
    }

    @Test
    void endsAConnectionWhoseWriteFailedInsteadOfMerelyForgettingIt() {
        TaskEventStream stream = new TaskEventStream(mock(StateService.class), Clock.systemUTC(), mock(TaskScheduler.class), mock(ExecutorService.class));
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

        stream.send(gone, "changed", "changed");

        assertThat(ended).isTrue();
    }

    @Test
    void namesTheRunAndBeatsOnceToABrowserThatOpens() {
        TaskEventStream stream = spy(new TaskEventStream(mock(StateService.class), Clock.systemUTC(),
                mock(TaskScheduler.class), mock(ExecutorService.class)));

        SseEmitter browser = stream.open();

        verify(stream).send(eq(browser), eq("open"), anyString());
        verify(stream).send(browser, "beat", "20000");
    }

    @Test
    void tellsEveryOpenBrowserThatTheStateChanged() {
        StateService stateService = mock(StateService.class);
        ExecutorService broadcaster = mock(ExecutorService.class);
        TaskEventStream stream = spy(new TaskEventStream(stateService, Clock.systemUTC(), mock(TaskScheduler.class), broadcaster));
        stream.followStateChanges();
        ArgumentCaptor<Consumer<StateService.StateFile>> listener = ArgumentCaptor.captor();
        verify(stateService).onChange(listener.capture());
        SseEmitter browser = stream.open();
        listener.getValue().accept(new StateService.StateFile(null));
        ArgumentCaptor<Runnable> queued = ArgumentCaptor.captor();
        verify(broadcaster).execute(queued.capture());

        queued.getValue().run();

        verify(stream).send(browser, "changed", "changed");
    }

    @Test
    void beatsEveryOpenBrowserWhenTheHeartbeatFires() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskEventStream stream = spy(new TaskEventStream(mock(StateService.class), Clock.systemUTC(), scheduler, Executors.newSingleThreadExecutor()));
        stream.followStateChanges();
        SseEmitter browser = stream.open();
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.captor();
        verify(scheduler).scheduleAtFixedRate(heartbeat.capture(), any(Instant.class), any(Duration.class));

        heartbeat.getValue().run();

        verify(stream, times(2)).send(browser, "beat", "20000");
    }

    @Test
    void queuesTheNextBroadcastWhenTheBackendRefusedTheLastOne() {
        StateService stateService = mock(StateService.class);
        ExecutorService broadcaster = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException("stopping")).doNothing().when(broadcaster).execute(any());
        TaskEventStream stream = new TaskEventStream(stateService, Clock.systemUTC(), mock(TaskScheduler.class), broadcaster);
        stream.followStateChanges();
        ArgumentCaptor<Consumer<StateService.StateFile>> listener = ArgumentCaptor.captor();
        verify(stateService).onChange(listener.capture());

        listener.getValue().accept(new StateService.StateFile(null));
        listener.getValue().accept(new StateService.StateFile(null));

        verify(broadcaster, times(2)).execute(any());
    }

    @Test
    void stopsTheHeartbeatWhenTheBackendShutsDown() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> beating = mock(ScheduledFuture.class);
        doReturn(beating).when(scheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
        TaskEventStream stream = new TaskEventStream(mock(StateService.class), Clock.systemUTC(), scheduler, Executors.newSingleThreadExecutor());
        stream.followStateChanges();

        stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

        verify(beating).cancel(false);
    }

    @Test
    void stopsTheBroadcasterWithoutInterruptingAWriteWhenTheBackendShutsDown() {
        ExecutorService broadcaster = mock(ExecutorService.class);
        TaskEventStream stream = new TaskEventStream(mock(StateService.class), Clock.systemUTC(), mock(TaskScheduler.class), broadcaster);

        stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

        verify(broadcaster).shutdown();
        verify(broadcaster, never()).shutdownNow();
    }

    @Test
    void beatsNoBrowserItEndedAtShutdown() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskEventStream stream = spy(new TaskEventStream(mock(StateService.class), Clock.systemUTC(), scheduler, Executors.newSingleThreadExecutor()));
        stream.followStateChanges();
        SseEmitter browser = stream.open();
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.captor();
        verify(scheduler).scheduleAtFixedRate(heartbeat.capture(), any(Instant.class), any(Duration.class));
        stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

        heartbeat.getValue().run();

        verify(stream, times(1)).send(browser, "beat", "20000");
    }

    @Test
    void beatsNoBrowserWhoseConnectionCompleted() throws Exception {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskEventStream stream = spy(new TaskEventStream(mock(StateService.class), Clock.systemUTC(), scheduler, Executors.newSingleThreadExecutor()));
        stream.followStateChanges();
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.captor();
        verify(scheduler).scheduleAtFixedRate(heartbeat.capture(), any(Instant.class), any(Duration.class));
        MvcResult opened = MockMvcBuilders.standaloneSetup(new BoardApiController(mock(TaskViews.class), stream,
                new GlobalCommands(List.of()), new HeaderState(new Jobs(List.of(), mock(TaskScheduler.class), Clock.systemUTC(), org.mockito.Mockito.mock(dev.jagt.orchestrator.service.StateService.class)), org.mockito.Mockito.mock(dev.jagt.orchestrator.port.AssistantHealth.class), Clock.systemUTC()))).build()
                .perform(MockMvcRequestBuilders.get("/api/events").accept("text/event-stream")).andReturn();
        MockAsyncContext connection = (MockAsyncContext) opened.getRequest().getAsyncContext();

        connection.complete();
        heartbeat.getValue().run();

        verify(stream, times(1)).send(any(), eq("beat"), eq("20000"));
    }

    @Test
    void beatsNoBrowserWhoseConnectionTimedOut() throws Exception {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskEventStream stream = spy(new TaskEventStream(mock(StateService.class), Clock.systemUTC(), scheduler, Executors.newSingleThreadExecutor()));
        stream.followStateChanges();
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.captor();
        verify(scheduler).scheduleAtFixedRate(heartbeat.capture(), any(Instant.class), any(Duration.class));
        MvcResult opened = MockMvcBuilders.standaloneSetup(new BoardApiController(mock(TaskViews.class), stream,
                new GlobalCommands(List.of()), new HeaderState(new Jobs(List.of(), mock(TaskScheduler.class), Clock.systemUTC(), org.mockito.Mockito.mock(dev.jagt.orchestrator.service.StateService.class)), org.mockito.Mockito.mock(dev.jagt.orchestrator.port.AssistantHealth.class), Clock.systemUTC()))).build()
                .perform(MockMvcRequestBuilders.get("/api/events").accept("text/event-stream")).andReturn();
        MockAsyncContext connection = (MockAsyncContext) opened.getRequest().getAsyncContext();

        connection.getListeners().getFirst().onTimeout(new AsyncEvent(connection));
        heartbeat.getValue().run();

        verify(stream, times(1)).send(any(), eq("beat"), eq("20000"));
    }

    @Test
    void beatsNoBrowserWhoseConnectionFailed() throws Exception {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskEventStream stream = spy(new TaskEventStream(mock(StateService.class), Clock.systemUTC(), scheduler, Executors.newSingleThreadExecutor()));
        stream.followStateChanges();
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.captor();
        verify(scheduler).scheduleAtFixedRate(heartbeat.capture(), any(Instant.class), any(Duration.class));
        MvcResult opened = MockMvcBuilders.standaloneSetup(new BoardApiController(mock(TaskViews.class), stream,
                new GlobalCommands(List.of()), new HeaderState(new Jobs(List.of(), mock(TaskScheduler.class), Clock.systemUTC(), org.mockito.Mockito.mock(dev.jagt.orchestrator.service.StateService.class)), org.mockito.Mockito.mock(dev.jagt.orchestrator.port.AssistantHealth.class), Clock.systemUTC()))).build()
                .perform(MockMvcRequestBuilders.get("/api/events").accept("text/event-stream")).andReturn();
        MockAsyncContext connection = (MockAsyncContext) opened.getRequest().getAsyncContext();

        connection.getListeners().getFirst().onError(new AsyncEvent(connection, new IOException("reset")));
        heartbeat.getValue().run();

        verify(stream, times(1)).send(any(), eq("beat"), eq("20000"));
    }

    @Test
    void beatsEveryOpenBoardOnTheConfiguredInterval() {
        StateService stateService = mock(StateService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskEventStream stream = new TaskEventStream(stateService, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), scheduler, Executors.newSingleThreadExecutor());

        stream.followStateChanges();

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(Instant.ofEpochSecond(20)), eq(Duration.ofSeconds(20)));
    }
}
