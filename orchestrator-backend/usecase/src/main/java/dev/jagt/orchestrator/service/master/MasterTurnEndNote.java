package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.role.RoleTurnEndNote;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MasterTurnEndNote implements RoleTurnEndNote {

  private final ConfigService configService;

  @Override
  public Optional<String> noteFor(TaskState task) {
    if (!FlowRules.headedForTheArbiter(task.standing().status())
        || !configService.load().arbiter().running()) {
      return Optional.empty();
    }
    return Optional.of(
        FlowRules.awaitingVerification(task.standing().status())
            ? "→ verification runs first · the Master reads the round once it passes"
            : "→ with the Master for review · its verdict arrives here");
  }
}
