package dev.jagt.orchestrator.startup.role;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.role.RoleFiles;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import dev.jagt.orchestrator.service.CodeReviewSettings;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The code reviewer is on unless turned off, so its brief must be readable while it runs. */
@Component
@RequiredArgsConstructor
public class CodeReviewerCheck implements StartupCheck {

  private final CodeReviewSettings settings;
  private final OrchestratorPaths paths;

  @Override
  public List<Problem> problems() {
    if (!settings.load().enabled()) {
      return List.of();
    }
    return new RoleFiles(paths, "code-reviewer").unreadable();
  }

  @Override
  public boolean machine() {
    return false;
  }
}
