package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.protocol.RetryPolicy;
import dev.jagt.orchestrator.protocol.TicketRead;
import dev.jagt.orchestrator.protocol.Violation;
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

    private final MeteredAssistant assistant;
    private final RetryPolicy policy;

    @Autowired
    public TicketReader(MeteredAssistant assistant) {
        this(assistant, RetryPolicy.PAID_READ);
    }

    TicketReader(MeteredAssistant assistant, RetryPolicy policy) {
        this.assistant = assistant;
        this.policy = policy;
    }

    public Answer<TicketFacts> read(String ticketRef) {
        return askUntilUsable(ticketRef);
    }

    /**
     * A model's "no such item" is indistinguishable from a tool it never found, so a non-answer is asked again —
     * carrying what was wrong with the last one, or the same question returns the same answer. Every attempt is
     * paid for, so all are returned as one spend, and an exhausted policy answers with no facts: the caller's job
     * is then to reach a human, never to guess.
     */
    private Answer<TicketFacts> askUntilUsable(String ticketRef) {
        long deadline = System.nanoTime() + policy.budget().toNanos();
        Answer<TicketFacts> answer = Answer.unavailable();
        TokenUsage spent = TokenUsage.NONE;
        List<String> corrections = List.of();
        for (int attempt = 1; attempt <= policy.attempts(); attempt++) {
            answer = assistant.readTicket(ticketRef, corrections);
            spent = spent.plus(answer.usage());
            if (answer.facts().filter(TicketFacts::usable).isPresent()) {
                return new Answer<>(answer.facts(), spent);
            }
            corrections = answer.facts()
                    .map(facts -> TicketRead.violations(ticketRef, facts).stream()
                            .map(Violation::toString).toList())
                    .orElse(List.of());
            log.atWarn().setMessage("ticket read unusable")
                    .addKeyValue("ref", ticketRef)
                    .addKeyValue("cause", answer.facts().isEmpty() ? "no answer" : String.join("; ", corrections))
                    .addKeyValue("attempt", attempt)
                    .addKeyValue("limit", policy.attempts())
                    .log();
            if (policy.lastAttempt(attempt) || System.nanoTime() > deadline || !pause()) {
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
            Thread.sleep(policy.between());
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
