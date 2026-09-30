package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.ActionOrigin;
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

    private final AgentSessions sessions;
    private final CommandService commands;
    private final FlowReports reports;

    /** Answers whether the verdict moved anything, so a caller can say so without reading the file again. */
    public boolean act(String taskId, TaskState task, MasterReview.Verdict verdict,
                       ConfigService.ConfigFile.MasterConfig config) {
        switch (verdict.kind()) {
            case NOT_READY -> {
                if (!sessions.relayIfChanged(taskId, findings(verdict))) {
                    return false;
                }
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
                log.atInfo().setMessage("master ships").addKeyValue("task", taskId).log();
                // Through the same door a human's press uses, so an illegal move is refused rather than taken, and
                // stamped as the Master's so what a ship does on its behalf can differ from what it does on yours.
                OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, TaskAction.SHIP));
                return true;
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
                + "\n\nWait for the answer in this window, apply it uncommitted, and report REVIEW_PENDING again.")) {
            return false;
        }
        log.atInfo().setMessage("master asks").addKeyValue("task", taskId).log();
        return reports.report(taskId, TaskStatus.IN_PROGRESS, "outcome=question — reviewer: " + question);
    }

    /** The reviewer's own words, relayed whole: shortening a finding is deciding it, which is not jagt's. */
    private static String findings(MasterReview.Verdict verdict) {
        return "The review of your round came back NOT READY. Fix every line below, leave the fix uncommitted,"
                + " and report REVIEW_PENDING again.\n\n" + String.join("\n", verdict.findings());
    }
}
