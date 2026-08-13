package dev.jagt.orchestrator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Stops the backend, whoever asked — the console's {@code quit} and the board's Stop button are the same act
 * and must not grow two implementations. Agents are unaffected: they live in tmux, which is exactly why
 * detaching the orchestrator is a safe thing to offer at all.
 */
@Service
public class BackendShutdown {

    private static final Logger log = LoggerFactory.getLogger(BackendShutdown.class);

    private final ConfigurableApplicationContext context;

    public BackendShutdown(ConfigurableApplicationContext context) {
        this.context = context;
    }

    /**
     * Closes the context from a SIDE thread after a short delay, so an HTTP caller still gets its answer —
     * closing it inline would tear the web server down mid-response and the human would see a dead socket
     * instead of "stopping". The console calls {@link #stopNow()} instead: it has already printed.
     */
    public void stopAfterResponding() {
        Thread stopper = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stopNow();
        }, "jagt-shutdown");
        stopper.setDaemon(false);
        stopper.start();
    }

    public void stopNow() {
        log.info("Stopping the backend on request — agents keep running in tmux.");
        context.close();
    }
}
