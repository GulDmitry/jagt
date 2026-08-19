package dev.jagt.orchestrator.surface.ui;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * The default surface: a local web board at {@code localhost:<port>}, served by the same jar (static assets +
 * the {@code /api} endpoints). Nothing to start — the embedded server is already up by the time a UI is asked
 * to run, so this only announces where to look and keeps out of the terminal's way.
 *
 * <p>Announcing matters more than it looks: with no console UI the process otherwise prints Spring's banner
 * and goes silent, and the human has no idea the board exists.
 */
@Component
@ConditionalOnExpression("'${orchestrator.ui:web}'.matches('web|both')")
@Slf4j
public class WebOperatorUi implements OperatorUi {

    private final String port;

    public WebOperatorUi(@Value("${server.port:8290}") String port) {
        this.port = port;
    }

    @Override
    public void start() {
        // STDOUT, not just the log: logging is configured to a file so the console stays clean for the TUI,
        // and with the board as the default surface that left a human staring at an empty terminal with no
        // banner, no address and no sign of life. This is the one thing that has to reach the console.
        System.out.println("jagt board → http://localhost:" + port
                + "   (plain text: /status /stats · console UI: --orchestrator.ui=tui · Ctrl-C stops)");
        log.info("Board serving on http://localhost:{}", port);
    }

    @Override
    public String name() {
        return "web board";
    }
}
