package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.AgentReport;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.MasterRight;
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
        return "hand the Master session each plan and each round that came back, and act on what it answered";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(2);
    }

    @Override
    public void run() {
        ConfigService.ConfigFile.MasterConfig config = configService.load().master();
        if (!config.running()) {
            return;
        }
        stateService.tasks().forEach((taskId, task) -> {
            if (!Move.masterReads(task.status())) {
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
        return reviewable(task, reviews.readsTheRoundInFront(task),
                configService.load().master().may(MasterRight.ANSWER));
    }

    /** A question the Master answers is the answer job's: reviewing its round ships around the question. */
    static boolean reviewable(TaskState task, boolean judged, boolean questionsAnswered) {
        return Move.masterReads(task.status()) && !judged
                && !(questionsAnswered && AgentReport.of(task.message()) == AgentReport.QUESTION);
    }

    private void ask(String taskId, TaskState task, ConfigService.ConfigFile.MasterConfig config) {
        log.atInfo().setMessage("master review asked").addKeyValue("task", taskId)
                .addKeyValue("alias", task.alias())
                .log();
        if (FlowRules.holdsAPlan(task.status())) {
            panel.plan(taskId, task, config);
        } else {
            panel.review(taskId, task, config);
        }
    }
}
