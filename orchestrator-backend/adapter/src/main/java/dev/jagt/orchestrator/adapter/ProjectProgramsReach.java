package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.ConfigService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A project's verify or setup program the image lacks is jagt's own reach failing, never a card's.
 */
@Component
@RequiredArgsConstructor
public class ProjectProgramsReach implements Job {

  private final ConfigService configService;
  private final Reach reach;

  @Override
  public String id() {
    return "project-programs-reach";
  }

  @Override
  public String describe() {
    return "reads whether each project's verifyCommand and setupCommand program is installed";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(1);
  }

  @Override
  public void run() {
    configService
        .load()
        .projects()
        .forEach((name, project) -> {
          read("verifyCommand", name, project.verifyCommand());
          read("setupCommand", name, project.setupCommand());
        });
  }

  private void read(String driver, String project, List<String> command) {
    String program = missing(command);
    if (program == null) {
      reach.reached(driver, project);
      return;
    }
    reach.lost(
        new ReachLost(
            driver,
            project,
            List.of(),
            "project '"
                + project
                + "' runs '"
                + program
                + "', which is not on PATH nor in the usual install directories. Extend the image"
                + " (docs/server.md)."),
        () -> program.equals(missing(command)));
  }

  private static @Nullable String missing(List<String> command) {
    if (command.isEmpty() || command.getFirst().isBlank()) {
      return null;
    }
    String program = command.getFirst().strip();
    return !program.contains("/") && Executables.unresolved(Executables.resolve(program))
        ? program
        : null;
  }
}
