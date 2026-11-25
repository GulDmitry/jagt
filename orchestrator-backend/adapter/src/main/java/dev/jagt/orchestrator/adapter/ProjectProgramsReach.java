package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.adapter.flows.FlowsRunner;
import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.adapter.worktree.CredentialChecks;
import dev.jagt.orchestrator.config.role.RoleOptions;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.RoleKits;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A verify, setup or flows program the image lacks, or a project credential that fails its check,
 * is jagt's own reach failing.
 */
@Component
@RequiredArgsConstructor
public class ProjectProgramsReach implements Job {

  private final ConfigService configService;
  private final RoleKits kits;
  private final Reach reach;
  private final CredentialChecks credentials;

  @Override
  public String id() {
    return "project-programs-reach";
  }

  @Override
  public String describe() {
    return "reads whether each verify, setup and flows program is installed, and each credential"
        + " check passes";
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
    credentials.runDue();
    kits.all().forEach(kit -> flows(kit.settings().role().id(), kit.settings().load()));
  }

  private void flows(String role, RoleOptions options) {
    List<String> command =
        options.enabled() && FlowsRunner.PLAYWRIGHT.equals(options.flows().driver())
            ? options.flows().command()
            : List.of();
    read("flows.command", role, command, "role '" + role + "'");
  }

  private void read(String driver, String project, List<String> command) {
    read(driver, project, command, "project '" + project + "'");
  }

  private void read(String driver, String project, List<String> command, String owner) {
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
            owner
                + " runs '"
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
