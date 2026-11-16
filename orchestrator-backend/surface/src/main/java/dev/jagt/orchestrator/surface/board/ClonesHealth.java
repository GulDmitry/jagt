package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.job.ProjectClonesJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("clones")
@RequiredArgsConstructor
class ClonesHealth implements HealthIndicator {

  private final ProjectClonesJob clones;

  @Override
  public Health health() {
    return clones.cloning() ? Health.outOfService().build() : Health.up().build();
  }
}
