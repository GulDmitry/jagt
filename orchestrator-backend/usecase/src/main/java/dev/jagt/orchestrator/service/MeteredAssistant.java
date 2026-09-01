package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant;
import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The master's outside-reader with its meter attached: every read lands in the session total the moment it returns,
 * and the returned {@link Answer} still carries its cost for {@link #chargeTask} to attribute once a task exists.
 * INJECT THIS, never {@link MasterAssistant} directly — a direct injection spends money off the books.
 */
@Component
@RequiredArgsConstructor
public class MeteredAssistant {

    private final MasterAssistant assistant;
    private final UsageTracker usageTracker;

    public Answer<TicketFacts> readTicket(String ticketRef) {
        return metered(AssistantCallKind.TICKET_READ, assistant.readTicket(ticketRef));
    }

    public Answer<MergeRequestFacts> readMergeRequest(String mrUrl) {
        return metered(AssistantCallKind.MR_READ, assistant.readMergeRequest(mrUrl));
    }

    public Answer<ReviewFacts> readReview(String mrUrl) {
        return metered(AssistantCallKind.REVIEW_SWEEP, assistant.readReview(mrUrl));
    }

    public Answer<MasterAssistant.CommandProposal> mapCommand(String text, String context) {
        return metered(AssistantCallKind.COMMAND_MAP, assistant.mapCommand(text, context));
    }

    /** Free and token-less, so no meter: what the CLI itself says about the servers a read needs. */
    public Optional<List<String>> brokenMcpServers() {
        return assistant.brokenMcpServers();
    }

    public void chargeTask(String taskId, TokenUsage usage) {
        usageTracker.chargeTask(taskId, usage);
    }

    private <T> Answer<T> metered(AssistantCallKind kind, Answer<T> answer) {
        usageTracker.record(kind, answer.usage());
        return answer;
    }
}
