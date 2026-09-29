package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.IntakeConfig;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * The far end of the loop: a task whose work has left the worktree is closed by the stage its own item reached,
 * so nobody has to come back and press the last button. The condition is the tracker's own word, never a reading
 * of whether the work LOOKS finished.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCloseJob implements Job {

    /** Every task handed over costs a read per poll, so one poll asks about a handful and the rest wait. */
    private static final int READS_PER_POLL = 5;

    private final ConfigService configService;
    private final StateService stateService;
    private final TrackerClose close;
    private final CommandService commands;

    @Override
    public String id() {
        return "ticket-close";
    }

    @Override
    public String describe() {
        return "close every task whose item reached the stage that says the work landed";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(configService.load().intake().everyMinutesOrDefault());
    }

    @Override
    public void run() {
        IntakeConfig intake = configService.load().intake();
        if (!intake.enabledOrDefault() || !intake.missing().isEmpty()) {
            return;
        }
        int reads = 0;
        for (Map.Entry<String, TaskState> entry : stateService.tasks().entrySet()) {
            if (reads == READS_PER_POLL) {
                return;
            }
            if (!FlowRules.handedOver(entry.getValue().status()) || !TaskName.isTicketKey(entry.getKey())) {
                continue;
            }
            reads++;
            closeIfLanded(entry.getKey(), entry.getValue());
        }
    }

    private void closeIfLanded(String taskId, TaskState task) {
        if (!close.closes(taskId, task)) {
            return;
        }
        log.atInfo().setMessage("tracker closed a task")
                .addKeyValue("task", taskId)
                .log();
        // Through the same door a human's press uses, stamped as nobody's judgement but the tracker's.
        OriginContext.as(ActionOrigin.TRACKER, () -> commands.execute(taskId, TaskAction.DONE));
    }
}
