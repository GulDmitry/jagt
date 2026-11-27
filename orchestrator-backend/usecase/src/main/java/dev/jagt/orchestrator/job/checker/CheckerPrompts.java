package dev.jagt.orchestrator.job.checker;

import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.config.role.RoleOptions;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.service.assistant.TicketTexts;
import dev.jagt.orchestrator.service.reach.CheckerReach;
import dev.jagt.orchestrator.task.TaskState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/** What an acceptance run is told: the brief, then the facts of the deploy it checks. */
@RequiredArgsConstructor
public class CheckerPrompts {

  private static final Duration REREAD = Duration.ofMinutes(10);

  private final RoleDefinition role;
  private final RoleFiles brief;
  private final TicketTexts tickets;
  private final Clock clock;

  /**
   * The input a round cannot be given: the deploy's worktree, or the ticket text of a task that has
   * a ticket. A ticket not yet kept is read here at most once per re-read window, each read being
   * paid.
   */
  public Optional<String> missingInput(String taskId, TaskState task) {
    if (task.worktreePath() == null) {
      return Optional.of("deploy");
    }
    String url = task.ticket().ticketUrl();
    if (url == null || url.isBlank() || tickets.of(taskId).isPresent()) {
      return Optional.empty();
    }
    long now = clock.millis();
    if (tickets.readSince(taskId, now - REREAD.toMillis())) {
      return Optional.of("ticket");
    }
    tickets.read(taskId, url, now);
    return tickets.of(taskId).isPresent() ? Optional.empty() : Optional.of("ticket");
  }

  public CheckerReader.Run accept(
      String taskId, String worktree, String sha, RoleOptions acceptance, String step) {
    String environments =
        acceptance.environments().stream()
            .map(CheckerPrompts::environment)
            .collect(Collectors.joining("\n"));
    String facts =
        deployFact(taskId, sha)
            + "\nCheck it on:\n"
            + environments
            + flowsLine(acceptance)
            + workspaceLine(acceptance, worktree, sha)
            + ticketLine(taskId);
    return new CheckerReader.Run(
        acceptance.layout().fill(brief.text()),
        acceptance.layout().fill(brief.section(step)) + "\n\n" + facts,
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
        acceptance.layout().fill(brief.text()),
        acceptance.layout().fill(brief.section(role.checking().declineStep())) + "\n\n" + facts,
        Path.of(worktree),
        sha,
        acceptance.model(),
        CheckerReach.of(acceptance),
        role.replies().decline().json(),
        role.id());
  }

  private String ticketLine(String taskId) {
    return tickets
        .of(taskId)
        .map(text -> "\nThe ticket, as jagt read it at intake:\n<ticket>\n" + text + "\n</ticket>")
        .orElse("");
  }

  private static String flowsLine(RoleOptions acceptance) {
    String path = acceptance.flowsPathOrBlank();
    return path.isEmpty()
        ? ""
        : "\nFlows: "
            + path
            + ". In `flows` give each flow's `id`, a test's `@AC-n` tag else its title, never its"
            + " file, and its `criterion`, written exactly as in the `criterion` of the row it"
            + " checks; in"
            + " `environment` the listed URL they run on."
            + " List and open its files with Glob and Read; the shell runs no `ls`."
            + (acceptance.workspacePath().isEmpty()
                ? " jagt runs each flow you name on that URL after your answer: a passed flow"
                    + " makes its criterion's row met, a failed one not met; you run none."
                : "");
  }

  private static String workspaceLine(RoleOptions acceptance, String worktree, String sha) {
    return acceptance
        .workspacePath()
        .map(root ->
            "\nWorkspace: "
                + root
                + ", your working directory. The change's repository is "
                + worktree
                + ": read git there with `git -C "
                + worktree
                + " show --no-ext-diff --no-textconv "
                + sha
                + "`, never in the workspace."
                + (acceptance.flows().command().isEmpty()
                    ? " No flows command is set."
                    : " Run a flow as `BASE_URL=<environment> "
                        + String.join(" ", acceptance.flows().command())
                        + " <file>`.")
                + " In `flows` name each flow you wrote or ran with its `id` and the"
                + " `criterion` of the row it checks, in `environment` the URL.")
        .orElse("");
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
