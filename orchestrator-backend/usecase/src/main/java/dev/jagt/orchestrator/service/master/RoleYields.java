package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.service.CodeReviewSettings;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Whether a brief row yields to the role its {@code unless} names: that role is not turned off,
 * every repository of the task has a code host, and a role that posts notes may post on the task's
 * own request.
 */
@Component
@RequiredArgsConstructor
public class RoleYields {

  private final ConfigService configService;
  private final CodeReviewSettings reviews;

  boolean yields(String unless, TaskState task) {
    if (unless.isBlank() || task.repos().isEmpty()) {
      return false;
    }
    ConfigFile file = configService.load();
    return !Boolean.FALSE.equals(file.role(unless).enabled())
        && task.repos().stream()
            .noneMatch(repo -> file.hasNoCodeHost(Objects.requireNonNull(repo.project())))
        && (!CodeReviewSettings.ROLE.equals(unless) || reviews.load().postOwn());
  }
}
