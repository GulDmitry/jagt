package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.protocol.RetryPolicy;
import dev.jagt.orchestrator.protocol.TicketRead;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    /** An exhausted policy answers with no usable facts: the caller's job is then to reach a human, never to guess. */
    public Answer<TicketFacts> read(String ticketRef) {
        Answer<TicketFacts> answer = PaidRead.untilUsable(policy, ticketRef,
                corrections -> assistant.readTicket(ticketRef, corrections),
                facts -> TicketRead.violations(ticketRef, facts), TicketFacts::usable);
        if (answer.facts().filter(TicketFacts::usable).isEmpty()) {
            assistant.brokenMcpServers().filter(broken -> !broken.isEmpty()).ifPresent(broken ->
                    log.atError().setMessage("mcp servers down")
                            .addKeyValue("ref", ticketRef)
                            .addKeyValue("servers", String.join(", ", broken))
                            .log());
        }
        return answer;
    }

    public void charge(String taskId, TokenUsage usage) {
        assistant.chargeTask(taskId, usage);
    }
}
