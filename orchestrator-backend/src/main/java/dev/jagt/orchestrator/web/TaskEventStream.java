package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.service.StateService;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pushes "the board changed" to every open browser, so the web UI never polls for state. It subscribes to
 * {@link StateService#onChange} — the same signal a state-driven TUI repaint uses — and forwards it as a
 * Server-Sent Event; the client then re-fetches the projection.
 *
 * <p>Deliberately carries NO payload: the event says "something moved", the client asks for the current board.
 * A payload would be a second serialization of the projection that could disagree with {@code /api/tasks}, and
 * a browser that missed one event would then be silently stale.
 *
 * <p>The change signal arrives on whichever thread performed the write — an agent's tool call, a scheduler
 * tick — and writing to every open socket there makes that caller wait on the slowest browser, or on a
 * half-dead TCP connection. So the fan-out is handed to one thread of its own. Because the event has no
 * payload, several changes queued behind one another are the SAME event: only one is kept, and the client
 * re-fetches once instead of once per change.
 */
@Component
@Slf4j
public class TaskEventStream implements ApplicationListener<ContextClosedEvent> {

    private final StateService stateService;
    private final ExecutorService broadcaster;
    private final AtomicBoolean broadcastQueued = new AtomicBoolean();
    private final List<SseEmitter> browsers = new CopyOnWriteArrayList<>();
    private volatile boolean closing;

    @Autowired
    public TaskEventStream(StateService stateService) {
        this(stateService, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "board-sse");
            thread.setDaemon(true);
            return thread;
        }));
    }

    TaskEventStream(StateService stateService, ExecutorService broadcaster) {
        this.stateService = stateService;
        this.broadcaster = broadcaster;
    }

    @PostConstruct
    void followStateChanges() {
        stateService.onChange(written -> queueBroadcast());
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
            log.debug("No board broadcast, the backend is stopping: {}", e.toString());
        }
    }

    /** A new browser tab. No timeout: the board is meant to stay open all day. */
    public SseEmitter open() {
        SseEmitter browser = new SseEmitter(0L);
        browser.onCompletion(() -> browsers.remove(browser));
        browser.onTimeout(() -> browsers.remove(browser));
        browser.onError(error -> browsers.remove(browser));
        synchronized (this) {
            // A shutdown that has already swept the list must not gain a new endless connection: the servlet
            // still answers until the web server actually stops, and the board's EventSource reconnects the
            // moment we complete it.
            if (closing) {
                browser.complete();
                return browser;
            }
            browsers.add(browser);
        }
        send(browser, "open");
        return browser;
    }

    /**
     * Ctrl-C has to actually stop jagt. Each open board tab is an async request that never completes (no
     * timeout, by design above), and Tomcat's stop WAITS for in-flight async requests — so with one tab open
     * the shutdown hook hung forever and the process survived every ^C. Ending the connections ourselves is
     * the fix, and {@code ContextClosedEvent} is the last moment we still can: it fires before the lifecycle
     * stop that shuts the web server down, unlike {@code @PreDestroy}, which runs after it. This is an
     * {@code ApplicationListener} rather than {@code @EventListener} so the wiring is in the type, not in an
     * annotation a test cannot see.
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        List<SseEmitter> open;
        synchronized (this) {
            closing = true;
            open = List.copyOf(browsers);
            browsers.clear();
        }
        open.forEach(this::end);
        // Not shutdownNow(): interrupting the fan-out mid-write truncates the last update on the wire, and a
        // task drained before it ran would leave the pending flag set, silently killing every later broadcast.
        broadcaster.shutdown();
    }

    private void broadcast() {
        browsers.forEach(browser -> send(browser, "changed"));
    }

    void send(SseEmitter browser, String event) {
        try {
            browser.send(SseEmitter.event().name(event).data(event));
        } catch (IOException | IllegalStateException e) {
            // A closed tab is the normal case, not an error worth shouting about. It has to be ENDED though,
            // not merely forgotten: a failed write leaves the async request registered with the container,
            // and the shutdown sweep can only end what is still in this list.
            browsers.remove(browser);
            end(browser);
            log.debug("Dropping a closed board connection: {}", e.toString());
        }
    }

    private void end(SseEmitter browser) {
        try {
            browser.complete();
        } catch (RuntimeException e) {
            log.debug("A board connection was already gone: {}", e.toString());
        }
    }
}
