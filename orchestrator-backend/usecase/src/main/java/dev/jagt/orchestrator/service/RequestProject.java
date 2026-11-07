package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.GitRemote;
import dev.jagt.orchestrator.task.ProjectConfig;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Which configured project a review request belongs to: the one whose remote names its repository
 * path, the host breaking a tie.
 */
@Component
@RequiredArgsConstructor
public class RequestProject {

  private final ConfigService configService;
  private final GitWorktrees gitWorktrees;

  public String of(String mrUrl) {
    Map<String, String> owners = new LinkedHashMap<>();
    configService
        .load()
        .projects()
        .forEach(
            (name, project) -> {
              String remote = remoteOf(project);
              if (GitRemote.holds(remote, mrUrl)) {
                owners.put(name, remote);
              }
            });
    return owners.entrySet().stream()
        .filter(owner -> owners.size() == 1 || GitRemote.onHostOf(owner.getValue(), mrUrl))
        .findFirst()
        .or(() -> owners.entrySet().stream().findFirst())
        .map(Map.Entry::getKey)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "no configured project matches request url: " + mrUrl));
  }

  private @Nullable String remoteOf(ProjectConfig project) {
    try {
      return gitWorktrees.remoteUrl(Path.of(project.path()));
    } catch (IllegalStateException noRemote) {
      return null;
    }
  }
}
