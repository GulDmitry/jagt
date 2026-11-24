package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.role.HeaderChips;
import dev.jagt.orchestrator.service.role.HeaderChips.HeaderChip;
import dev.jagt.orchestrator.service.role.model.ModelRoles;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What the header says beside the tasks: the unattended jobs, the reach jagt lost and the marks
 * roles add.
 */
@Component
@RequiredArgsConstructor
class HeaderState {

  private final Jobs jobs;
  private final Reach reach;
  private final Clock clock;
  private final List<HeaderChips> chips;
  private final ModelRoles roles;

  Jobs.Summary summary() {
    return jobs.summary(clock.millis());
  }

  List<Jobs.Status> statuses() {
    return jobs.statuses(clock.millis());
  }

  List<HeaderChip> chips() {
    return chips.stream().flatMap(source -> source.chips().stream()).toList();
  }

  List<String> roleLogs() {
    return roles.running();
  }

  List<ReachLost> reachLost() {
    return reach.standing();
  }
}
