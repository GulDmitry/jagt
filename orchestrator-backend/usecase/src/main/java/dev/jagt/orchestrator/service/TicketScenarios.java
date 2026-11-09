package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.RoleOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketScenarios {

  private final RoleSettings acceptanceSettings;

  /** Blank unless an enabled acceptance holds a workspace with a scenarios file for the task. */
  public String briefingLine(String taskId) {
    RoleOptions acceptance = acceptanceSettings.load();
    if (!acceptance.enabled()) {
      return "";
    }
    return acceptance
        .workspacePath()
        .map(workspace -> workspace.resolve("scenarios").resolve(taskId + ".md").normalize())
        .filter(Files::isRegularFile)
        .map(TicketScenarios::line)
        .orElse("");
  }

  private static String line(Path file) {
    return "\n\n<scenarios>\nAcceptance wrote this ticket's scenarios: "
        + file
        + ". Copy its open rows into `plan.md`'s `## Acceptance criteria`.\n</scenarios>";
  }
}
