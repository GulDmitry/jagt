package dev.jagt.orchestrator;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.surface.ui.Console;
import dev.jagt.orchestrator.surface.ui.FailureListener;
import dev.jagt.orchestrator.surface.ui.ServedFailure;
import dev.jagt.orchestrator.surface.ui.StartupFailure;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/** Runs the application; a refused start is served on a page until the config is saved. */
final class StartRetry {

  private final Consumer<String[]> application;
  private final FailureListener failurePages;

  StartRetry(Consumer<String[]> application, FailureListener failurePages) {
    this.application = application;
    this.failurePages = failurePages;
  }

  void run(String[] launch) {
    while (true) {
      try {
        application.accept(launch);
        return;
      } catch (RuntimeException failure) {
        String problems = StartupFailure.describe(failure);
        Console.problem(problems);
        if (!servedUntilSaved(launch, problems)) {
          throw failure;
        }
      } catch (Error failure) {
        // A NoClassDefFoundError here means the jar was rewritten under a running JVM.
        Console.problem(StartupFailure.describe(failure));
        throw failure;
      }
    }
  }

  private boolean servedUntilSaved(String[] launch, String problems) {
    Optional<? extends ServedFailure> page =
        failurePages.listen(
            boardPort(launch), OrchestratorPaths.configFileOutside(launch), problems);
    if (page.isEmpty()) {
      return false;
    }
    try (ServedFailure open = page.get()) {
      Console.say("start refused: fix jagt.yml at http://localhost:" + open.port());
      open.awaitSave();
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static int boardPort(String[] launch) {
    return Arrays.stream(launch)
        .filter(arg -> arg.startsWith("--server.port="))
        .findFirst()
        .map(arg -> Integer.parseInt(arg.substring("--server.port=".length()).trim()))
        .orElse(8290);
  }
}
