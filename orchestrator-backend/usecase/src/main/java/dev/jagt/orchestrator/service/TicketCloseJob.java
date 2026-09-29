package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.IntakeConfig;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

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
    private final TicketReader tickets;
    private final TrackerWorkflow workflow;
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
            closeIfLanded(entry.getKey());
        }
    }

    private void closeIfLanded(String taskId) {
        Answer<TicketFacts> read = tickets.read(taskId);
        tickets.charge(taskId, read.usage());
        Optional<TicketFacts> facts = read.facts().filter(TicketFacts::usable);
        // A stage nobody could read leaves the task exactly where it was: closing on silence would drop a
        // worktree because a tracker was down.
        if (facts.isEmpty() || facts.get().trackerStatus().isBlank()) {
            log.atWarn().setMessage("close stage unreadable")
                    .addKeyValue("task", taskId)
                    .addKeyValue("cause", read.facts().isEmpty() ? "the read never reached the tracker"
                            : "the item came back with no workflow status")
                    .log();
            return;
        }
        if (!workflow.closesWork(facts.get())) {
            return;
        }
        log.atInfo().setMessage("tracker closed a task")
                .addKeyValue("task", taskId)
                .addKeyValue("stage", facts.get().trackerStatus())
                .log();
        // Through the same door a human's press uses, stamped as nobody's judgement but the tracker's.
        OriginContext.as(ActionOrigin.TRACKER, () -> commands.execute(taskId, TaskAction.DONE));
    }
}
