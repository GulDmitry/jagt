package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.service.StateService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes "the board changed" to every open browser, so the web UI never polls for state. It subscribes to
 * {@link StateService#onChange} — the same signal a state-driven TUI repaint uses — and forwards it as a
 * Server-Sent Event; the client then re-fetches the projection.
 *
 * <p>Deliberately carries NO payload: the event says "something moved", the client asks for the current board.
 * A payload would be a second serialization of the projection that could disagree with {@code /api/tasks}, and
 * a browser that missed one event would then be silently stale.
 */
@Component
public class TaskEventStream {

    private static final Logger log = LoggerFactory.getLogger(TaskEventStream.class);

    private final StateService stateService;
    private final List<SseEmitter> browsers = new CopyOnWriteArrayList<>();

    public TaskEventStream(StateService stateService) {
        this.stateService = stateService;
    }

    @PostConstruct
    void followStateChanges() {
        stateService.onChange(written -> broadcast());
    }

    /** A new browser tab. No timeout: the board is meant to stay open all day. */
    public SseEmitter open() {
        SseEmitter browser = new SseEmitter(0L);
        browser.onCompletion(() -> browsers.remove(browser));
        browser.onTimeout(() -> browsers.remove(browser));
        browser.onError(error -> browsers.remove(browser));
        browsers.add(browser);
        send(browser, "open");
        return browser;
    }

    private void broadcast() {
        browsers.forEach(browser -> send(browser, "changed"));
    }

    private void send(SseEmitter browser, String event) {
        try {
            browser.send(SseEmitter.event().name(event).data(event));
        } catch (IOException | IllegalStateException e) {
            // A closed tab is the normal case, not an error worth shouting about.
            browsers.remove(browser);
            log.debug("Dropping a closed board connection: {}", e.toString());
        }
    }
}
