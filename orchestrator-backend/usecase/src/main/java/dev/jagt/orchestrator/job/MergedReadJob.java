package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.MergedRequests;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Reads again every request git found merged and the host did not, so a reset base or a reverted merge ends it. */
@Service
@RequiredArgsConstructor
public class MergedReadJob implements Job {

    private final StateService stateService;
    private final MergedRequests merged;

    @Override
    public String id() {
        return "merged-read";
    }

    @Override
    public String describe() {
        return "read again the requests git found merged, and let ship and auto-review resume where the merge is gone";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(5);
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public void run() {
        stateService.tasks().forEach((taskId, task) -> {
            if (foundByGit(task) && !FlowRules.closed(task.status())) {
                merged.refresh(taskId);
            }
        });
    }

    private static boolean foundByGit(TaskState task) {
        return task.repos().stream().anyMatch(repo -> repo.hasReviewRequest() && repo.wasMerged() && !repo.wasHostMerged());
    }
}
