package dev.jagt.orchestrator.service.git;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.stop.Guard;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** The git state a human trusted for a task, held against what git would obey now. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrustedGitState {

  private static final int NAMED = 3;
  private static final int MAX_CHARS = 20_000;

  private final StateService stateService;
  private final GitWorktrees git;

  /**
   * Trusts what git obeys now; throws where it cannot be read. True where the task was parked on a
   * change and its session waits on a question.
   */
  public boolean trust(String taskId) {
    TaskState task = stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
    Map<String, String> now = read(task);
    stateService.reduce(
        taskId, (reducer, current) -> reducer.gitTrusted(current, GitTrust.of(now)));
    return task.gitTrust() != null
        && task.gitTrust().changed() != null
        && task.report() == AgentReport.QUESTION;
  }

  /**
   * What git obeys now where it differs from what was trusted, as a diff a human reads before
   * trusting it; kept nowhere. Empty where nothing differs or nothing was recorded.
   */
  public Optional<TaskDiff.RepoDiff> changes(String taskId) {
    TaskState task = stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
    GitTrust trusted = task.gitTrust();
    if (trusted == null) {
      return Optional.empty();
    }
    String text;
    try {
      Map<String, String> changes = trusted.changes(read(task));
      text = changes.isEmpty() ? "" : describe(changes, git.gitContents(worktrees(task)));
    } catch (RuntimeException unreadable) {
      text = "could not be read: " + unreadable.getMessage();
    }
    boolean cut = text.length() > MAX_CHARS;
    return text.isEmpty()
        ? Optional.empty()
        : Optional.of(
            new TaskDiff.RepoDiff(
                "git config and hooks", "trusted", cut ? text.substring(0, MAX_CHARS) : text, cut));
  }

  private static String describe(Map<String, String> changes, Map<String, String> contents) {
    StringBuilder text = new StringBuilder();
    changes.forEach((entry, how) -> {
      text.append("@@ ").append(entry).append(' ').append(how).append('\n');
      if (!how.equals("removed")) {
        contents
            .getOrDefault(entry, "")
            .lines()
            .forEach(line -> text.append('+').append(line).append('\n'));
      }
    });
    return text.toString();
  }

  private Map<String, String> read(TaskState task) {
    return git.gitState(worktrees(task));
  }

  private static List<Path> worktrees(TaskState task) {
    return task.repos().stream()
        .map(TaskRepo::worktreePath)
        .filter(path -> path != null && !path.isBlank())
        .map(Path::of)
        .toList();
  }

  /**
   * Why git must not run for the task yet, empty where it obeys what was trusted. A task with
   * nothing recorded trusts what it finds; a change found stays on the task until {@link #trust}
   * adopts it.
   */
  public Optional<String> distrust(String taskId) {
    TaskState task = stateService.task(taskId).orElse(null);
    if (task == null) {
      return Optional.empty();
    }
    GitTrust trusted = task.gitTrust();
    String parked = trusted == null ? null : trusted.changed();
    if (parked != null) {
      return Optional.of(parked);
    }
    Map<String, String> now;
    try {
      now = read(task);
    } catch (RuntimeException unreadable) {
      return Optional.of(
          "git state of " + taskId + " could not be read: " + unreadable.getMessage());
    }
    if (trusted == null) {
      stateService.reduce(
          taskId, (reducer, current) -> reducer.gitTrusted(current, GitTrust.of(now)));
      return Optional.empty();
    }
    List<String> differences = trusted.differences(now);
    if (differences.isEmpty()) {
      return Optional.empty();
    }
    String changed =
        "git config or hooks changed since trusted: "
            + String.join("; ", differences.subList(0, Math.min(NAMED, differences.size())))
            + (differences.size() > NAMED ? "; " + (differences.size() - NAMED) + " more" : "")
            + " — read them in the Diff tab, trust adopts them";
    stateService.reduce(
        taskId,
        (reducer, current) -> reducer.gitChanged(current, changed).refused(Guard.GIT_STATE));
    log.atWarn()
        .setMessage("git state changed")
        .addKeyValue("task", taskId)
        .addKeyValue("cause", OneLine.clipped(String.join("; ", differences)))
        .addKeyValue("effect", "task parked")
        .log();
    return Optional.of(changed);
  }
}
