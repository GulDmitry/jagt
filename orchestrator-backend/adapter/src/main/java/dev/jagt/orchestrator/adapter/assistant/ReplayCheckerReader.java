package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.replay.RecordedReads;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.task.AcceptanceDecline;
import dev.jagt.orchestrator.task.AcceptanceVerdict;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
@RequiredArgsConstructor
public class ReplayCheckerReader implements CheckerReader {

  private static final JsonMapper JSON = new JsonMapper();

  private final RecordedReads reads;

  @Override
  public Answer<AcceptanceVerdict> read(Run run) {
    Optional<JsonNode> said = reads.answer(AssistantCallKind.ACCEPTANCE_CHECK.name(), run.sha());
    if (said.isEmpty()) {
      return new Answer<>(
          Optional.of(
              AcceptanceVerdict.failed(run.sha(), "the recording holds no acceptance check")),
          TokenUsage.NONE);
    }
    var envelope = JSON.createObjectNode();
    envelope.set("structured_output", said.get());
    return CheckerAnswers.verdictOf(run, new HeadlessReply(envelope, TokenUsage.NONE, ""));
  }

  @Override
  public Answer<AcceptanceDecline> reconsider(Run run) {
    return new Answer<>(
        Optional.of(AcceptanceDecline.failed(run.sha(), "the recording holds no acceptance check")),
        TokenUsage.NONE);
  }
}
