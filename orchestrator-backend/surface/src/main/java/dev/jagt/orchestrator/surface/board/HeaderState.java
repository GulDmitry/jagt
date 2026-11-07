package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.port.AssistantHealth;
import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What the header says beside the tasks: the unattended jobs, the assistant and the reach jagt
 * lost.
 */
@Component
@RequiredArgsConstructor
class HeaderState {

  private final Jobs jobs;
  private final AssistantHealth assistant;
  private final Reach reach;
  private final Clock clock;

  Jobs.Summary summary() {
    return jobs.summary(clock.millis());
  }

  List<Jobs.Status> statuses() {
    return jobs.statuses(clock.millis());
  }

  List<ReachLost> reachLost() {
    return reach.standing();
  }

  Optional<AssistantHealth.Outage> assistantOutage() {
    return assistant.outage();
  }
}
