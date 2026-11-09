package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.service.KeptTicketTexts;
import dev.jagt.orchestrator.service.PlanBaseline;
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
