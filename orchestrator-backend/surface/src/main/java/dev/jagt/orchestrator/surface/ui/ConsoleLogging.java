package dev.jagt.orchestrator.surface.ui;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
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
 * Registered by hand in {@code main} rather than through {@code META-INF/spring/…imports}: {@code bootJar}
 * hoists that file to the jar ROOT, which is NOT on the executable jar's classpath, so an
 * {@code EnvironmentPostProcessor} declared there is silently never loaded (the TUI came up with
 * Spring's log lines painted over it).
 */
public class ConsoleLogging implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final String THRESHOLD = "logging.threshold.console";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        apply(event.getEnvironment());
    }

    void apply(ConfigurableEnvironment environment) {
        String ui = environment.getProperty("orchestrator.ui", "web").trim();
        if ("tui".equalsIgnoreCase(ui) || "both".equalsIgnoreCase(ui)) {
            // The file stays structured whatever the surface: `activity` reads it back, and a human reads
            // that report rather than the file.
            environment.getPropertySources().addLast(new MapPropertySource("jagt-console-logging",
                    Map.of(THRESHOLD, "off")));
        }
    }

    /**
     * Between two of Boot's own listeners: after the one that loads {@code application.yml} (which is where
     * {@code orchestrator.ui} usually comes from) and before the one that initialises logging — after that,
     * setting the threshold changes nothing.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }
}
