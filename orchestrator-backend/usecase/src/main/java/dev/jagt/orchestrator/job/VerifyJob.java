package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.AgentStatusReports;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskMovedOn;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.Verification;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
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
public class VerifyJob implements TaskJob {

    private static final String FAILED = "verify:failed";

    private final StateService stateService;
    private final Verification verification;
    private final AgentStatusReports statusReports;
    private final StepRelay relay;
    private final TaskStops stops;

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
    public Watch watch() {
        return Watch.defaults().withStatuses(FlowRules::awaitingVerification).withStops(StopKind.NONE)
                .withReports(report -> !report.asks());
    }

    @Override
    public void run(String taskId, TaskState task) {
        verify(taskId, task);
    }

    private void verify(String taskId, TaskState task) {
        Optional<String> key = verification.key(task);
        if (key.filter(failing -> failing.equals(task.turnOrNone().mark(FAILED))).isPresent()) {
            stops.stop(taskId, TaskStop.Trigger.UNCHANGED_FAILURE, "handed back the tree its verification failed on: "
                    + standing(task));
            return;
        }
        Optional<String> failure = verification.failure(task);
        if (failure.isPresent()) {
            if (stillThisRound(taskId, task, "verify")) {
                returned(taskId, task, key, "verify", failure.get(), "verification failed; relayed");
            }
            return;
        }
        List<Probe> probed = verification.probed(taskId, task);
        if (!stillThisRound(taskId, task, "probes")) {
            return;
        }
        if (!probed.isEmpty()) {
            stateService.updateTask(taskId, current -> current.withProbes(probed));
        }
        List<Probe> failed = probed.stream().filter(Probe::failed).toList();
        if (failed.isEmpty()) {
            statusReports.verified(true, "verified", taskId);
            return;
        }
        returned(taskId, task, key, "probes", failed.stream().map(Probe::result).collect(Collectors.joining("\n\n")),
                "acceptance probe failed; relayed");
    }

    private boolean stillThisRound(String taskId, TaskState taken, String step) {
        Optional<TaskState> now = stateService.task(taskId);
        boolean same = now.filter(task -> task.status() == taken.status() && task.statusSince() == taken.statusSince())
                .isPresent();
        if (!same) {
            log.atInfo().setMessage("verification outdated").addKeyValue("task", taskId).addKeyValue("step", step)
                    .addKeyValue("status", now.map(task -> task.status().name()).orElse("gone")).log();
        }
        return same;
    }

    private static String standing(TaskState task) {
        String probes = task.probes().stream().filter(Probe::failed).map(Probe::toString)
                .collect(Collectors.joining("; "));
        return probes.isEmpty() ? "the project's verify command" : probes;
    }

    /** A round stopped at its ceiling stays at VERIFYING, verified anew once the human lifts the stop. */
    private void returned(String taskId, TaskState read, Optional<String> key, String step, String facts, String said) {
        boolean told;
        try {
            told = relay.relayRound(taskId, step, facts, Standing.of(read));
        } catch (TaskMovedOn dropped) {
            return;
        }
        if (told) {
            key.ifPresent(failing -> stateService.updateTask(taskId,
                    current -> current.withMark(FAILED, failing)));
        } else if (stateService.task(taskId).filter(TaskState::stopped).isPresent()) {
            return;
        }
        statusReports.verified(false, said, taskId);
    }
}
