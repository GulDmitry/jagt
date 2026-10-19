package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.StateService;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** No payload, so changes queued behind one another are one event. Fan-out never runs on the writing thread. */
@Component
@Slf4j
public class TaskEventStream implements ApplicationListener<ContextClosedEvent> {

    private static final Duration HEARTBEAT = Duration.ofSeconds(20);

    private final StateService stateService;
    private final ScheduledExecutorService broadcaster;
    private final AtomicBoolean broadcastQueued = new AtomicBoolean();
    private final List<SseEmitter> browsers = new CopyOnWriteArrayList<>();
    private volatile boolean closing;
    private final String started = Long.toString(System.currentTimeMillis(), 36);

    public TaskEventStream(StateService stateService,
                           @Qualifier("boardBroadcaster") ScheduledExecutorService broadcaster) {
        this.stateService = stateService;
        this.broadcaster = broadcaster;
    }

    @PostConstruct
    void followStateChanges() {
        stateService.onChange(written -> queueBroadcast());
        broadcaster.scheduleAtFixedRate(() -> browsers.forEach(this::beat), HEARTBEAT.toMillis(),
                HEARTBEAT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void queueBroadcast() {
        if (!broadcastQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            broadcaster.execute(() -> {
                broadcastQueued.set(false);
                broadcast();
            });
        } catch (RejectedExecutionException e) {
            broadcastQueued.set(false);
            log.atDebug().setMessage("board broadcast skipped")
                    .addKeyValue("cause", "backend stopping")
                    .addKeyValue("note", e.toString())
                    .log();
        }
    }

    /** No timeout: the board stays open all day. The first event names this run, so a page older than it can tell. */
    public SseEmitter open() {
        SseEmitter browser = new SseEmitter(0L);
        browser.onCompletion(() -> browsers.remove(browser));
        browser.onTimeout(() -> browsers.remove(browser));
        browser.onError(error -> browsers.remove(browser));
        synchronized (this) {
            // The servlet still answers after the shutdown sweep, and nothing would ever end a connection
            // added now; the board's EventSource reconnects.
            if (closing) {
                browser.complete();
                return browser;
            }
            browsers.add(browser);
        }
        send(browser, "open", started);
        beat(browser);
        return browser;
    }

    /** Tomcat's stop waits on in-flight async requests, so an open tab hangs it. {@code @PreDestroy} is too late. */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        List<SseEmitter> open;
        synchronized (this) {
            closing = true;
            open = List.copyOf(browsers);
            browsers.clear();
        }
        open.forEach(this::end);
        // Not shutdownNow(): an interrupted write truncates the last update, and a drained task leaves the
        // queued flag set.
        broadcaster.shutdown();
    }

    // The event carries no payload: a second serialization of the projection could disagree with
    // /api/tasks, and a browser that missed one event would then be silently stale.
    private void broadcast() {
        browsers.forEach(browser -> send(browser, "changed", "changed"));
    }

    /** Carries its own interval, so the page knows how long a silence may last without a setting of its own. */
    private void beat(SseEmitter browser) {
        send(browser, "beat", String.valueOf(HEARTBEAT.toMillis()));
    }

    void send(SseEmitter browser, String event, String data) {
        try {
            browser.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            // A failed write leaves the async request registered with the container, so the connection is
            // ended rather than only forgotten.
            browsers.remove(browser);
            end(browser);
            log.atDebug().setMessage("board connection dropped")
                    .addKeyValue("cause", e.toString())
                    .log();
        }
    }

    private void end(SseEmitter browser) {
        try {
            browser.complete();
        } catch (RuntimeException e) {
            log.atDebug().setMessage("board connection already gone")
                    .addKeyValue("cause", e.toString())
                    .log();
        }
    }
}
