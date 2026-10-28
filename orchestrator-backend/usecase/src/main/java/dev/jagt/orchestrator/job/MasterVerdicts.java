package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.MasterMove;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
                boolean asked = ask(taskId, task, verdict.question());
                if (asked) {
                    decisions.returned(task, verdict.round(), List.of());
                }
                yield asked;
            }
            case READY -> plan ? config.may(MasterRight.PLAN) && !task.planGate().keptForTheHuman()
                    && go(taskId, task, verdict) : ready(taskId, task, verdict, config);
        };
    }

    private boolean ready(String taskId, TaskState task, MasterReview.Verdict verdict,
                          ConfigService.ConfigFile.MasterConfig config) {
        decisions.returned(task, verdict.round(), List.of());
        return config.may(MasterRight.SHIP) && ship.ship(taskId, task);
    }

    /** Repeats are relayed marked, never dropped; they count toward the round ceiling like any round. */
    private boolean returned(String taskId, TaskState task, MasterReview.Verdict verdict, boolean plan) {
        Set<MasterReview.Identity> repeated = decisions.repeated(task, verdict.round(), verdict.findings());
        List<String> findings = verdict.findings().stream()
                .map(said -> repeated.contains(said.identity()) ? said.line() + REPEAT : said.line()).toList();
        MasterMove returning = plan ? MasterMove.PLAN_NOT_READY : MasterMove.ROUND_NOT_READY;
        StepRelay.Claim claim = claiming(taskId, task, AgentReport.PLAIN, "reviewer: not ready; relayed");
        if (!relay.relayRound(taskId, returning.id(), String.join("\n", findings), claim)) {
            return false;
        }
        decisions.returned(task, verdict.round(), verdict.findings());
        String ruled = verdict.findings().stream().filter(MasterReview.Said::settles).map(MasterReview.Said::line)
                .collect(Collectors.joining("\n"));
        if (!ruled.isEmpty()) {
            decisions.record(task, ruled);
        }
        log.atInfo().setMessage("master returns the round").addKeyValue("task", taskId).addKeyValue("alias", task.alias())
                .addKeyValue("findings", verdict.findings().size()).log();
        return true;
    }

    /** Back to work in the claim itself, or the session's report is no transition and the verdict reads as this round's. */
    private StepRelay.Claim claiming(String taskId, TaskState read, AgentReport means, String message) {
        return new StepRelay.Claim(read, () -> reports.standing(taskId, read),
                opensTheTurn -> reports.reportWhileStanding(taskId, read, FlowRules.relayed(), means, message, opensTheTurn),
                opened -> reports.undoClaim(taskId, read, opened));
    }

    private boolean go(String taskId, TaskState task, MasterReview.Verdict verdict) {
        StepRelay.Claim claim = claiming(taskId, advised(taskId, task, verdict), AgentReport.PLAIN, "master: the plan holds");
        if (!approval.approve(taskId, "The Master read plan.md against the ticket, standing in for the human", claim)) {
            return false;
        }
        decisions.returned(task, verdict.round(), List.of());
        log.atInfo().setMessage("master approves the plan").addKeyValue("task", taskId).addKeyValue("alias", task.alias()).log();
        return true;
    }

    /** The task as it stands once the plan's question is answered, which opens a turn of its own and moves no status. */
    private TaskState advised(String taskId, TaskState task, MasterReview.Verdict verdict) {
        if (!task.report().asks() || verdict.advice().isEmpty()) {
            return task;
        }
        AtomicReference<TaskState> after = new AtomicReference<>(task);
        relay.relay(taskId, MasterMove.PLAN_ANSWERED.id(), PLAN_ANSWERED + verdict.advice().stream()
                        .map(MasterReview.Said::line).collect(Collectors.joining("\n")),
                new StepRelay.Claim(task, () -> reports.standing(taskId, task),
                        opens -> reports.claimWhileStanding(taskId, task, live -> {
                            TaskState opened = opens.apply(live);
                            after.set(opened);
                            return opened;
                        }),
                        opened -> reports.undoClaim(taskId, task, opened)));
        return after.get();
    }

    /**
     * The session waits on the answer, so it is told the question, and back at work a hand-back after it is a new
     * round. The report is the one an agent makes when it stops rather than guess.
     */
    private boolean ask(String taskId, TaskState task, String question) {
        StepRelay.Claim claim = claiming(taskId, task, AgentReport.QUESTION, REVIEWER + question);
        if (!relay.relay(taskId, MasterMove.ASKED_HUMAN.id(), "The reviewer asked the human: " + question, claim)) {
            return false;
        }
        log.atInfo().setMessage("master asks").addKeyValue("task", taskId).addKeyValue("alias", task.alias())
                .addKeyValue("question", OneLine.clipped(question)).log();
        return true;
    }

    /** The Master's answer to a question, and the task back at work on it. */
    public boolean answered(String taskId, TaskState task, String question, MasterPanel.Decision decision) {
        return OriginContext.as(ActionOrigin.MASTER,
                () -> answer(taskId, task, question, decision.text(), opened(decision)));
    }

    private static String opened(MasterPanel.Decision decision) {
        return decision.newTask().isBlank() ? "" : "\n" + decision.newTask() + "\n" + NOT_OPENED;
    }

    private boolean answer(String taskId, TaskState task, String question, String decision, String opened) {
        String answer = "The Master answered your question, standing in for the human:\n" + decision + opened;
        StepRelay.Claim claim = claiming(taskId, task, AgentReport.PLAIN, "master answered the question");
        boolean told = FlowRules.awaitsItsInstruction(task.status())
                ? relay.relayUnder(taskId, MasterMove.ANSWERED_UNDER.id(), answer, false, claim)
                : relay.relay(taskId, MasterMove.ANSWERED.id(), answer, claim);
        if (!told) {
            return false;
        }
        decisions.record(task, "- asked: " + question + " — decided: " + decision.replace('\n', ' '));
        decisions.answered(task);
        log.atInfo().setMessage("master answered").addKeyValue("task", taskId).addKeyValue("alias", task.alias())
                .addKeyValue("question", OneLine.clipped(question))
                .addKeyValue("decision", OneLine.clipped(decision)).log();
        return true;
    }

    public int answersOverThisTree(TaskState task) {
        return decisions.answersOverThisTree(task);
    }

    private static final String REPEAT = " [repeat]";
    private static final String NOT_OPENED = "Not opened: only the human opens a task.";

    private static final String PLAN_ANSWERED = "The Master read plan.md against the ticket and answered your question,"
            + " standing in for the human:\n";
}
