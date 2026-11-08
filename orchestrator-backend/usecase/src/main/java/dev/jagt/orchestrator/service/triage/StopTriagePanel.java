package dev.jagt.orchestrator.service.triage;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.master.ChargedReviews;
import dev.jagt.orchestrator.task.StopTriage;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StopTriagePanel {

  private static final Pattern ISSUE =
      Pattern.compile("(session|master|case|jagt|none):\\s*(\\S.*)", Pattern.CASE_INSENSITIVE);
  private static final Pattern FAMILY = Pattern.compile("[A-M]");

  private final ChargedReviews reviews;
  private final OrchestratorPaths paths;

  /** What the stop's reader is shown beside the stop itself. */
  public record Trail(String excerpt, String commits) {

    private static final int STEPS = 12;

    /** Its last steps and rounds as the task recorded them; the reader reads the commits. */
    public static Trail of(TaskState task) {
      List<String> lines = new ArrayList<>();
      task.history().stream()
          .skip(Math.max(0, task.history().size() - STEPS))
          .forEach(
              step ->
                  lines.add(
                      Instant.ofEpochMilli(step.at())
                          + " "
                          + step.status()
                          + (step.origin() == null ? "" : " by " + step.origin())
                          + (step.why() == null ? "" : ": " + step.why())));
      task.trailOrNone()
          .rounds()
          .forEach(
              round ->
                  lines.add(
                      Instant.ofEpochMilli(round.at())
                          + " round, threads "
                          + round.threads()
                          + (round.failing().isBlank() ? "" : ", failing " + round.failing())));
      String worktrees =
          task.repos().stream().map(TaskRepo::worktreePath).filter(Objects::nonNull).toList()
              + ", base "
              + task.baseBranchOr("the base branch");
      return new Trail(
          String.join("\n", lines),
          "Read them with read-only git in plain words: the commits ahead of the base, in "
              + worktrees);
    }
  }

  /** Throws {@link StopTriageFailed} naming why nothing usable came back. */
  public StopTriage read(String taskId, TaskState task, Trail trail, String model) {
    TaskStop stop = task.stop();
    String brief = new RoleFiles(paths, "triage").text();
    if (stop == null) {
      throw new StopTriageFailed("the task is not stopped");
    }
    if (brief.isBlank()) {
      throw new StopTriageFailed("the triage brief is unreadable");
    }
    List<Path> worktrees = task.repos().stream().map(TaskRepo::worktreePath).map(Path::of).toList();
    Judgement judged =
        reviews.review(
            taskId,
            new RoundReviewer.Round("", prompt(taskId, stop, trail, brief), worktrees, model));
    if (!judged.failure().isBlank()) {
      throw new StopTriageFailed(judged.failure());
    }
    if (judged.findings().isEmpty()) {
      throw new StopTriageFailed("the read named no finding");
    }
    return mapped(judged);
  }

  private static StopTriage mapped(Judgement judged) {
    Finding finding = judged.findings().getFirst();
    Matcher said = ISSUE.matcher(finding.issue().strip().lines().findFirst().orElse("").strip());
    if (!said.matches()) {
      throw new StopTriageFailed("the finding names no who: " + finding.issue());
    }
    String letter = finding.pattern().strip().toUpperCase(Locale.ROOT);
    return new StopTriage(
        StopTriage.Who.valueOf(said.group(1).toUpperCase(Locale.ROOT)),
        said.group(2),
        FAMILY.matcher(letter).matches() ? letter : StopTriage.UNKNOWN_FAMILY,
        judged.verdict().equals("ready") ? StopTriage.Lift.SAFE : StopTriage.Lift.UNSAFE,
        judged.premises().stream()
            .map(RoundReviewer.Premise::claim)
            .reduce((first, second) -> first + "; " + second)
            .orElse(""),
        "");
  }

  static String prompt(String taskId, TaskStop stop, Trail trail, String brief) {
    return "<role>You triage task "
        + taskId
        + ", which jagt stopped for its human. You read and change nothing.</role>\n"
        + "<stop>\ntrigger: "
        + stop.trigger()
        + "\nreason: "
        + stop.trigger().reason()
        + "\nevidence: "
        + stop.evidence()
        + "\nadvice to the human: "
        + stop.trigger().next()
        + "\n</stop>\n"
        + "<trail>\n"
        + trail.excerpt()
        + "\n</trail>\n"
        + "<commits>\n"
        + trail.commits()
        + "\n</commits>\n"
        + "The brief you read by:\n<brief>\n"
        + brief
        + "\n</brief>\n"
        + "Run no build and no test. Respond directly, no preamble.";
  }
}
