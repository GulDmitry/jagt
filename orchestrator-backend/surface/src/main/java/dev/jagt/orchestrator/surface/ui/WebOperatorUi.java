package dev.jagt.orchestrator.surface.ui;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Nothing to start: the embedded server is already up by the time a UI is asked to run, so this only announces
 * where to look and keeps out of the terminal's way.
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
        // STDOUT, not just the log: logging goes to a file so the console stays clean for the TUI, which
        // otherwise leaves a human staring at a terminal with no banner, no address and no sign of life.
        System.out.println("jagt board → http://localhost:" + port
                + "   (plain text: /status /stats · console UI: --orchestrator.ui=tui · Ctrl-C stops)");
        log.info("Board serving on http://localhost:{}", port);
    }

    @Override
    public String name() {
        return "web board";
    }
}
