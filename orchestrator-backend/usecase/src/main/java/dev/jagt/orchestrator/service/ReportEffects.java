package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.protocol.MessageContext;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * What a session's report sets going once it is applied: a wake after the next wait of {@code infraWait.backoff}
 * for a report of a wait, and a stop once the session has answered {@value #STEP} with a wait as many times as
 * there are waits. A reply no case takes closes its turn for the human.
 */
@Component
@RequiredArgsConstructor
public class ReportEffects {

    static final String STEP = "wait-over";
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
            return;
        }
        int waited = STEP.equals(turns.of(taskId).step()) ? count(turns.of(taskId).mark(WAITS)) : 0;
        properties.before(waited).ifPresentOrElse(wait -> {
            wakes.armIn(taskId, wait, STEP);
            turns.mark(taskId, WAITS, String.valueOf(waited + 1));
        }, () -> {
            turns.unmark(taskId, WAITS);
            stops.stop(taskId, TaskStop.Trigger.WAIT, said.message() == null ? "a dependency stayed down" : said.message());
        });
    }

    private static int count(@Nullable String mark) {
        try {
            return mark == null ? 0 : Integer.parseInt(mark);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
