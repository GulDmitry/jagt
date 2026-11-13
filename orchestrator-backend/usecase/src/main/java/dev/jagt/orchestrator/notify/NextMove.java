package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.flow.Facts;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.service.HandBack;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NextMove {

  private final HandBack handBack;
  private final FlowRules rules;

  public Move of(TaskState task) {
    RoundState round = handBack.round(task);
    Facts projected = Facts.projected(task);
    return Move.forTask(
        rules,
        task.status(),
        projected.withDelivery(projected.delivery().withShippedWithoutRequest(round.requestless())),
        round,
        false);
  }
}
