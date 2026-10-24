package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
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
    private final StepRelay relay;

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
                .filter(entry -> FlowRules.awaitingVerification(entry.getValue().status()) && !entry.getValue().stopped())
                .forEach(entry -> verify(entry.getKey(), entry.getValue()));
    }

    private void verify(String taskId, TaskState task) {
        Optional<String> failure = verification.failure(task);
        if (failure.isPresent()) {
            returned(taskId, "verify", failure.get(), "verification failed; relayed");
            return;
        }
        List<Probe> probed = verification.probed(taskId, task);
        if (!probed.isEmpty()) {
            stateService.updateTask(taskId, current -> current.withProbes(probed));
        }
        List<Probe> failed = probed.stream().filter(Probe::failed).toList();
        if (failed.isEmpty()) {
            statusReports.verified(true, "verified", taskId);
            return;
        }
        returned(taskId, "probes", failed.stream().map(Probe::result).collect(Collectors.joining("\n\n")),
                "acceptance probe failed; relayed");
    }

    /** A round stopped at its ceiling stays at VERIFYING, verified anew once the human lifts the stop. */
    private void returned(String taskId, String step, String facts, String said) {
        if (!relay.relayRound(taskId, step, facts)
                && stateService.task(taskId).filter(TaskState::stopped).isPresent()) {
            return;
        }
        statusReports.verified(false, said, taskId);
    }
}
