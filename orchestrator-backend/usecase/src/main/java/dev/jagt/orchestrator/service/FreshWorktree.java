package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.worktree.WorktreeContainers;
import dev.jagt.orchestrator.service.worktree.SubAgentBriefing;
import dev.jagt.orchestrator.task.launch.NewTask;
import dev.jagt.orchestrator.task.repo.NewRepo;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** What a worktree is given once cut: the briefing of its session, and the project's own setup. */
@Component
@RequiredArgsConstructor
@Slf4j
public class FreshWorktree {

  private final SubAgentBriefing briefing;
  private final Processes processes;
  private final WorktreeContainers containers;

  public void nameStack(Path worktree) {
    containers.writeStackName(worktree);
  }

  public SubAgentBriefing briefing() {
    return briefing;
  }

  /** The project's {@code setupCommand} in the worktree; a failure or the limit throws. */
  public void setUp(NewTask request, NewRepo repo) {
    List<String> template = repo.config().setupCommand();
    if (template.isEmpty()) {
      return;
    }
    List<String> command =
        template.stream()
            .map(part ->
                part.replace("{branch}", request.taskId())
                    .replace("{worktree}", repo.worktreePath().toString()))
            .toList();
    String said = failure(repo, command);
    if (said != null) {
      log.atWarn()
          .setMessage("worktree setup failed")
          .addKeyValue("project", repo.project())
          .addKeyValue("cmd", String.join(" ", command))
          .addKeyValue("cause", OneLine.clipped(said))
          .log();
      throw new SetupFailed("[" + repo.project() + "] setupCommand " + said);
    }
  }

  private @Nullable String failure(NewRepo repo, List<String> command) {
    try {
      Processes.Result result =
          processes.run(repo.worktreePath(), repo.config().setupTimeoutOrDefault(), command);
      return result.exitCode() == 0
          ? null
          : "exited "
              + result.exitCode()
              + "\n"
              + Verification.tail(result.stderr().isBlank() ? result.stdout() : result.stderr());
    } catch (IllegalStateException e) {
      return "did not finish: " + OneLine.clipped(e.getMessage());
    }
  }
}
