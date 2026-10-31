package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.QaReader;
import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
@RequiredArgsConstructor
public class ReplayQaReader implements QaReader {

    @Override
    public Answer<QaVerdict> read(Run run) {
        return new Answer<>(Optional.of(QaVerdict.failed(run.sha(), "the recording holds no QA check")), TokenUsage.NONE);
    }

    @Override
    public Answer<QaDecline> reconsider(Run run) {
        return new Answer<>(Optional.of(QaDecline.failed(run.sha(), "the recording holds no QA check")), TokenUsage.NONE);
    }
}
