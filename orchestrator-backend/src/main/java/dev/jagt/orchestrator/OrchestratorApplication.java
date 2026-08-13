package dev.jagt.orchestrator;

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
            SpringApplication.run(OrchestratorApplication.class, args);
        } catch (RuntimeException | Error failure) {
            // Boot logs the failure and then marks it handled, so the JVM prints nothing either: the human
            // got a bare shell prompt back and no reason. See StartupFailure.
            System.err.println(StartupFailure.describe(failure));
            throw failure;
        }
    }
}
