package dev.jagt.orchestrator;

import dev.jagt.orchestrator.ui.ConsoleLogging;
import dev.jagt.orchestrator.ui.StartupFailure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class OrchestratorApplication {

    // NOTE: there is deliberately NO logback "failure-render preload" here. A NoClassDefFoundError during a
    // startup failure / on exit is a CORRUPTED FAT JAR, not a missing preload — `./gradlew build` rewrites
    // the jar in place while a JVM runs from it. See the GOTCHA in CLAUDE.md (Build & run). Do not re-add it.
    public static void main(String[] args) {
        try {
            application().run(args);
        } catch (RuntimeException | Error failure) {
            // Boot logs the failure and then marks it handled, so the JVM prints nothing either: the human
            // got a bare shell prompt back and no reason. See StartupFailure.
            System.err.println(StartupFailure.describe(failure));
            throw failure;
        }
    }

    /**
     * Visible for a test: {@link ConsoleLogging} has to be registered here, by hand, and the last time this
     * broke it was the REGISTRATION that was wrong while the listener itself worked — a `main` simplified
     * back to a bare {@code SpringApplication.run} would paint Spring's log lines over the TUI again with the
     * whole suite green.
     */
    static SpringApplication application() {
        SpringApplication application = new SpringApplication(OrchestratorApplication.class);
        application.addListeners(new ConsoleLogging());
        return application;
    }
}
