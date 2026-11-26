package dev.jagt.orchestrator.service.assistant;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Round;
import dev.jagt.orchestrator.protocol.read.RetryPolicy;
import dev.jagt.orchestrator.protocol.read.RoundRead;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A round read whose answer is judged: a word outside the vocabulary is asked again once, told what
 * it got wrong, and ends as a failed judgement naming it.
 */
@Component
@RequiredArgsConstructor
public class RoundReader {

  private final Assistants assistants;

  public Answer<Judgement> read(Round round) {
    return PaidRead.untilWithoutViolations(
            RetryPolicy.ROUND_READ,
            "round",
            corrections -> assistants.reviewer().review(round.correcting(corrections)),
            RoundRead::violations)
        .map(RoundRead::closed);
  }

  public boolean loadsMcpServer() {
    return assistants.reviewer().loadsMcpServer();
  }
}
