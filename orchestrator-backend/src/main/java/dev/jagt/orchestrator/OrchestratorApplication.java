package dev.jagt.orchestrator;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.surface.ui.LogFileReset;
import dev.jagt.orchestrator.surface.ui.StartupFailure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class OrchestratorApplication {

    // A NoClassDefFoundError while a startup failure renders means the jar was rewritten in place under a
    // running JVM, not a class that needs preloading.
    public static void main(String[] args) {
        try {
            application().run(withConfigFile(args));
        } catch (RuntimeException | Error failure) {
            System.err.println(StartupFailure.describe(failure));
            throw failure;
        }
    }

    /**
     * A launch is the only place that knows where `jagt.yml` is before a context exists. Not the packaged
     * {@code application.yml}: an import written there is loaded by every test context too.
     */
    private static String[] withConfigFile(String[] args) {
        String resolved = OrchestratorPaths.configFileOutside(args).toString();
        String[] launched = java.util.Arrays.copyOf(args, args.length + 2);
        launched[args.length] = "--spring.config.additional-location=optional:file:" + resolved;
        // Pinned as well as imported: `root` inside that file would otherwise send the bean to a second jagt.yml.
        launched[args.length + 1] = "--orchestrator.config-file=" + resolved;
        return launched;
    }

    // Visible for a test: the REGISTRATION is what breaks while the listener itself works.
    static SpringApplication application() {
        SpringApplication application = new SpringApplication(OrchestratorApplication.class);
        application.addListeners(new LogFileReset());
        return application;
    }
}
