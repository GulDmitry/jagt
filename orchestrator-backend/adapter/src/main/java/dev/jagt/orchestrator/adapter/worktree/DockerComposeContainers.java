package dev.jagt.orchestrator.adapter.worktree;

import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.worktree.WorktreeContainers;
import dev.jagt.orchestrator.service.OneLine;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(
    prefix = "orchestrator",
    name = "containers",
    havingValue = "docker-compose",
    matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DockerComposeContainers implements WorktreeContainers {

  private static final Duration LIST_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration DOWN_TIMEOUT = Duration.ofMinutes(3);
  private static final JsonMapper MAPPER = new JsonMapper();

  private final Processes processRunner;

  @Override
  public Map<String, String> environment(Path worktree) {
    return Map.of("COMPOSE_PROJECT_NAME", projectName(worktree));
  }

  /**
   * {@code <repository>-<task>} of {@code .../<task>/worktrees/<repository>}, as compose allows.
   */
  static String projectName(Path worktree) {
    Path folder = worktree.toAbsolutePath().normalize();
    Path parent = folder.getParent();
    Path owner =
        parent != null && "worktrees".equals(String.valueOf(parent.getFileName()))
            ? parent.getParent()
            : parent;
    String task = owner == null ? "" : String.valueOf(owner.getFileName());
    return (folder.getFileName() + "-" + task)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9_-]+", "-")
        .replaceAll("^[^a-z0-9]+", "");
  }

  @Override
  public void stop(Path worktree) {
    try {
      stopOrThrow(worktree);
    } catch (RuntimeException e) {
      log.atWarn()
          .setMessage("worktree containers stop failed")
          .addKeyValue("path", worktree)
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "removed anyway")
          .log();
    }
  }

  private void stopOrThrow(Path worktree) {
    String docker = Executables.resolve("docker");
    Processes.Result listed;
    try {
      listed =
          processRunner.run(
              null, LIST_TIMEOUT, List.of(docker, "compose", "ls", "--all", "--format", "json"));
    } catch (IllegalStateException e) {
      if (e.getCause() instanceof IOException) {
        return;
      }
      throw e;
    }
    Set<String> projects = new LinkedHashSet<>(List.of(projectName(worktree)));
    if (listed.exitCode() == 0) {
      projects.addAll(projectsUnder(listed.stdout(), roots(worktree)));
    }
    for (String project : projects) {
      var down =
          processRunner.run(
              null,
              DOWN_TIMEOUT,
              List.of(docker, "compose", "-p", project, "down", "-v", "--remove-orphans"));
      report(worktree, project, down);
    }
  }

  private static void report(Path worktree, String project, Processes.Result down) {
    if (down.exitCode() == 0) {
      log.atInfo()
          .setMessage("worktree containers stopped")
          .addKeyValue("path", worktree)
          .addKeyValue("project", project)
          .addKeyValue("exit", 0)
          .log();
      return;
    }
    log.atWarn()
        .setMessage("worktree containers stop failed")
        .addKeyValue("path", worktree)
        .addKeyValue("project", project)
        .addKeyValue("exit", down.exitCode())
        .addKeyValue("cause", OneLine.clipped(down.stderr()))
        .addKeyValue("effect", "removed anyway")
        .log();
  }

  /** A directory already gone is matched by its plain absolute path. */
  private static List<Path> roots(Path worktree) {
    Path absolute = worktree.toAbsolutePath().normalize();
    try {
      return List.of(worktree.toRealPath(), absolute);
    } catch (IOException e) {
      return List.of(absolute);
    }
  }

  /** A project belongs to the worktree when its first compose file does. */
  static List<String> projectsUnder(String composeLsJson, List<Path> roots) {
    List<String> projects = new ArrayList<>();
    for (JsonNode project : MAPPER.readTree(composeLsJson)) {
      String first = project.path("ConfigFiles").asString("").split(",", 2)[0].trim();
      if (first.isEmpty()) {
        continue;
      }
      Path file = Path.of(first).toAbsolutePath().normalize();
      if (roots.stream().anyMatch(file::startsWith)) {
        projects.add(project.path("Name").asString(""));
      }
    }
    return projects;
  }
}
