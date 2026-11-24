package dev.jagt.orchestrator;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.home.ShippedFiles;
import dev.jagt.orchestrator.surface.ui.FailurePage;
import dev.jagt.orchestrator.surface.ui.log.LogFileRoll;
import dev.jagt.orchestrator.surface.ui.log.LogTarget;
import dev.jagt.orchestrator.surface.ui.log.PortCheck;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrchestratorApplication {

  public static void main(String[] args) {
    Locale.setDefault(Locale.ENGLISH);
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    OrchestratorPaths.installedRootOutside(args)
        .ifPresent(
            home -> ShippedFiles.placeInto(home, OrchestratorApplication.class.getClassLoader()));
    String[] launch = withConfigFile(args);
    new StartRetry(given -> application().run(given), FailurePage::listen).run(launch);
  }

  /**
   * A launch is the only place that knows where `jagt.yml` is before a context exists. Not the
   * packaged {@code application.yml}: an import written there is loaded by every test context too.
   */
  static String[] withConfigFile(String[] args) {
    String resolved = OrchestratorPaths.configFileOutside(args).toString();
    // Pinned as well as imported: `root` inside that file would otherwise send the bean to a second
    // jagt.yml.
    // Spring joins a repeated argument with a comma, so the human's own is replaced, not repeated.
    return java.util.stream.Stream.concat(
            java.util.Arrays.stream(args)
                .filter(arg -> !arg.startsWith("--orchestrator.config-file=")),
            java.util.stream.Stream.of(
                "--spring.config.additional-location=optional:file:" + resolved,
                "--orchestrator.config-file=" + resolved,
                "--orchestrator.default-data=" + OrchestratorPaths.dataOutside(args)))
        .toArray(String[]::new);
  }

  // Visible for a test: the REGISTRATION is what breaks while the listener itself works.
  static SpringApplication application() {
    SpringApplication application = new SpringApplication(OrchestratorApplication.class);
    application.addListeners(
        new PortCheck(System::exit),
        new LogTarget(),
        new LogFileRoll(RuntimeWiring.utcClock(), System.err));
    return application;
  }
}
