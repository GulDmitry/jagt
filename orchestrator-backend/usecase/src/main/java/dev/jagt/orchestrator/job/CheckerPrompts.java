package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.config.RoleOptions;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.service.CheckerReach;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import java.nio.file.Path;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What an acceptance run is told: the brief, then the facts of the deploy it checks. */
@Component
@RequiredArgsConstructor
public class CheckerPrompts {

  private final RoleDefinition role;
  private final RoleFiles brief;

  public CheckerReader.Run accept(
      String taskId, String worktree, String sha, RoleOptions acceptance, String step) {
    String environments =
        acceptance.environments().stream()
            .map(CheckerPrompts::environment)
            .collect(Collectors.joining("\n"));
    String facts =
        deployFact(taskId, sha) + "\nCheck it on:\n" + environments + flowsLine(acceptance);
    return new CheckerReader.Run(
        brief.text(),
        brief.section(step) + "\n\n" + facts,
        Path.of(worktree),
        sha,
        acceptance.model(),
        CheckerReach.of(acceptance),
        role.replies().answer().json(),
        role.id());
  }

  /**
   * The round acceptance sent back and the reason the session gave for changing nothing, read
   * against fresh evidence.
   */
  public CheckerReader.Run decline(
      String taskId,
      String worktree,
      String sha,
      RoleOptions acceptance,
      String rows,
      String reason) {
    String environments =
        acceptance.environments().stream()
            .map(CheckerPrompts::environment)
            .collect(Collectors.joining("\n"));
    String facts =
        deployFact(taskId, sha)
            + "\nThe rows you found not met:\n"
            + rows
            + "\nThe session changed nothing, and says:\n"
            + reason
            + "\nCheck on:\n"
            + environments;
    return new CheckerReader.Run(
        brief.text(),
        brief.section(AcceptanceVerdict.DECLINE) + "\n\n" + facts,
        Path.of(worktree),
        sha,
        acceptance.model(),
        CheckerReach.of(acceptance),
        role.replies().decline().json(),
        role.id());
  }

  private static String flowsLine(RoleOptions acceptance) {
    String path = acceptance.flowsPathOrBlank();
    return path.isEmpty()
        ? ""
        : "\nFlows: "
            + path
            + ". In `flows` name the ids checking a criterion: a test tagged `@AC-n`, else its"
            + " title; in `environment` the listed URL they run on.";
  }

  private static String deployFact(String taskId, String sha) {
    return taskId + "'s deploy was pushed at " + sha + "; whether it landed is yours to prove.";
  }

  private static String environment(RoleOptions.Environment environment) {
    return "- "
        + environment.url()
        + (environment.landed().isBlank()
            ? ""
            : " (the change is up when " + environment.landed() + " answers)");
  }
}
