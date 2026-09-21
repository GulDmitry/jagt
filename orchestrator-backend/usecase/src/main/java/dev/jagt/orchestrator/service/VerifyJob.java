package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Runs the project's own command for every task waiting at VERIFYING. Green hands the round on to the human;
 * red goes back to the agent with the output, so nobody opens an IDE on a tree that does not build.
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
                .filter(entry -> entry.getValue().status() == TaskStatus.VERIFYING)
                .forEach(entry -> verify(entry.getKey(), entry.getValue()));
    }

    private void verify(String taskId, TaskState task) {
        Optional<String> failure = verification.failure(task);
        if (failure.isEmpty()) {
            statusReports.report(TaskStatus.REVIEW_PENDING, "verified", taskId);
            return;
        }
        sessions.relayIfChanged(taskId, brief(failure.get()));
        statusReports.report(TaskStatus.IN_PROGRESS, "verification failed; relayed", taskId);
    }

    /** The output, and the one route out of it: a failing command is not a judgement to weigh. */
    private static String brief(String failure) {
        return "jagt ran this project's verification command and it failed. Fix it, leave the fix"
                + " uncommitted, and report REVIEW_PENDING again.\n\n" + failure;
    }
}
