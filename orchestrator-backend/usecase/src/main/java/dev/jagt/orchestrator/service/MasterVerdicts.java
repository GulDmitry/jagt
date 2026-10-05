package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * What a verdict does. Not ready goes back to the session that wrote the code and a question to the human,
 * whatever the mode; ready moves the task on only where a human said the reviewer stands in for them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterVerdicts {

    /** Fixed, so the relay of it is made once and a round handed back unchanged is not asked about again. */
    static final String NOTHING_TO_SHIP = "nothing to ship: no repository of this task holds work."
            + " Close the task, or say what it still needs?";

    private final AgentSessions sessions;
    private final MasterShip ship;
    private final FlowReports reports;
    private final MasterDecisions decisions;

    /** Answers whether the verdict moved anything, so a caller can say so without reading the file again. */
    public boolean act(String taskId, TaskState task, MasterReview.Verdict verdict,
                       ConfigService.ConfigFile.MasterConfig config) {
        switch (verdict.kind()) {
            case NOT_READY -> {
                if (!sessions.relayIfChanged(taskId, findings(verdict))) {
                    return false;
                }
                decisions.record(task, String.join("\n", verdict.findings()));
                // Back to work, or the session's REVIEW_PENDING is no transition and the verdict reads as this round's.
                return reports.report(taskId, TaskStatus.IN_PROGRESS, "reviewer: not ready; relayed");
            }
            case QUESTION -> {
                return ask(taskId, verdict.question());
            }
            case READY -> {
                if (!config.may(MasterRight.SHIP)) {
                    return false;
                }
                return ship.ship(taskId, task) || ask(taskId, NOTHING_TO_SHIP);
            }
        }
        return false;
    }

    /**
     * The session waits on the answer, so it is told the question, and back at work a hand-back after it is a new
     * round. The report is the one an agent makes when it stops rather than guess.
     */
    private boolean ask(String taskId, String question) {
        if (!sessions.relayIfChanged(taskId, "The reviewer asked the human: " + question
                + "\n\nYour status already carries the question. Report nothing and end your turn; the human answers"
                + " in this window. Then report IN_PROGRESS, apply the answer uncommitted, and hand the round back.")) {
            return false;
        }
        log.atInfo().setMessage("master asks").addKeyValue("task", taskId).log();
        return reports.report(taskId, TaskStatus.IN_PROGRESS, "outcome=question — reviewer: " + question);
    }

    /** The Master's answer to the session's own question, and the task back at work on it. */
    public boolean answered(String taskId, TaskState task, String question, String decision) {
        if (!sessions.relayIfChanged(taskId, "The Master answered your question, standing in for the human:\n"
                + decision + "\n\nReport IN_PROGRESS, apply it, and hand the round back as usual.")) {
            return false;
        }
        decisions.record(task, "- asked: " + question + " — decided: " + decision.replace('\n', ' '));
        log.atInfo().setMessage("master answered").addKeyValue("task", taskId).log();
        return reports.report(taskId, TaskStatus.IN_PROGRESS, "master answered the question");
    }

    /** The reviewer's own words, relayed whole: shortening a finding is deciding it, which is not jagt's. */
    private static String findings(MasterReview.Verdict verdict) {
        return "The review of your round came back NOT READY. Each line below is a review comment: fix it, or"
                + " where you believe it wrong, ask (rule 1), because the reviewer cannot read your reasons. Leave"
                + " the fix uncommitted and report REVIEW_PENDING again.\n\n" + String.join("\n", verdict.findings());
    }
}
