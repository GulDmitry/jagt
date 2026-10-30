package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.protocol.MessageContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * What a session's report sets going once it is applied: a wake after the next wait of {@code infraWait.backoff}
 * for a report of a wait, and a stop once the session has answered {@code wait-over} with a wait as many times as
 * there are waits. That stop keeps asking at the last wait, until the session reports something but a wait.
 * A reply no case takes closes its turn for the human.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportEffects {

    static final Step STEP = Step.WAIT_OVER;
    static final String WAITS = "waits";

    private final SessionWakes wakes;
    private final TaskTurns turns;
    private final TaskStops stops;
    private final InfraWaitProperties properties;

    /** The rules a report is judged by, told whether a wait is something jagt does here. */
    public MessageContext judged(MessageContext context) {
        return properties.waits() ? context : context.refusingWaits();
    }

    /** A reply refused stops its task first, so its next report is not refused for the same step again. */
    public void refused(String taskId) {
        turns.refused(taskId);
    }

    /** Called once the report is applied, so the turn it closed is the one a wake is armed for. */
    public void after(AgentStatusMessage said, String taskId) {
        if (!said.waits()) {
            stops.endOnReport(taskId).ifPresent(stop -> log.atInfo().setMessage("stop voided by read")
                    .addKeyValue("task", taskId).addKeyValue("trigger", stop.trigger())
                    .addKeyValue("read", "session report").log());
            return;
        }
        if (stops.standing(taskId, TaskStop.Trigger.WAIT)) {
            askAgain(taskId);
            return;
        }
        int waited = STEP.id().equals(turns.of(taskId).step()) ? count(turns.of(taskId).mark(WAITS)) : 0;
        properties.before(waited).ifPresentOrElse(wait -> {
            wakes.armIn(taskId, wait, STEP);
            turns.mark(taskId, WAITS, String.valueOf(waited + 1));
        }, () -> {
            turns.unmark(taskId, WAITS);
            stops.stop(taskId, TaskStop.Trigger.WAIT, said.message() == null ? "a dependency stayed down" : said.message())
                    .filter(stop -> stop.trigger() == TaskStop.Trigger.WAIT).ifPresent(stop -> askAgain(taskId));
        });
    }

    private void askAgain(String taskId) {
        properties.last().ifPresent(wait -> wakes.armIn(taskId, wait, STEP));
    }

    private static int count(@Nullable String mark) {
        try {
            return mark == null ? 0 : Integer.parseInt(mark);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
