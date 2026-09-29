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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    /** Where the last poll stopped, so the next one carries on rather than starting over. */
    private String lastAsked;

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
        Map<String, TaskState> handedOver = handedOver();
        // From wherever the last poll stopped: a task is handed over until it is closed, so starting at the top
        // every time would spend every read on the first few and never reach the rest.
        List<String> order = new ArrayList<>(handedOver.keySet());
        int from = Math.max(0, order.indexOf(lastAsked) + 1);
        for (int asked = 0; asked < Math.min(READS_PER_POLL, order.size()); asked++) {
            String taskId = order.get((from + asked) % order.size());
            lastAsked = taskId;
            closeIfLanded(taskId, handedOver.get(taskId));
        }
    }

    private Map<String, TaskState> handedOver() {
        Map<String, TaskState> waiting = new LinkedHashMap<>();
        stateService.tasks().forEach((taskId, task) -> {
            if (FlowRules.handedOver(task.status()) && TaskName.isTicketKey(taskId)) {
                waiting.put(taskId, task);
            }
        });
        return waiting;
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
