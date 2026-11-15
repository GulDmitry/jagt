package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

/**
 * Clones every project named by url whose folder is missing: at start before the checks run, and
 * again each minute for a clone that failed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectClonesJob implements Job {

  private static final String DRIVER = "clone";
  private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(30);

  private final ConfigService configService;
  private final Processes processes;
  private final Reach reach;

  @Override
  public String id() {
    return "project-clones";
  }

  @Override
  public String describe() {
    return "clones a project named by url whose folder under the data folder is missing";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(1);
  }

  @EventListener(ApplicationStartedEvent.class)
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public void cloneAtStart() {
    run();
  }

  @Override
  public synchronized void run() {
    Map<String, ProjectConfig> projects;
    try {
      projects = configService.load().projects();
    } catch (RuntimeException unreadable) {
      return;
    }
    projects.forEach((key, project) -> {
      Path folder = Path.of(project.path());
      if (project.hasUrl() && !Files.exists(folder)) {
        clone(key, String.valueOf(project.url()), folder);
      }
    });
  }

  private void clone(String key, String url, Path folder) {
    String cause = cloneFailure(url, folder);
    if (cause == null) {
      reach.reached(DRIVER, key);
      log.atInfo().setMessage("project cloned").addKeyValue("project", key).log();
      return;
    }
    discard(folder);
    reach.lost(new ReachLost(DRIVER, key, List.of(), cause), () -> !Files.exists(folder));
  }

  private @Nullable String cloneFailure(String url, Path folder) {
    try {
      Files.createDirectories(folder.getParent());
      Processes.Result result =
          processes.run(
              folder.getParent(),
              CLONE_TIMEOUT,
              Map.of("GIT_TERMINAL_PROMPT", "0"),
              List.of("git", "clone", url, folder.toString()));
      if (result.exitCode() == 0) {
        return null;
      }
      String said = OneLine.clipped(result.stderr().isBlank() ? result.stdout() : result.stderr());
      return said == null || said.isBlank() ? "git clone exit " + result.exitCode() : said;
    } catch (IOException | RuntimeException failed) {
      return Causes.of(failed);
    }
  }

  private static void discard(Path folder) {
    try {
      FileSystemUtils.deleteRecursively(folder);
    } catch (IOException e) {
      log.atWarn()
          .setMessage("partial clone kept")
          .addKeyValue("path", folder)
          .addKeyValue("cause", Causes.of(e))
          .addKeyValue("effect", "the next run cannot clone there")
          .log();
    }
  }
}
