package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.master.MasterDecisions;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.PlanApproval;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * What a verdict does. Not ready goes back to the session that wrote the code and a question to the human,
 * whatever the mode; ready moves the task on only where a human said the reviewer stands in for them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterVerdicts {

    private final StepRelay relay;
    private final MasterShip ship;
    private final FlowReports reports;
    private final MasterDecisions decisions;
    private final PlanApproval approval;

    private static final String REVIEWER = "reviewer: ";

    /** Answers whether the verdict moved anything, so a caller can say so without reading the file again. */
    public boolean act(String taskId, TaskState task, MasterReview.Verdict verdict,
                       ConfigService.ConfigFile.MasterConfig config) {
        return OriginContext.as(ActionOrigin.MASTER, () -> judged(taskId, task, verdict, config));
    }

    private boolean judged(String taskId, TaskState task, MasterReview.Verdict verdict,
                           ConfigService.ConfigFile.MasterConfig config) {
        boolean plan = FlowRules.holdsAPlan(task.status());
        return switch (verdict.kind()) {
            case NOT_READY -> returned(taskId, task, verdict, plan);
            case QUESTION -> {
                decisions.returned(task, verdict.round(), List.of());
                yield ask(taskId, verdict.question());
            }
            case READY -> {
                decisions.returned(task, verdict.round(), List.of());
                yield plan ? config.may(MasterRight.PLAN) && !task.planGate().keptForTheHuman()
                        && go(taskId, task, verdict)
                        : config.may(MasterRight.SHIP) && ship.ship(taskId, task);
            }
        };
    }

    /** Repeats are relayed marked, never dropped; rounds of nothing but repeats end as the human's question. */
    private boolean returned(String taskId, TaskState task, MasterReview.Verdict verdict, boolean plan) {
        MasterDecisions.Returned seen = decisions.returned(task, verdict.round(), verdict.findings());
        List<String> findings = verdict.findings().stream()
                .map(said -> seen.repeated().contains(said.identity()) ? said.line() + REPEAT : said.line()).toList();
        int repeated = seen.rounds();
        if (repeated >= MasterDecisions.REPEATED_ROUNDS) {
            log.atWarn().setMessage("master review stalled").addKeyValue("task", taskId)
                    .addKeyValue("rounds", repeated)
                    .addKeyValue("cause", "findings repeat earlier rounds")
                    .addKeyValue("effect", "the round goes to the human")
                    .log();
            return ask(taskId, "These findings came back " + repeated + " rounds running, neither fixed nor"
                    + " disputed: " + String.join(" ", findings));
        }
        if (!relay.relayRound(taskId, plan ? "plan-not-ready" : "round-not-ready", String.join("\n", findings))) {
            return false;
        }
        String ruled = verdict.findings().stream().filter(MasterReview.Said::settles).map(MasterReview.Said::line)
                .collect(Collectors.joining("\n"));
        if (!ruled.isEmpty()) {
            decisions.record(task, ruled);
        }
        log.atInfo().setMessage("master returns the round").addKeyValue("task", taskId)
                .addKeyValue("findings", verdict.findings().size()).log();
        // Back to work, or the session's report is no transition and the verdict reads as this round's.
        return reports.report(taskId, FlowRules.relayed(), "reviewer: not ready; relayed");
    }

    private boolean go(String taskId, TaskState task, MasterReview.Verdict verdict) {
        if (task.report().asks() && !verdict.advice().isEmpty()) {
            relay.relay(taskId, "plan-answered", PLAN_ANSWERED + verdict.advice().stream().map(MasterReview.Said::line)
                    .collect(Collectors.joining("\n")));
        }
        if (!approval.approve(taskId, "The Master read plan.md against the ticket, standing in for the human")) {
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
        if (!relay.relay(taskId, "asked-human", "The reviewer asked the human: " + question)) {
            return false;
        }
        log.atInfo().setMessage("master asks").addKeyValue("task", taskId).addKeyValue("question", OneLine.clipped(question))
                .log();
        return reports.report(taskId, FlowRules.relayed(), AgentReport.QUESTION, REVIEWER + question);
    }

    /** The Master's answer to a question, and the task back at work on it. */
    public boolean answered(String taskId, TaskState task, String question, MasterPanel.Decision decision,
                            boolean freshSession) {
        return OriginContext.as(ActionOrigin.MASTER,
                () -> answer(taskId, task, question, decision.text(), opened(decision), freshSession));
    }

    private static String opened(MasterPanel.Decision decision) {
        return decision.newTask().isBlank() ? "" : "\n" + decision.newTask() + "\n" + NOT_OPENED;
    }

    private boolean answer(String taskId, TaskState task, String question, String decision, String opened,
                           boolean freshSession) {
        String answer = "The Master answered your question, standing in for the human:\n" + decision + opened;
        boolean told = FlowRules.awaitsItsInstruction(task.status())
                ? relay.relayUnder(taskId, "answered-under", answer, freshSession)
                : freshSession ? relay.relayFresh(taskId, "answered", answer) : relay.relay(taskId, "answered", answer);
        if (!told) {
            return false;
        }
        decisions.record(task, "- asked: " + question + " — decided: " + decision.replace('\n', ' '));
        decisions.answered(task);
        log.atInfo().setMessage("master answered").addKeyValue("task", taskId).addKeyValue("question", OneLine.clipped(question))
                .addKeyValue("decision", OneLine.clipped(decision)).log();
        return reports.report(taskId, FlowRules.relayed(), "master answered the question");
    }

    public int answersOverThisTree(TaskState task) {
        return decisions.answersOverThisTree(task);
    }

    public boolean stalled(TaskState task) {
        return decisions.repeatedRounds(task) >= MasterDecisions.REPEATED_ROUNDS;
    }

    private static final String REPEAT = " [repeat]";
    private static final String NOT_OPENED = "Not opened: only the human opens a task.";

    private static final String PLAN_ANSWERED = "The Master read plan.md against the ticket and answered your question,"
            + " standing in for the human:\n";
}
