package dev.jagt.orchestrator;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.surface.ui.Console;
import dev.jagt.orchestrator.surface.ui.FailurePage;
import dev.jagt.orchestrator.surface.ui.StartupFailure;
import dev.jagt.orchestrator.surface.ui.log.LogFileRoll;
import dev.jagt.orchestrator.surface.ui.log.LogTarget;
import dev.jagt.orchestrator.surface.ui.log.PortGuard;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrchestratorApplication {

  // A NoClassDefFoundError while a startup failure renders means the jar was rewritten in place
  // under a
  // running JVM, not a class that needs preloading.
  public static void main(String[] args) {
    Locale.setDefault(Locale.ENGLISH);
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    String[] launch = withConfigFile(args);
    while (true) {
      try {
        application().run(launch);
        return;
      } catch (RuntimeException failure) {
        String problems = StartupFailure.describe(failure);
        Console.problem(problems);
        if (!servedUntilSaved(launch, problems)) {
          throw failure;
        }
      } catch (Error failure) {
        Console.problem(StartupFailure.describe(failure));
        throw failure;
      }
    }
  }

  private static boolean servedUntilSaved(String[] launch, String problems) {
    Optional<FailurePage> page =
        FailurePage.listen(
            boardPort(launch), OrchestratorPaths.configFileOutside(launch), problems);
    if (page.isEmpty()) {
      return false;
    }
    try (FailurePage open = page.get()) {
      Console.say("start refused: fix jagt.yml at http://localhost:" + open.port());
      open.awaitSave();
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static int boardPort(String[] launch) {
    return java.util.Arrays.stream(launch)
        .filter(arg -> arg.startsWith("--server.port="))
        .findFirst()
        .map(arg -> Integer.parseInt(arg.substring("--server.port=".length()).trim()))
        .orElse(8290);
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
                "--orchestrator.config-file=" + resolved))
        .toArray(String[]::new);
  }

  // Visible for a test: the REGISTRATION is what breaks while the listener itself works.
  static SpringApplication application() {
    SpringApplication application = new SpringApplication(OrchestratorApplication.class);
    application.addListeners(
        new PortGuard(), new LogTarget(), new LogFileRoll(RuntimeWiring.utcClock(), System.err));
    return application;
  }
}
