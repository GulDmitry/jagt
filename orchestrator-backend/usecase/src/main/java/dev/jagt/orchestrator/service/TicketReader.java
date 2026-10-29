package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.McpHealth;
import dev.jagt.orchestrator.port.TrackerAssistant;
import dev.jagt.orchestrator.protocol.RetryPolicy;
import dev.jagt.orchestrator.protocol.TicketRead;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Where a ticket's facts come from: the headless assistant, following the reference into whatever tracker
 * holds it. The cost is RETURNED rather than charged, the read being what produces the key the task is named by.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TicketReader {

    private final TrackerAssistant assistant;
    private final McpHealth mcp;
    private final UsageTracker usage;
    private final RetryPolicy policy;

    /** An exhausted policy answers with no usable facts: the caller's job is then to reach a human, never to guess. */
    public Answer<TicketFacts> read(String ticketRef) {
        Answer<TicketFacts> answer = PaidRead.untilUsable(policy, ticketRef,
                corrections -> assistant.readTicket(ticketRef, corrections),
                facts -> TicketRead.violations(ticketRef, facts), TicketFacts::usable);
        if (answer.facts().filter(TicketFacts::usable).isPresent()) {
            return answer;
        }
        Optional<List<String>> broken = mcp.brokenServers();
        broken.filter(down -> !down.isEmpty()).ifPresent(down ->
                log.atError().setMessage("mcp servers down")
                        .addKeyValue("ref", ticketRef)
                        .addKeyValue("servers", String.join(", ", down))
                        .log());
        if (answer.facts().isEmpty() && answer.failure().isBlank()) {
            return Answer.failed(answer.usage(), mcpSentence(broken));
        }
        return answer;
    }

    private static String mcpSentence(Optional<List<String>> broken) {
        String fix = "; see docs/installation.md#mcp-access-comes-first";
        return broken.map(down -> down.isEmpty() ? "no MCP server is down, so the tracker's tool answered nothing"
                : "MCP server down: " + String.join(", ", down)).orElse("MCP health could not be established") + fix;
    }

    public void charge(String taskId, TokenUsage usage) {
        this.usage.chargeTask(taskId, usage);
    }
}
