package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.Verification;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runs the project's own command, then the plan's acceptance probes, for every task waiting at VERIFYING. Green
 * hands the round on to the human; red goes back to the agent with the output, so nobody opens an IDE on a tree
 * that does not build.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerifyJob implements Job {

    private final StateService stateService;
    private final Verification verification;
    private final AgentStatusReports statusReports;
    private final AgentSessions sessions;

    @Override
    public String id() {
        return "verify";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "run each project's own command before a hand-back reaches a human";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(20);
    }

    @Override
    public void run() {
        stateService.tasks().entrySet().stream()
                .filter(entry -> FlowRules.awaitingVerification(entry.getValue().status()))
                .forEach(entry -> verify(entry.getKey(), entry.getValue()));
    }

    private void verify(String taskId, TaskState task) {
        Optional<String> failure = verification.failure(task);
        if (failure.isPresent()) {
            sessions.relayIfChanged(taskId, brief(failure.get()));
            statusReports.report(FlowRules.verified(false), "verification failed; relayed", taskId);
            return;
        }
        List<Probe> probed = verification.probed(taskId, task);
        if (!probed.isEmpty()) {
            stateService.updateTask(taskId, current -> current.withProbes(probed));
        }
        List<Probe> failed = probed.stream().filter(Probe::failed).toList();
        if (failed.isEmpty()) {
            statusReports.report(FlowRules.verified(true), "verified", taskId);
            return;
        }
        sessions.relayIfChanged(taskId, PROBES_FAILED + failed.stream().map(Probe::result)
                .collect(Collectors.joining("\n\n")));
        statusReports.report(FlowRules.verified(false), "acceptance probe failed; relayed", taskId);
    }

    /** The plan was ratified with these probes, so the code is what changes; a new plan is the way to new probes. */
    private static final String PROBES_FAILED = "jagt ran the acceptance probes of the plan you were given a go on,"
            + " and these failed. Make them pass, leave the fix uncommitted, and report REVIEW_PENDING again. A probe"
            + " that is itself wrong changes only with a new plan: write it into plan.md and report PLAN_PENDING.\n\n";

    /** The output, and the one route out of it: a failing command is not a judgement to weigh. */
    private static String brief(String failure) {
        return "jagt ran this project's verification command and it failed. Fix it, leave the fix"
                + " uncommitted, and report REVIEW_PENDING again.\n\n" + failure;
    }
}
