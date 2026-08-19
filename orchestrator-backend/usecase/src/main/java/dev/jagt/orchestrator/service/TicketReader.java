package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.port.Tracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Where a ticket's facts come from: a configured {@link Tracker}'s own API when it can fetch that reference
 * (free, instant), otherwise the metered headless assistant, which follows a URL into any tracker at all (a
 * full model call, and the most expensive one in a task's life).
 *
 * <p>There is deliberately NO fallback from a failed tracker read to the assistant, for the same reason the
 * review side has none: a tracker that claims a reference owns it, and falling back would spend money
 * invisibly on every expired token while the launch still looked healthy.
 *
 * <p>The cost is RETURNED rather than charged here: the read is what produces the key the task is named by, so
 * there is no task to attribute it to until the caller has created one — which is what {@link #charge} is for.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketReader {

    private final List<Tracker> trackers;
    private final MeteredAssistant assistant;

    public Answer<TicketFacts> read(String ticketRef) {
        Optional<Tracker> tracker = trackers.stream().filter(t -> t.supports(ticketRef)).findFirst();
        if (tracker.isEmpty()) {
            return assistant.readTicket(ticketRef);
        }
        log.debug("Reading {} over the {} API (no tokens spent)", ticketRef, tracker.get().displayName());
        return new Answer<>(tracker.get().readTicket(ticketRef), TokenUsage.NONE);
    }

    public void charge(String taskId, TokenUsage usage) {
        assistant.chargeTask(taskId, usage);
    }
}
