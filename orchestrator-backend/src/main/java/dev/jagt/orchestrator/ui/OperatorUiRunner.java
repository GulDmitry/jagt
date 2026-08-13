package dev.jagt.orchestrator.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Starts whichever operator surfaces are configured. The ONE application entry point, so "which UI runs" is a
 * config answer in a single place rather than an {@code ApplicationRunner} per front-end racing each other.
 *
 * <p>Non-blocking surfaces first: with {@code orchestrator.ui=both} the board must be announced and serving
 * BEFORE the TUI takes over the terminal and blocks for the rest of the session.
 */
@Component
@Order(Integer.MAX_VALUE)
public class OperatorUiRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OperatorUiRunner.class);

    private final List<OperatorUi> surfaces;

    public OperatorUiRunner(List<OperatorUi> surfaces) {
        this.surfaces = surfaces;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (surfaces.isEmpty()) {
            // Reachable only by asking for a UI that does not exist; say so instead of starting a headless
            // backend the human cannot see or drive.
            log.warn("No operator UI is enabled — check orchestrator.ui (web | tui | both)."
                    + " The HTTP endpoints are still up.");
            return;
        }
        surfaces.stream()
                .sorted(Comparator.comparing(OperatorUi::blocking))
                .forEach(surface -> {
                    log.info("Operator UI: {}", surface.name());
                    surface.start();
                });
    }
}
