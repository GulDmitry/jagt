package dev.jagt.orchestrator;

import dev.jagt.orchestrator.config.OrchestratorPaths;
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
            application().run(withConfigFile(args));
        } catch (RuntimeException | Error failure) {
            System.err.println(StartupFailure.describe(failure));
            throw failure;
        }
    }

    /**
     * Hands Spring the SAME `jagt.yml` that {@code ConfigService} re-reads, resolved the same way — a launch is
     * the only place that knows where it is before a context exists. Declared here rather than in the packaged
     * `application.yml` on purpose: an import written there is loaded by every test context too, and a suite
     * that reads the developer's own settings passes or fails on an untracked file.
     */
    private static String[] withConfigFile(String[] args) {
        String resolved = OrchestratorPaths.configFileOutside(args).toString();
        String[] launched = java.util.Arrays.copyOf(args, args.length + 2);
        launched[args.length] = "--spring.config.additional-location=optional:file:" + resolved;
        // Pinned as well as imported: `root` inside that file re-answers where the root is, and without this
        // the bean would then look for a SECOND jagt.yml under the new root while Spring had bound the first.
        launched[args.length + 1] = "--orchestrator.config-file=" + resolved;
        return launched;
    }

    /**
     * Visible for a test, because the REGISTRATION is what breaks while {@link SessionLog} itself works: a
     * {@code main} simplified back to a bare {@code SpringApplication.run} would report yesterday's entries as
     * this session's work with the whole suite green.
     */
    static SpringApplication application() {
        SpringApplication application = new SpringApplication(OrchestratorApplication.class);
        application.addListeners(new SessionLog());
        return application;
    }
}
