package dev.jagt.orchestrator.adapter.worktree;

import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.reach.SessionHold;
import dev.jagt.orchestrator.service.reach.SessionVet;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.repo.ProjectConfig.CredentialCheck;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Runs each project's declared credential checks and reads their exit code alone: a passing check
 * is read again daily, a failing one at every call, so a renewed credential clears its mark at the
 * next read.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CredentialChecks implements SessionHold, SessionVet {

  static final String DRIVER = "credential";

  private static final Duration TIMEOUT = Duration.ofMinutes(1);
  private static final Duration PASSING_EVERY = Duration.ofDays(1);

  private final ConfigService configService;
  private final Processes processes;
  private final Reach reach;
  private final Clock clock;

  private final Map<String, Long> passedAt = new ConcurrentHashMap<>();

  public void runDue() {
    configService
        .load()
        .projects()
        .forEach((name, project) ->
            project.credentialChecks().stream()
                .filter(check -> !check.command().isEmpty() && due(name, check))
                .forEach(check -> read(name, project, check)));
  }

  /** Runs the task's projects' checks now; the first failure's line, null where all pass. */
  @Override
  public @Nullable String vet(TaskState task) {
    String first = null;
    for (String name : task.projects()) {
      ProjectConfig project = configService.load().projects().get(name);
      if (project == null) {
        continue;
      }
      for (CredentialCheck check : project.credentialChecks()) {
        String said = check.command().isEmpty() ? null : read(name, project, check);
        first = first == null ? said : first;
      }
    }
    return first;
  }

  @Override
  public @Nullable String held(TaskState task) {
    if (SessionPhase.of(task.work().session()).live()) {
      return null;
    }
    List<String> projects = task.projects();
    return reach.standing().stream()
        .filter(lost -> DRIVER.equals(lost.driver()))
        .filter(lost -> projects.stream().anyMatch(name -> lost.host().startsWith(name + "/")))
        .map(ReachLost::said)
        .findFirst()
        .orElse(null);
  }

  private boolean due(String project, CredentialCheck check) {
    Long passed = passedAt.get(key(project, check));
    return passed == null || clock.millis() - passed >= PASSING_EVERY.toMillis();
  }

  private @Nullable String read(String name, ProjectConfig project, CredentialCheck check) {
    String host = key(name, check);
    String cause = failure(project, check);
    if (cause == null) {
      passedAt.put(host, clock.millis());
      reach.reached(DRIVER, host);
      return null;
    }
    passedAt.remove(host);
    String said =
        "project '"
            + name
            + "': credential check '"
            + check.name()
            + "' fails ("
            + cause
            + "); renew it: "
            + check.renewedBy();
    reach.lost(new ReachLost(DRIVER, host, List.of(), said), () -> declared(name, check));
    return said;
  }

  private @Nullable String failure(ProjectConfig project, CredentialCheck check) {
    Path folder = Path.of(project.path());
    try {
      Processes.Result result =
          processes.run(Files.isDirectory(folder) ? folder : null, TIMEOUT, check.command());
      return result.exitCode() == 0 ? null : "exit " + result.exitCode();
    } catch (Processes.Stopped stopped) {
      throw stopped;
    } catch (RuntimeException failed) {
      log.atWarn()
          .setMessage("credential check did not run")
          .addKeyValue("check", check.name())
          .addKeyValue("cause", Causes.of(failed))
          .addKeyValue("effect", "its project's sessions do not start")
          .log();
      return "did not finish";
    }
  }

  private boolean declared(String project, CredentialCheck check) {
    ProjectConfig held = configService.load().projects().get(project);
    return held != null && held.credentialChecks().contains(check);
  }

  private static String key(String project, CredentialCheck check) {
    return project + "/" + check.name();
  }
}
