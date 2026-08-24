package dev.jagt.orchestrator.surface.ui;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * The ONE application entry point, so "which UI runs" is a config answer in a single place rather than an
 * {@code ApplicationRunner} per front-end racing each other.
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
            // Reachable only by asking for a UI that does not exist.
            log.atWarn().setMessage("no operator ui enabled")
                    .addKeyValue("fix", "orchestrator.ui (web|tui|both)")
                    .addKeyValue("http", "up")
                    .log();
            return;
        }
        surfaces.stream()
                .sorted(Comparator.comparing(OperatorUi::blocking))
                .forEach(surface -> {
                    log.atInfo().setMessage("operator ui started")
                            .addKeyValue("surface", surface.name())
                            .log();
                    surface.start();
                });
    }
}
