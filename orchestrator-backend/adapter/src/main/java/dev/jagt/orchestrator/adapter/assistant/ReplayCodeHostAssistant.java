package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.replay.RecordedReads;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CodeHostAssistant;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
@RequiredArgsConstructor
public class ReplayCodeHostAssistant implements CodeHostAssistant {

    private final RecordedReads reads;

    @Override
    public Answer<MergeRequestFacts> readMergeRequest(String mrUrl) {
        return answer(AssistantCallKind.MR_READ, mrUrl).map(HeadlessClaudeCodeHostAssistant::request);
    }

    @Override
    public Answer<ReviewFacts> readReview(String mrUrl) {
        return answer(AssistantCallKind.REVIEW_SWEEP, mrUrl).map(HeadlessClaudeCodeHostAssistant::review);
    }

    private Answer<JsonNode> answer(AssistantCallKind kind, String ref) {
        Optional<JsonNode> said = reads.answer(kind.name(), ref);
        return said.isEmpty() ? Answer.unavailable() : HeadlessClaude.reported(new Answer<>(said, TokenUsage.NONE), ref);
    }
}
