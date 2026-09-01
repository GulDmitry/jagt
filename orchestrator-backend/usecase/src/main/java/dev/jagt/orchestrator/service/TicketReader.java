package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Where a ticket's facts come from: the metered headless assistant, following the reference into whatever tracker
 * holds it. The cost is RETURNED rather than charged, the read being what produces the key the task is named by.
 */
@Component
@Slf4j
public class TicketReader {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    /** Bounds a launch a human is waiting on: a read that hangs to its own timeout leaves no room for another. */
    private static final Duration BUDGET = Duration.ofMinutes(2);

    private final MeteredAssistant assistant;
    private final int maxAttempts;
    private final Duration retryDelay;

    @Autowired
    public TicketReader(MeteredAssistant assistant) {
        this(assistant, MAX_ATTEMPTS, RETRY_DELAY);
    }

    TicketReader(MeteredAssistant assistant, int maxAttempts, Duration retryDelay) {
        this.assistant = assistant;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
    }

    public Answer<TicketFacts> read(String ticketRef) {
        return askUntilUsable(ticketRef);
    }

    /**
     * A model's "no such item" is indistinguishable from a tool it never found, so a non-answer is asked again and
     * only the last is believed. Every attempt is paid for, so all are returned as one spend.
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
            log.atWarn().setMessage("ticket read unusable")
                    .addKeyValue("ref", ticketRef)
                    .addKeyValue("cause", answer.facts().isEmpty() ? "no answer" : "facts unusable")
                    .addKeyValue("attempt", attempt)
                    .addKeyValue("limit", maxAttempts)
                    .log();
            if (attempt == maxAttempts || System.nanoTime() > deadline || !pause()) {
                break;
            }
        }
        assistant.brokenMcpServers().filter(broken -> !broken.isEmpty()).ifPresent(broken ->
                log.atError().setMessage("mcp servers down")
                        .addKeyValue("ref", ticketRef)
                        .addKeyValue("servers", String.join(", ", broken))
                        .log());
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
