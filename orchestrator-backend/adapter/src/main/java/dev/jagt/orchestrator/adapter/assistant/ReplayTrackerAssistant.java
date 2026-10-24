package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.replay.RecordedReads;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.TrackerAssistant;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
@RequiredArgsConstructor
public class ReplayTrackerAssistant implements TrackerAssistant {

    private final RecordedReads reads;

    @Override
    public Answer<TicketFacts> readTicket(String ticketRef, List<String> corrections) {
        return answer(AssistantCallKind.TICKET_READ, ticketRef).map(HeadlessClaudeTrackerAssistant::ticket);
    }

    @Override
    public Answer<String> readTicketText(String ticketRef) {
        Answer<String> read = answer(AssistantCallKind.MASTER_REVIEW, ticketRef).map(HeadlessClaudeTrackerAssistant::text);
        return new Answer<>(read.facts().filter(text -> !text.isEmpty()), read.usage(), read.failure(), read.refused());
    }

    @Override
    public Answer<List<String>> findCandidates(String query) {
        return answer(AssistantCallKind.INTAKE, query).map(HeadlessClaudeTrackerAssistant::keys);
    }

    private Answer<JsonNode> answer(AssistantCallKind kind, String ref) {
        Optional<JsonNode> said = reads.answer(kind.name(), ref);
        return said.isEmpty() ? Answer.unavailable() : HeadlessClaude.reported(new Answer<>(said, TokenUsage.NONE), ref);
    }
}
