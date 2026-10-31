package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/** What a finished check does to the task: rows not met go back as a round, unprovable ones to the human. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QaOutcomes {

    private final StepRelay relay;
    private final FlowReports reports;
    private final TaskStops stops;

    public boolean notMet(String taskId, TaskState task, QaVerdict verdict) {
        String facts = verdict.sha() + ":\n" + verdict.notMet().stream().map(QaVerdict.Row::line)
                .collect(Collectors.joining("\n"));
        StepRelay.Claim claim = new StepRelay.Claim(task, () -> reports.standing(taskId, task),
                opensTheTurn -> reports.reportWhileStanding(taskId, task, FlowRules.relayed(), AgentReport.PLAIN,
                        "qa: not met; relayed", opensTheTurn),
                opened -> reports.undoClaim(taskId, task, opened));
        boolean sent = OriginContext.as(ActionOrigin.QA, () -> relay.relayRound(taskId, Step.QA_ROUND, facts, claim));
        log.atInfo().setMessage("qa returns the round").addKeyValue("task", taskId)
                .addKeyValue("rows", verdict.notMet().size()).addKeyValue("sent", sent).log();
        return sent;
    }

    public void unprovable(String taskId, QaVerdict verdict) {
        String said = verdict.question().isBlank() ? verdict.rows().stream()
                .filter(row -> row.status() != QaVerdict.Status.MET).map(QaVerdict.Row::line)
                .collect(Collectors.joining("\n")) : verdict.question();
        reports.report(taskId, TaskStatus.DEPLOYED, AgentReport.QUESTION, "QA could not prove, on "
                + verdict.sha() + ":\n" + (said.isBlank() ? "the check named no criterion" : said));
        log.atInfo().setMessage("qa asks").addKeyValue("task", taskId).addKeyValue("rows", verdict.rows().size()).log();
    }

    public void failed(String taskId, QaVerdict verdict) {
        stops.stop(taskId, TaskStop.Trigger.QA_FAILED, verdict.failure());
        log.atWarn().setMessage("qa check failed").addKeyValue("task", taskId)
                .addKeyValue("cause", verdict.failure()).log();
    }
}
