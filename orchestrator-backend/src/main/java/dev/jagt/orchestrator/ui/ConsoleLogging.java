package dev.jagt.orchestrator.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Who owns the terminal decides whether logs may be printed to it. The TUI paints a full-screen Lanterna
 * buffer, so a log line landing there corrupts the display — that surface gets a silent console and a log
 * FILE. The web board leaves the terminal untouched, and a server whose terminal says nothing at all is
 * indistinguishable from one that died: with the board, the logs belong on the console.
 *
 * <p>Lowest precedence on purpose ({@code addLast}), so {@code --logging.threshold.console=...} still wins.
 * This runs as an {@link EnvironmentPostProcessor} because logging is initialised right after the environment
 * is prepared — a {@code @Bean} would decide this long after the first log line was already dropped.
 */
public class ConsoleLogging implements EnvironmentPostProcessor {

    private static final String THRESHOLD = "logging.threshold.console";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (ownsTheTerminal(environment.getProperty("orchestrator.ui", "web"))) {
            environment.getPropertySources()
                    .addLast(new MapPropertySource("jagt-console-logging", Map.of(THRESHOLD, "off")));
        }
    }

    private boolean ownsTheTerminal(String ui) {
        return "tui".equalsIgnoreCase(ui.trim()) || "both".equalsIgnoreCase(ui.trim());
    }
}
