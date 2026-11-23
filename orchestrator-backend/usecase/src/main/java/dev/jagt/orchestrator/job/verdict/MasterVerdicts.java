package dev.jagt.orchestrator.job.verdict;

import dev.jagt.orchestrator.config.MasterMove;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.job.master.MasterShip;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.service.turn.StepRelay;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.master.MasterRight;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * What a verdict does. Not ready goes back to the session that wrote the code and a question to the
 * human, whatever the mode; ready moves the task on only where a human said the reviewer stands in
 * for them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterVerdicts {

  private final StepRelay relay;
  private final MasterShip ship;
  private final MasterClaims claims;
  private final MasterDecisions decisions;

  private static final String REVIEWER = "reviewer: ";

  /**
   * Answers whether the verdict moved anything, so a caller can say so without reading the file
   * again.
   */
  public boolean act(
      String taskId,
      TaskState task,
      MasterReview.Verdict verdict,
      ConfigFile.ArbiterConfig config) {
    return OriginContext.as(ActionOrigin.MASTER, () -> judged(taskId, task, verdict, config));
  }

  private boolean judged(
      String taskId,
      TaskState task,
      MasterReview.Verdict verdict,
      ConfigFile.ArbiterConfig config) {
    boolean plan = FlowRules.holdsPlan(task.standing().status());
    return switch (verdict.kind()) {
      case NOT_READY -> returned(taskId, task, verdict, plan);
      case QUESTION -> {
        boolean asked = ask(taskId, task, verdict.question());
        if (asked) {
          decisions.returned(task, verdict.round(), List.of());
        }
        yield asked;
      }
      case READY -> ready(taskId, task, verdict, config);
    };
  }

  private boolean ready(
      String taskId,
      TaskState task,
      MasterReview.Verdict verdict,
      ConfigFile.ArbiterConfig config) {
    decisions.returned(task, verdict.round(), List.of());
    return config.may(MasterRight.SHIP) && ship.ship(taskId, task);
  }

  /**
   * Repeats are relayed marked, never dropped; they count toward the round ceiling like any round.
   */
  private boolean returned(
      String taskId, TaskState task, MasterReview.Verdict verdict, boolean plan) {
    Set<MasterReview.Identity> repeated =
        decisions.repeated(task, verdict.round(), verdict.findings());
    List<String> findings =
        verdict.findings().stream()
            .map(said -> repeated.contains(said.identity()) ? said.line() + REPEAT : said.line())
            .toList();
    MasterMove returning = plan ? MasterMove.PLAN_NOT_READY : MasterMove.ROUND_NOT_READY;
    StepRelay.Claim claim =
        claims.reporting(taskId, task, AgentReport.PLAIN, "reviewer: not ready; relayed");
    if (!relay.relayRound(taskId, returning.step(), String.join("\n", findings), claim)) {
      return false;
    }
    decisions.returned(task, verdict.round(), verdict.findings());
    String ruled =
        verdict.findings().stream()
            .filter(MasterReview.Said::settles)
            .map(MasterReview.Said::line)
            .collect(Collectors.joining("\n"));
    if (!ruled.isEmpty()) {
      decisions.record(task, ruled);
    }
    log.atInfo()
        .setMessage("master returns the round")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("findings", verdict.findings().size())
        .log();
    return true;
  }

  /**
   * The session waits on the answer, so it is told the question, and back at work a hand-back after
   * it is a new round. The report is the one an agent makes when it stops rather than guess.
   */
  private boolean ask(String taskId, TaskState task, String question) {
    StepRelay.Claim claim =
        claims.reporting(taskId, task, AgentReport.QUESTION, REVIEWER + question);
    if (!relay.relay(
        taskId,
        MasterMove.ASKED_HUMAN.step(),
        "The reviewer asked the human: " + question,
        claim)) {
      return false;
    }
    log.atInfo()
        .setMessage("master asks")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("question", OneLine.clipped(question))
        .log();
    return true;
  }

  /** The Master's answer to a question, and the task back at work on it. */
  public boolean answered(
      String taskId, TaskState task, String question, MasterPanel.Decision decision) {
    return OriginContext.as(
        ActionOrigin.MASTER,
        () -> answer(taskId, task, question, decision.text(), opened(decision)));
  }

  private static String opened(MasterPanel.Decision decision) {
    return decision.newTask().isBlank() ? "" : "\n" + decision.newTask() + "\n" + NOT_OPENED;
  }

  private boolean answer(
      String taskId, TaskState task, String question, String decision, String opened) {
    String answer =
        "The Master answered your question, standing in for the human:\n" + decision + opened;
    StepRelay.Claim claim =
        claims.reporting(taskId, task, AgentReport.PLAIN, "master answered the question");
    if (!tell(taskId, task, answer, claim)) {
      return false;
    }
    decisions.record(task, "- asked: " + question + " — decided: " + decision.replace('\n', ' '));
    decisions.answered(task);
    log.atInfo()
        .setMessage("master answered")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("question", OneLine.clipped(question))
        .addKeyValue("decision", OneLine.clipped(decision))
        .log();
    return true;
  }

  /**
   * A stop the Master lifted, the session told its instruction as an answer, and back at work on
   * it; not where a plan waits.
   */
  public boolean stopReturned(String taskId, TaskState task, String instruction) {
    return OriginContext.as(
        ActionOrigin.MASTER,
        () -> {
          StepRelay.Claim claim =
              claims.reporting(
                  taskId, task, AgentReport.PLAIN, "master: stop lifted, returned to the session");
          boolean told =
              tell(
                  taskId,
                  task,
                  "The Master lifted jagt's stop and returns the task to you, standing in for"
                      + " the human:\n"
                      + instruction,
                  claim);
          if (told) {
            log.atInfo()
                .setMessage("master returns the task")
                .addKeyValue("task", taskId)
                .addKeyValue("alias", task.ticket().alias())
                .addKeyValue("instruction", OneLine.clipped(instruction))
                .log();
          }
          return told;
        });
  }

  private boolean tell(String taskId, TaskState task, String text, StepRelay.Claim claim) {
    if (FlowRules.awaitsItsInstruction(task.standing().status())) {
      return relay.relayUnder(taskId, MasterMove.ANSWERED_UNDER.step(), text, false, claim);
    }
    return relay.relay(taskId, MasterMove.ANSWERED.step(), text, claim);
  }

  public int answersOverThisTree(TaskState task) {
    return decisions.answersOverThisTree(task);
  }

  private static final String REPEAT = " [repeat]";
  private static final String NOT_OPENED = "Not opened: only the human opens a task.";
}
