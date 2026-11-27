package dev.jagt.orchestrator.job.checker.wiring;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.job.checker.CheckerPrompts;
import dev.jagt.orchestrator.job.checker.CheckerRecords;
import dev.jagt.orchestrator.service.assistant.TicketTexts;
import dev.jagt.orchestrator.service.role.RoleKit;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What each checker keeps beside {@code state.json}: its results and the ticket text it reads. */
@Component
@RequiredArgsConstructor
public class CheckerLedgers {

  private final OrchestratorPaths paths;
  private final Clock clock;
  private final TicketTexts tickets;

  public CheckerRecords records(String role) {
    return new CheckerRecords(paths, clock, role);
  }

  public CheckerPrompts prompts(RoleKit kit) {
    return new CheckerPrompts(kit.definition(), kit.files(), tickets, clock);
  }
}
