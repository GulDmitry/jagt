package dev.jagt.orchestrator.service.turn;

import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NotesDue;
import dev.jagt.orchestrator.service.role.RoleTurnEndNote;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.session.Session;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * A turn end, refused where the move is still the agent's, or the tree changed, and nothing was
 * reported since the turn began, or where the text holds an open question no report asked. One
 * refusal per turn: a turn the refusal itself sent on always ends.
 */
@Service
@RequiredArgsConstructor
public class TurnEndVerdicts {

  private static final Pattern OPEN_QUESTION =
      Pattern.compile("OPEN QUESTIONS:?+[ \\t]*+(?!none\\b)\\S", Pattern.CASE_INSENSITIVE);

  private final ConfigService configService;
  private final NotesDue notes;
  private final TurnTree turnTree;
  private final List<RoleTurnEndNote> roleNotes;

  public TurnEndVerdict of(String taskId, @Nullable TaskState task, TurnEnding ending) {
    if (ending.pausedOnBackgroundWork() || task == null) {
      return withTheRole(task);
    }
    boolean treeChanged = turnTree.changedSinceLastEnd(taskId, task);
    if (ending.sentOn()) {
      return withTheRole(task);
    }
    if (leavesQuestionUnreported(task, ending.text())) {
      return new TurnEndVerdict.Refuse(
          "Your turn is ending with OPEN QUESTIONS in its text, and "
              + taskId
              + " holds no question. Call update_agent_status with outcome=question and put the"
              + " question in its message, or leave it out of your text if you settled it.");
    }
    Session session = task.work().session();
    boolean unreported =
        session.workStartedAt() != 0 && session.reportedAt() < session.workStartedAt();
    if (unreported
        && !Move.endsUnreported(task.standing().status())
        && Move.endsOnTheTree(task.standing().status())
        && treeChanged) {
      return new TurnEndVerdict.Refuse(
          "Your turn is ending with "
              + taskId
              + " at "
              + task.standing().status()
              + " on the board and the tree changed without a report. Call update_agent_status"
              + " first: IN_PROGRESS to hand the change back as a round, or revert the change.");
    }
    if (!unreported || !Move.endsUnreported(task.standing().status())) {
      return notes
          .reminder(task, configService.load().agent().notesEveryOrDefault())
          .<TurnEndVerdict>map(TurnEndVerdict.Refuse::new)
          .orElseGet(() -> withTheRole(task));
    }
    return new TurnEndVerdict.Refuse(
        "Your turn is ending with "
            + taskId
            + " at "
            + task.standing().status()
            + " on the board and nothing reported this turn. Call update_agent_status first:"
            + " REVIEW_PENDING if the work is done, outcome=question if you need the human,"
            + " IN_PROGRESS if you go on.");
  }

  private static boolean leavesQuestionUnreported(TaskState task, @Nullable String turnText) {
    return turnText != null
        && Move.endsUnreported(task.standing().status())
        && !task.standing().report().asks()
        && OPEN_QUESTION.matcher(turnText).find();
  }

  private TurnEndVerdict withTheRole(@Nullable TaskState task) {
    if (task == null) {
      return new TurnEndVerdict.Silent();
    }
    return roleNotes.stream()
        .flatMap(note -> note.noteFor(task).stream())
        .findFirst()
        .<TurnEndVerdict>map(TurnEndVerdict.Tell::new)
        .orElseGet(TurnEndVerdict.Silent::new);
  }
}
