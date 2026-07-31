package dev.jagt.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class OrchestratorApplication {

    public static void main(String[] args) {
        preloadFailureLoggingClasses();
        SpringApplication.run(OrchestratorApplication.class, args);
    }

    /**
     * Preload the logback class Spring's failure/shutdown logging renders exceptions with. In a fat jar,
     * if {@code ThrowableProxy} loads for the FIRST time during a startup FAILURE or JVM shutdown — when
     * the nested-jar classloader is tearing down or refuses new loads — the load throws NoClassDefFoundError,
     * which then MASKS the real error (e.g. "Port 8290 was already in use") behind a confusing logback
     * stack trace. Loading it now, while the classloader is healthy, caches it so the real cause surfaces.
     */
    private static void preloadFailureLoggingClasses() {
        try {
            Class.forName("ch.qos.logback.classic.spi.ThrowableProxy");
        } catch (ClassNotFoundException ignored) {
            // logback not on the classpath (e.g. a slim build) — nothing to preload.
        }
    }
}
