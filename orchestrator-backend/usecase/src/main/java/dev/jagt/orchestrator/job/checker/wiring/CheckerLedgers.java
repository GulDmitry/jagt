package dev.jagt.orchestrator.job.checker.wiring;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.job.checker.CheckerRecords;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Where each checker keeps what its checks ended in, beside {@code state.json}. */
@Component
@RequiredArgsConstructor
public class CheckerLedgers {

  private final OrchestratorPaths paths;
  private final Clock clock;

  public CheckerRecords records(String role) {
    return new CheckerRecords(paths, clock, role);
  }
}
