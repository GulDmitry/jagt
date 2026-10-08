package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.service.master.MasterShip;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * What a verdict does. Not ready goes back to the session that wrote the code and a question to the human,
 * whatever the mode; ready moves the task on only where a human said the reviewer stands in for them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterVerdicts {

    private final AgentSessions sessions;
    private final MasterShip ship;
    private final FlowReports reports;
    private final MasterDecisions decisions;

    private static final String DO = "do ";

    /** Answers whether the verdict moved anything, so a caller can say so without reading the file again. */
    public boolean act(String taskId, TaskState task, MasterReview.Verdict verdict,
                       ConfigService.ConfigFile.MasterConfig config) {
        boolean plan = FlowRules.holdsAPlan(task.status());
        switch (verdict.kind()) {
            case NOT_READY -> {
                if (!sessions.relayIfChanged(taskId, (plan ? PLAN_RETURNED : ROUND_RETURNED)
                        + String.join("\n", verdict.findings()))) {
                    return false;
                }
                String ruled = verdict.findings().stream().filter(f -> !f.contains(" — " + MasterPanel.SHOW))
                        .collect(Collectors.joining("\n"));
                if (!ruled.isEmpty()) {
                    decisions.record(task, ruled);
                }
                log.atInfo().setMessage("master returns the round").addKeyValue("task", taskId)
                        .addKeyValue("findings", verdict.findings().size()).log();
                // Back to work, or the session's report is no transition and the verdict reads as this round's.
                return reports.report(taskId, FlowRules.relayed(), "reviewer: not ready; relayed");
            }
            case QUESTION -> {
                return ask(taskId, verdict.question());
            }
            case READY -> {
                if (plan) {
                    return config.may(MasterRight.PLAN) && go(taskId);
                }
                if (!config.may(MasterRight.SHIP)) {
                    return false;
                }
                return ship.ship(taskId, task);
            }
        }
        return false;
    }

    private boolean go(String taskId) {
        if (!sessions.relayIfChanged(taskId, "The Master read plan.md against the ticket, standing in for the human:"
                + " the plan holds. Start on it.")) {
            return false;
        }
        log.atInfo().setMessage("master approves the plan").addKeyValue("task", taskId).log();
        return reports.report(taskId, FlowRules.relayed(), "master: the plan holds");
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
        log.atInfo().setMessage("master asks").addKeyValue("task", taskId).addKeyValue("question", question).log();
        return reports.report(taskId, FlowRules.relayed(), "outcome=question — reviewer: " + question);
    }

    /** The Master's answer to the session's own question, and the task back at work on it. */
    public boolean answered(String taskId, TaskState task, String question, String decision, boolean freshSession) {
        String opened = decision.lines().filter(line -> line.startsWith(DO))
                .map(line -> "\n" + ship.open(line.substring(DO.length()))).collect(Collectors.joining());
        if (!sessions.relayIfChanged(taskId, "The Master answered your question, standing in for the human:\n"
                + decision + opened + "\n\nReport IN_PROGRESS, apply it, and hand the round back as usual.")) {
            return false;
        }
        decisions.record(task, "- asked: " + question + " — decided: " + decision.replace('\n', ' '));
        decisions.answered(task);
        if (freshSession) {
            sessions.openTaskTab(taskId, null);
        }
        log.atInfo().setMessage("master answered").addKeyValue("task", taskId).addKeyValue("question", question)
                .addKeyValue("decision", decision).log();
        return reports.report(taskId, FlowRules.relayed(), "master answered the question");
    }

    public int answersOverThisTree(TaskState task) {
        return decisions.answersOverThisTree(task);
    }

    /** Ahead of the reviewer's own words, relayed whole: shortening a finding is deciding it, which is not jagt's. */
    private static final String ROUND_RETURNED = "The review of your round came back NOT READY. Each line below is a"
            + " review comment: fix it, or where you believe it wrong, change nothing for it and write in"
            + " task_notes.md `disputed: <the comment> — <evidence>`. A `show:` comment asks for evidence: answer it"
            + " the same way. Evidence is a ticket line, a file:line, or a command and what it printed: the reviewer"
            + " checks evidence and cannot read your reasons. Ask (rule 1) only for a decision nobody gave you. Leave"
            + " the fix uncommitted and report REVIEW_PENDING again.\n\n";

    private static final String PLAN_RETURNED = "The Master read plan.md against the ticket: NOT READY. Rework the"
            + " plan for each line below. Where you believe one wrong, change nothing for it and write in"
            + " task_notes.md `disputed: <the comment> — <evidence>`. Then report PLAN_PENDING again.\n\n";
}
