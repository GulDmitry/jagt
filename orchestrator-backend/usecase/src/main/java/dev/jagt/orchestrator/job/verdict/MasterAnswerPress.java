package dev.jagt.orchestrator.job.verdict;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.job.master.MasterShip;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * An answer that is a move the Master holds is that move, pressed, never its name relayed as words.
 */
@Service
@RequiredArgsConstructor
public class MasterAnswerPress {

  /** {@code WORDS}: nothing here to press, the answer is relayed. */
  public enum Result {
    PRESSED,
    REFUSED,
    WORDS
  }

  private final MasterShip ship;

  /** The tree and the unspent drafts as they stand; empty where git could not answer. */
  public Optional<String> standing(TaskState task) {
    return ship.standing(task);
  }

  /**
   * {@code move} is words where it names no move the Master holds, or where the tree or the drafts
   * are no longer what {@code read} saw.
   */
  public Result press(
      String taskId,
      TaskState task,
      String move,
      Optional<String> read,
      ConfigFile.ArbiterConfig config) {
    Optional<TaskAction> held = config.profile().holds(move, task);
    if (held.isEmpty() || read.isEmpty() || !read.equals(standing(task))) {
      return Result.WORDS;
    }
    return ship.press(taskId, task, held.get()) ? Result.PRESSED : Result.REFUSED;
  }
}
