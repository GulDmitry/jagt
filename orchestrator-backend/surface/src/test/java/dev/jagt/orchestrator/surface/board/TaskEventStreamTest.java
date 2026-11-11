package dev.jagt.orchestrator.surface.board;

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

import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.port.StateEvents.StateWritten;
import dev.jagt.orchestrator.service.ReachLedger;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskViews;
import jakarta.servlet.AsyncEvent;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class TaskEventStreamTest {

  private final TaskScheduler scheduler = mock(TaskScheduler.class);
  private final ExecutorService broadcaster = mock(ExecutorService.class);
  private final ArgumentCaptor<Runnable> queued = ArgumentCaptor.captor();
  private final ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.captor();
  private final TaskEventStream stream =
      new TaskEventStream(Clock.systemUTC(), scheduler, broadcaster);
  private final TaskEventStream spiedStream = spy(stream);
  private final TaskEventStream beatingStream =
      spy(new TaskEventStream(Clock.systemUTC(), scheduler, Executors.newSingleThreadExecutor()));

  @Test
  void handsTheBroadcastOffInsteadOfWritingOnTheThreadThatChangedTheState() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean(TaskEventStream.class, () -> stream);
    context.refresh();
    stream.open();

    context.publishEvent(new StateWritten(Map.of(), Map.of()));

    verify(broadcaster).execute(any());
  }

  @Test
  void keepsOnlyOnePendingBroadcastForBurstOfChanges() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean(TaskEventStream.class, () -> stream);
    context.refresh();

    context.publishEvent(new StateWritten(Map.of(), Map.of()));
    context.publishEvent(new StateWritten(Map.of(), Map.of()));
    context.publishEvent(new StateWritten(Map.of(), Map.of()));

    verify(broadcaster).execute(queued.capture());
    queued.getValue().run();
    context.publishEvent(new StateWritten(Map.of(), Map.of()));
    verify(broadcaster, times(2)).execute(any());
  }

  @Test
  void handsEachBrowserItsOwnStream() {
    assertThat(stream.open()).isNotSameAs(stream.open());
  }

  @Test
  void endsEveryBoardConnectionWhenTheBackendShutsDown() {
    SseEmitter browser = stream.open();

    stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

    assertThatIllegalStateException().isThrownBy(() -> browser.send("late"));
  }

  @Test
  void handsBackAnAlreadyEndedStreamToTabThatReconnectsDuringShutdown() {
    stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

    assertThatIllegalStateException().isThrownBy(() -> stream.open().send("reconnected"));
  }

  @Test
  void endsConnectionWhoseWriteFailedInsteadOfMerelyForgettingIt() {
    AtomicBoolean ended = new AtomicBoolean();
    SseEmitter gone =
        new SseEmitter(0L) {
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
  void namesTheRunAndBeatsOnceToBrowserThatOpens() {

    SseEmitter browser = spiedStream.open();

    verify(spiedStream).send(eq(browser), eq("open"), anyString());
    verify(spiedStream).send(browser, "beat", "20000");
  }

  @Test
  void tellsEveryOpenBrowserThatTheStateChanged() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean(TaskEventStream.class, () -> spiedStream);
    context.refresh();
    context.publishEvent(new StateWritten(Map.of(), Map.of()));
    verify(broadcaster).execute(queued.capture());
    SseEmitter browser = spiedStream.open();

    queued.getValue().run();

    verify(spiedStream).send(browser, "changed", "changed");
  }

  @Test
  void beatsEveryOpenBrowserWhenTheHeartbeatFires() {
    SseEmitter browser = beatingStream.open();
    verify(scheduler)
        .scheduleAtFixedRate(heartbeat.capture(), any(Instant.class), any(Duration.class));

    heartbeat.getValue().run();

    verify(beatingStream, times(2)).send(browser, "beat", "20000");
  }

  @Test
  void queuesTheNextBroadcastWhenTheBackendRefusedTheLastOne() {
    doThrow(new RejectedExecutionException("stopping"))
        .doNothing()
        .when(broadcaster)
        .execute(any());
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean(TaskEventStream.class, () -> stream);
    context.refresh();

    context.publishEvent(new StateWritten(Map.of(), Map.of()));
    context.publishEvent(new StateWritten(Map.of(), Map.of()));

    verify(broadcaster, times(2)).execute(any());
  }

  @Test
  void stopsTheHeartbeatWhenTheBackendShutsDown() {
    ScheduledFuture<?> beating = mock(ScheduledFuture.class);
    doReturn(beating)
        .when(scheduler)
        .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
    beatingStream.open();

    beatingStream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

    verify(beating).cancel(false);
  }

  @Test
  void stopsTheBroadcasterWithoutInterruptingWriteWhenTheBackendShutsDown() {

    stream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

    verify(broadcaster).shutdown();
    verify(broadcaster, never()).shutdownNow();
  }

  @Test
  void beatsNoBrowserItEndedAtShutdown() {
    final SseEmitter browser = beatingStream.open();
    verify(scheduler)
        .scheduleAtFixedRate(heartbeat.capture(), any(Instant.class), any(Duration.class));
    beatingStream.onApplicationEvent(new ContextClosedEvent(new StaticApplicationContext()));

    heartbeat.getValue().run();

    verify(beatingStream, times(1)).send(browser, "beat", "20000");
  }

  static Stream<Arguments> connectionEndings() {
    return Stream.of(
        Arguments.of("completed", (ThrowingConsumer<MockAsyncContext>) MockAsyncContext::complete),
        Arguments.of(
            "timed out",
            (ThrowingConsumer<MockAsyncContext>)
                connection ->
                    connection.getListeners().getFirst().onTimeout(new AsyncEvent(connection))),
        Arguments.of(
            "failed",
            (ThrowingConsumer<MockAsyncContext>)
                connection ->
                    connection
                        .getListeners()
                        .getFirst()
                        .onError(new AsyncEvent(connection, new IOException("reset")))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("connectionEndings")
  void beatsNoBrowserWhoseConnectionEnded(String ending, ThrowingConsumer<MockAsyncContext> ends)
      throws Throwable {
    MvcResult opened =
        MockMvcBuilders.standaloneSetup(
                new BoardApiController(
                    mock(TaskViews.class),
                    beatingStream,
                    new GlobalCommands(List.of()),
                    new HeaderState(
                        new Jobs(
                            List.of(),
                            mock(TaskScheduler.class),
                            Clock.systemUTC(),
                            mock(StateService.class)),
                        new ReachLedger(),
                        Clock.systemUTC())))
            .build()
            .perform(MockMvcRequestBuilders.get("/api/events").accept("text/event-stream"))
            .andReturn();
    MockAsyncContext connection =
        (MockAsyncContext) Objects.requireNonNull(opened.getRequest().getAsyncContext());

    ends.accept(connection);
    verify(scheduler)
        .scheduleAtFixedRate(heartbeat.capture(), any(Instant.class), any(Duration.class));
    heartbeat.getValue().run();

    verify(beatingStream, times(1)).send(any(), eq("beat"), eq("20000"));
  }

  @Test
  void beatsEveryOpenBoardOnTheConfiguredInterval() {
    TaskEventStream stream =
        new TaskEventStream(
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            scheduler,
            Executors.newSingleThreadExecutor());

    stream.open();

    verify(scheduler)
        .scheduleAtFixedRate(
            any(Runnable.class), eq(Instant.ofEpochSecond(20)), eq(Duration.ofSeconds(20)));
  }
}
