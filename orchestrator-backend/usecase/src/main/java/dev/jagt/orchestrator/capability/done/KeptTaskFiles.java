package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.service.assistant.KeptTicketTexts;
import dev.jagt.orchestrator.service.plan.PlanBaseline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KeptTaskFiles {

  private final KeptTicketTexts tickets;
  private final PlanBaseline baseline;

  public void forget(String taskId) {
    tickets.forget(taskId);
    baseline.forget(taskId);
  }
}
