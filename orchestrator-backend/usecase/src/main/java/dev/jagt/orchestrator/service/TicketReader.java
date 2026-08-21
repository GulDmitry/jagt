package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.port.Tracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
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
@Slf4j
public class TicketReader {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    /** Bounds a launch a human is waiting on: a read that hangs to its own timeout leaves no room for another. */
    private static final Duration BUDGET = Duration.ofMinutes(2);

    private final List<Tracker> trackers;
    private final MeteredAssistant assistant;
    private final int maxAttempts;
    private final Duration retryDelay;

    @Autowired
    public TicketReader(List<Tracker> trackers, MeteredAssistant assistant) {
        this(trackers, assistant, MAX_ATTEMPTS, RETRY_DELAY);
    }

    TicketReader(List<Tracker> trackers, MeteredAssistant assistant, int maxAttempts, Duration retryDelay) {
        this.trackers = trackers;
        this.assistant = assistant;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
    }

    public Answer<TicketFacts> read(String ticketRef) {
        Optional<Tracker> tracker = trackers.stream().filter(t -> t.supports(ticketRef)).findFirst();
        if (tracker.isPresent()) {
            log.debug("Reading {} over the {} API (no tokens spent)", ticketRef, tracker.get().displayName());
            return new Answer<>(tracker.get().readTicket(ticketRef), TokenUsage.NONE);
        }
        return askUntilUsable(ticketRef);
    }

    /**
     * An API's "no such item" is a fact; a model's is a guess — the tool it needed may simply not have been
     * found, which is indistinguishable from an item that is gone. So a non-answer is asked again, and only the
     * last one is believed. Every attempt is paid for, so all of them are returned as one spend.
     */
    private Answer<TicketFacts> askUntilUsable(String ticketRef) {
        long deadline = System.nanoTime() + BUDGET.toNanos();
        Answer<TicketFacts> answer = Answer.unavailable();
        TokenUsage spent = TokenUsage.NONE;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            answer = assistant.readTicket(ticketRef);
            spent = spent.plus(answer.usage());
            if (answer.facts().filter(TicketFacts::usable).isPresent()) {
                return new Answer<>(answer.facts(), spent);
            }
            log.warn("Read of {} answered nothing usable on attempt {} of {}", ticketRef, attempt, maxAttempts);
            if (attempt == maxAttempts || System.nanoTime() > deadline || !pause()) {
                break;
            }
        }
        return new Answer<>(answer.facts(), spent);
    }

    private boolean pause() {
        try {
            Thread.sleep(retryDelay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void charge(String taskId, TokenUsage usage) {
        assistant.chargeTask(taskId, usage);
    }
}
