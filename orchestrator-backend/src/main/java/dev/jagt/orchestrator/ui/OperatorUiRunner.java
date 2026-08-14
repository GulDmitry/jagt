package dev.jagt.orchestrator.ui;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Slf4j
public class OperatorUiRunner implements ApplicationRunner {

    private final List<OperatorUi> surfaces;

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
