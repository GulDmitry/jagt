package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Asks the Master's panel to read a round that has been handed back and not yet judged. The trigger is
 * deterministic — a status and a stamp, never a model's opinion that something looks ready — and the judgement
 * is the only part that is a model's.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterReviewJob implements Job {

    private final StateService stateService;
    private final ConfigService configService;
    private final MasterPanel panel;
    private final MasterReview reviews;
    private final MasterVerdicts verdicts;

    @Override
    public String id() {
        return "master-review";
    }

    @Override
    public String describe() {
        return "hand the Master session each round that came back, and act on what it answered";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(20);
    }

    @Override
    public void run() {
        ConfigService.ConfigFile.MasterConfig config = configService.load().master();
        if (!config.running()) {
            return;
        }
        stateService.tasks().forEach((taskId, task) -> {
            if (task.status() != TaskStatus.REVIEW_PENDING) {
                return;
            }
            reviews.of(task).filter(verdict -> verdict.writtenAt() >= task.statusSince())
                    .ifPresent(verdict -> verdicts.act(taskId, task, verdict, config));
        });
        // Asked LAST: a round holds this thread until every role answered, and verdicts already written go first.
        stateService.tasks().entrySet().stream()
                .filter(entry -> waiting(entry.getValue()))
                .findFirst()
                .ifPresent(entry -> ask(entry.getKey(), entry.getValue(), config));
    }

    private boolean waiting(TaskState task) {
        return task.status() == TaskStatus.REVIEW_PENDING && !reviews.readsTheRoundInFront(task);
    }

    private void ask(String taskId, TaskState task, ConfigService.ConfigFile.MasterConfig config) {
        log.atInfo().setMessage("master review asked").addKeyValue("task", taskId)
                .addKeyValue("alias", task.alias())
                .log();
        panel.review(taskId, task, config);
    }
}
