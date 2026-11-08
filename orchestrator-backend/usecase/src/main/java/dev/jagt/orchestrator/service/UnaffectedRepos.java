package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.protocol.PlanRepositories;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Takes the repositories an approved plan marks unaffected out of the task, those holding own
 * commits excepted.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnaffectedRepos {

  private final StateService stateService;
  private final TaskArtifacts artifacts;
  private final ConfigService configService;
  private final GitWorktrees gitWorktrees;
  private final RepoDeparture departure;

  public void leave(String taskId) {
    TaskState task = stateService.task(taskId).orElse(null);
    if (task == null) {
      return;
    }
    for (String project : unaffected(taskId, task.projects())) {
      if (task.canLose(project)) {
        leaveUnlessOwnCommits(taskId, project);
      }
    }
  }

  private List<String> unaffected(String taskId, List<String> projects) {
    try {
      return PlanRepositories.unaffectedIn(
          artifacts.read(taskId, Artifact.PLAN.fileName()), projects);
    } catch (TaskArtifacts.ArtifactAbsent absent) {
      return List.of();
    }
  }

  private void leaveUnlessOwnCommits(String taskId, String project) {
    TaskState task = stateService.task(taskId).orElse(null);
    TaskRepo repo =
        task == null
            ? null
            : task.repos().stream()
                .filter(one -> project.equals(one.project()))
                .findFirst()
                .orElse(null);
    if (repo == null) {
      return;
    }
    ProjectConfig config = configService.project(project);
    if (gitWorktrees.holdsOwnCommits(
        Path.of(config.path()).toAbsolutePath().normalize(), taskId, config.baseBranch())) {
      log.atInfo()
          .setMessage("unaffected repository stays")
          .addKeyValue("task", taskId)
          .addKeyValue("project", project)
          .addKeyValue("cause", "its task branch holds own commits")
          .log();
      return;
    }
    departure.leave(taskId, repo, "the approved plan marks it unaffected");
  }
}
