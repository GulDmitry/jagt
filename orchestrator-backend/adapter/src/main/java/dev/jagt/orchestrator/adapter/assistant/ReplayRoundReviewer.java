package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.replay.RecordedVerdicts;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
@RequiredArgsConstructor
public class ReplayRoundReviewer implements RoundReviewer {

    private final RecordedVerdicts verdicts;

    @Override
    public Answer<Judgement> review(Round round) {
        return new Answer<>(Optional.of(verdicts.answer(round.prompt())
                .orElse(Judgement.failed("the recording holds no verdict for this read"))), TokenUsage.NONE);
    }

    @Override
    public boolean loadsMcpServer() {
        return true;
    }
}
