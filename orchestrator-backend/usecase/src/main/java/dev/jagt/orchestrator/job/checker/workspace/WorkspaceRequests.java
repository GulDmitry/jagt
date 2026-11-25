package dev.jagt.orchestrator.job.checker.workspace;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.CodeHost;
import dev.jagt.orchestrator.port.codehost.RequestDraft;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.git.GitWorktrees;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * A role's folder inside a configured project, proposed as the one open request for the role's own
 * branch; the base branch is never written.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceRequests {

  private static final String PREFIX = "jagt/acceptance-";

  private final ConfigService configService;
  private final OrchestratorPaths paths;
  private final GitWorktrees worktrees;
  private final CodeHost host;

  /** Never throws: a request that cannot be made is logged and tried again by the next call. */
  public void propose(String role, Path folder, String taskId) {
    String branch = PREFIX + role;
    try {
      Optional<Map.Entry<String, ProjectConfig>> owner = owner(folder);
      if (owner.isEmpty()) {
        skipped(role, "no project of jagt.yml holds the folder");
      } else if (owner.get().getValue().gitRemote().local()) {
        skipped(role, "the project has remote none");
      } else {
        open(role, branch, owner.get(), folder, taskId);
      }
    } catch (Processes.Stopped stopped) {
      throw stopped;
    } catch (RuntimeException failed) {
      log.atWarn()
          .setMessage("workspace request failed")
          .addKeyValue("role", role)
          .addKeyValue("branch", branch)
          .addKeyValue("cause", Causes.of(failed))
          .addKeyValue("effect", "next check retries")
          .setCause(failed)
          .log();
    }
  }

  private void open(
      String role,
      String branch,
      Map.Entry<String, ProjectConfig> owner,
      Path folder,
      String taskId) {
    ProjectConfig project = owner.getValue();
    String task = "Acceptance " + role + " workspace after " + taskId;
    String remoteUrl = worktrees.remoteUrl(Path.of(project.path()));
    Answer<String> current = host.openRequestOf(owner.getKey(), remoteUrl, branch);
    if (!current.failure().isBlank()) {
      failed(role, branch, current.failure());
      return;
    }
    boolean fromBranch = current.facts().map(url -> !url.isBlank()).orElse(true);
    if (!worktrees.publishFolder(
        owner.getKey(),
        project,
        folder,
        branch,
        task,
        paths.layout().roleBranchWorktree(owner.getKey(), branch),
        fromBranch)) {
      return;
    }
    Answer<String> answer =
        host.openRequest(
            owner.getKey(),
            remoteUrl,
            new RequestDraft(
                branch,
                project.baseBranchName(),
                task,
                "Written by the acceptance role of jagt. Review before merging."));
    if (answer.facts().isPresent()) {
      log.atInfo()
          .setMessage("workspace request opened")
          .addKeyValue("role", role)
          .addKeyValue("branch", branch)
          .addKeyValue("url", answer.facts().get())
          .log();
    } else if (answer.failure().isBlank()) {
      skipped(role, "the host opens no request");
    } else {
      failed(role, branch, answer.failure());
    }
  }

  private void failed(String role, String branch, String cause) {
    log.atWarn()
        .setMessage("workspace request failed")
        .addKeyValue("role", role)
        .addKeyValue("branch", branch)
        .addKeyValue("cause", cause)
        .addKeyValue("effect", "next check retries")
        .log();
  }

  private Optional<Map.Entry<String, ProjectConfig>> owner(Path folder) {
    Path real = real(folder);
    return configService.load().projects().entrySet().stream()
        .filter(project ->
            real.startsWith(real(Path.of(project.getValue().path())))
                && !real.equals(real(Path.of(project.getValue().path()))))
        .findFirst();
  }

  private void skipped(String role, String cause) {
    log.atInfo()
        .setMessage("workspace request skipped")
        .addKeyValue("role", role)
        .addKeyValue("cause", cause)
        .log();
  }

  private static Path real(Path path) {
    try {
      return path.toRealPath();
    } catch (IOException e) {
      return path.toAbsolutePath().normalize();
    }
  }
}
