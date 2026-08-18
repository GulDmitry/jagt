package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant;
import dev.jagt.orchestrator.assistant.MasterAssistant.Answer;
import dev.jagt.orchestrator.model.MergeRequestFacts;
import dev.jagt.orchestrator.model.ReviewFacts;
import dev.jagt.orchestrator.model.TicketFacts;
import dev.jagt.orchestrator.model.AssistantCallKind;
import dev.jagt.orchestrator.model.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The master's outside-reader with its meter attached: every read lands in the session total the moment it
 * returns, so a paid call can never go uncounted, and the returned {@link Answer} still carries its cost for
 * {@link #chargeTask} to attribute later.
 *
 * <p>Why the two steps are separate: for {@code do}/{@code resume} the read is what PRODUCES the id the task
 * is created under, so there is nothing to charge until the task exists. Attributing it any earlier writes to
 * a task that is not in state.json yet and the number is silently lost.
 *
 * <p>INJECT THIS, never {@link MasterAssistant} directly: a direct injection compiles and wires fine and
 * simply spends money off the books.
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

    /** Attributes an already-recorded read to a task, once that task is in state.json. */
    public void chargeTask(String taskId, TokenUsage usage) {
        usageTracker.chargeTask(taskId, usage);
    }

    private <T> Answer<T> metered(AssistantCallKind kind, Answer<T> answer) {
        usageTracker.record(kind, answer.usage());
        return answer;
    }
}
