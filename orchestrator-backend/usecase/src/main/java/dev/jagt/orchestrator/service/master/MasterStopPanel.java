package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The Master's decision on a stop jagt set for a loop it suspects: lift it, return the task to its
 * session, or leave it.
 */
@Service
@RequiredArgsConstructor
public class MasterStopPanel {

  private final ChargedReviews reviews;
  private final MasterBriefs briefs;

  /** Empty where nothing usable came back, which leaves the stop to the human. */
  public Optional<MasterStopAnswer> ask(
      String taskId, TaskState task, ConfigService.ConfigFile.MasterConfig config) {
    TaskStop stop = task.stop();
    Optional<String> brief = briefs.master(taskId, config);
    if (stop == null || brief.isEmpty()) {
      return Optional.empty();
    }
    List<Path> worktrees = task.repos().stream().map(TaskRepo::worktreePath).map(Path::of).toList();
    Judgement judged =
        reviews.review(
            taskId,
            new RoundReviewer.Round(
                "", prompt(taskId, task, stop, brief.get()), worktrees, config.modelOrInherited()));
    return Optional.of(judged)
        .filter(said -> said.failure().isBlank() && !said.findings().isEmpty())
        .map(said -> MasterStopAnswer.of(said.findings().getFirst().issue()));
  }

  static String prompt(String taskId, TaskState task, TaskStop stop, String brief) {
    return MasterPanel.GOAL
        + "<role>You stand in for the human on task "
        + taskId
        + ", which jagt stopped because it"
        + " suspects a loop.</role>\n"
        + "<stop>\ntrigger: "
        + stop.trigger().reason()
        + "\nevidence: "
        + stop.evidence()
        + "\nadvice to the human: "
        + stop.trigger().next()
        + "\n</stop>\n"
        + "The brief you judge by:\n<brief>\n"
        + brief
        + "\n</brief>\n"
        + "The task: "
        + taskId
        + ", worktrees "
        + task.repos().stream().map(TaskRepo::worktreePath).toList()
        + ", base "
        + task.baseBranchOr("the base branch")
        + ". Run no build and no test.\n"
        + "<rules>\n"
        + "1. Read what the stop's evidence points at in the worktrees, with read-only git in plain"
        + " words.\n"
        + "2. Decide what the human would, only on what you can prove from what you read.\n"
        + "3. Lift where the loop is not one: the work moved, or the stop was set by a count the"
        + " work outgrew.\n"
        + "4. Return where the session must change its approach: say what it does differently.\n"
        + "5. Leave it to the human where only they can settle it: a right, a choice of theirs, a"
        + " fact you cannot read.\n"
        + "</rules>\n"
        + "<output_format>\n"
        + "- verdict: ready.\n"
        + "- findings: exactly one, its issue one line that starts with `lift: <the reason>`,"
        + " `return: <the instruction to the session>` or `human: <why>`.\n"
        + "- premises: what it rests on, each with provenBy.\n"
        + "- failure: blank unless something stopped you reading, then what.\n"
        + "</output_format>\n"
        + "Respond directly, no preamble.";
  }
}
