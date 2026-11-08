package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.WorktreeContainers;
import dev.jagt.orchestrator.service.OneLine;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    if (listed.exitCode() != 0) {
      return;
    }
    for (String project : projectsUnder(listed.stdout(), roots(worktree))) {
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
