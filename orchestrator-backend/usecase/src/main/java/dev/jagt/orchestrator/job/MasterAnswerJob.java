package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskMovedOn;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Where the Master stands in for the human, a session that stopped to ask is answered by it, not
 * left waiting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterAnswerJob implements Job {

  private final StateService stateService;
  private final ConfigService configService;
  private final MasterPanel panel;
  private final MasterVerdicts verdicts;
  private final TaskTurns turns;

  @Override
  public String id() {
    return "master-answer";
  }

  @Override
  public Serves serves() {
    return Serves.MASTER;
  }

  @Override
  public String describe() {
    return "answer the question a session stopped on, where the Master stands in for the human";
  }

  @Override
  public Duration every() {
    return Duration.ofSeconds(2);
  }

  @Override
  public void run() {
    ConfigService.ConfigFile.MasterConfig config = configService.load().master();
    if (!config.may(MasterRight.ANSWER)) {
      return;
    }
    Arbiter.Profile profile = config.profile();
    stateService.tasks().entrySet().stream()
        .filter(entry -> answers(entry.getValue(), profile))
        .findFirst()
        .ifPresent(entry -> answer(entry.getKey(), entry.getValue(), config));
  }

  /** A question no answer came back for is not asked again, nor after a restart; a new one is. */
  private static boolean answers(TaskState task, Arbiter.Profile profile) {
    return Move.asking(task.status(), task.report())
        && Turns.next(task.status(), task.report(), task.stopped(), Checkers.of(task), profile)
            .equals(Turn.Holder.ARBITER)
        && !task.masterOrNone().unanswered().equals(task.askedDigest());
  }

  private void unanswered(String taskId) {
    turns.unanswered(taskId);
  }

  /**
   * What no prose answer settles: a right only the human grants, a repository jagt attaches, a
   * plan.
   */
  private static Optional<String> humans(TaskState task) {
    if (task.report() == AgentReport.PERMISSION) {
      return Optional.of("a permission only the human grants");
    }
    if (task.message() == null) {
      return Optional.of("a question with no text");
    }
    if (task.asksForRepository()) {
      return Optional.of("a repository jagt attaches");
    }
    return FlowRules.holdsPlan(task.status())
        ? Optional.of("the plan review answers it")
        : Optional.empty();
  }

  private void answer(String taskId, TaskState task, ConfigService.ConfigFile.MasterConfig config) {
    Optional<String> humans = humans(task);
    if (humans.isPresent()) {
      log.atInfo()
          .setMessage("master answer withheld")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", humans.get())
          .addKeyValue("effect", "no answer is written here")
          .log();
      unanswered(taskId);
      return;
    }
    int given = verdicts.answersOverThisTree(task);
    if (given >= MasterDecisions.RUNAWAY) {
      log.atWarn()
          .setMessage("master answer runaway")
          .addKeyValue("task", taskId)
          .addKeyValue("answers", given)
          .addKeyValue("cause", "answers over a tree nothing changed in")
          .addKeyValue("effect", "the question waits for the human")
          .log();
      unanswered(taskId);
      return;
    }
    // Answers nothing acted on change the approach, in the same session.
    boolean stuck = given >= MasterDecisions.ANSWERS_PER_TREE;
    log.atInfo()
        .setMessage("master answers")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.alias())
        .log();
    String question = Objects.requireNonNull(task.message());
    try {
      switch (panel.answer(taskId, task, question, config, stuck)) {
        case MasterPanel.Undecided undecided -> unusable(taskId, undecided.cause());
        case MasterPanel.Decision decision -> {
          if (!verdicts.answered(taskId, task, question, decision)) {
            unusable(taskId, "the session could not be told the decision");
          }
        }
      }
    } catch (TaskMovedOn dropped) {
      return;
    }
  }

  private void unusable(String taskId, String cause) {
    log.atWarn()
        .setMessage("master answer unusable")
        .addKeyValue("task", taskId)
        .addKeyValue("cause", cause)
        .addKeyValue("effect", "the question waits for the human")
        .log();
    unanswered(taskId);
  }
}
