package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Asks the Master session to read a round that has been handed back and not yet judged. The trigger is
 * deterministic — a status and a stamp, never a model's opinion that something looks ready — and the judgement
 * is the only part that is a model's.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterReviewJob implements Job {

    private final StateService stateService;
    private final ConfigService configService;
    private final MasterSession master;
    private final MasterReview reviews;

    @Override
    public String id() {
        return "master-review";
    }

    @Override
    public String describe() {
        return "hand the Master session each round that came back, before a human reads it";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(20);
    }

    @Override
    public void run() {
        if (!configService.load().master().running() || !master.live()) {
            return;
        }
        stateService.tasks().entrySet().stream()
                .filter(entry -> waiting(entry.getValue()))
                .findFirst()
                .ifPresent(entry -> ask(entry.getKey(), entry.getValue()));
    }

    /** One at a time: the session reads one round at a time, and a queue typed into it would interleave. */
    private boolean waiting(TaskState task) {
        return task.status() == TaskStatus.REVIEW_PENDING && !reviews.readsTheRoundInFront(task);
    }

    private void ask(String taskId, TaskState task) {
        if (!master.say(brief(taskId, task))) {
            return;
        }
        log.atInfo().setMessage("master review asked").addKeyValue("task", taskId)
                .addKeyValue("alias", task.alias())
                .log();
    }

    /** Names the task and the file, and nothing about what to conclude: that is the brief's, not this line's. */
    private String brief(String taskId, TaskState task) {
        return "Review task " + taskId + " in " + task.repos().stream().map(repo -> repo.worktreePath()).collect(java.util.stream.Collectors.joining(", "))
                + ". Read its uncommitted diff against " + task.baseBranchOr("the base branch")
                + " as the roles your brief names, write your findings to " + MasterReview.FILE
                + " in that worktree, and end that file with VERDICT: ready or VERDICT: not ready."
                + " Change nothing else there.";
    }
}
