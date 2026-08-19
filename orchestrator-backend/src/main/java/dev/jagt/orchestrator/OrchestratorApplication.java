package dev.jagt.orchestrator;

import dev.jagt.orchestrator.surface.ui.ConsoleLogging;
import dev.jagt.orchestrator.surface.ui.SessionLog;
import dev.jagt.orchestrator.surface.ui.StartupFailure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class OrchestratorApplication {

    // There is deliberately NO logback "failure-render preload" here: a NoClassDefFoundError during a startup
    // failure or on exit is a CORRUPTED FAT JAR — `./gradlew build` rewrites it in place while a JVM runs from
    // it. Do not add one back.
    public static void main(String[] args) {
        try {
            application().run(args);
        } catch (RuntimeException | Error failure) {
            System.err.println(StartupFailure.describe(failure));
            throw failure;
        }
    }

    /**
     * Visible for a test, because the REGISTRATION is what breaks while {@link ConsoleLogging} itself works: a
     * {@code main} simplified back to a bare {@code SpringApplication.run} would paint Spring's log lines over
     * the TUI with the whole suite green.
     */
    static SpringApplication application() {
        SpringApplication application = new SpringApplication(OrchestratorApplication.class);
        application.addListeners(new ConsoleLogging(), new SessionLog());
        return application;
    }
}
