package dev.jagt.orchestrator.job.triage;

import dev.jagt.orchestrator.flow.capability.Watch;
import dev.jagt.orchestrator.flow.session.StopHold;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.job.TaskJob;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.stop.PhaseProfiles;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.stop.StopTriage;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** A stop is read once by the triage role, which writes the stop's hover and nothing else. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StopTriageJob implements TaskJob {

  private static final String ROLE = "triage";

  private final ConfigService configService;
  private final PhaseProfiles profiles;
  private final StopTriagePanel panel;
  private final TaskStops stops;

  @Override
  public String id() {
    return "stop-triage";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "read a stop once, and say on its hover who looped and whether a lift looks safe";
  }

  @Override
  public Duration every() {
    return Duration.ofSeconds(5);
  }

  @Override
  public Watch watch() {
    return Watch.defaults()
        .withStops(
            StopKind.LIFT,
            StopKind.HOST_READ,
            StopKind.SESSION_ALIVE,
            StopKind.SESSION_REPORT,
            StopKind.DEPLOY_READ);
  }

  @Override
  public void run(String taskId, TaskState task) {
    TaskStop stop = task.work().stop();
    if (stop == null || stop.triage() != null) {
      return;
    }
    ConfigFile.RoleConfig role = configService.load().role(ROLE);
    if (!role.enabledOrDefault() || StopHold.of(task, profiles.at(task)) == StopHold.ARBITER) {
      return;
    }
    StopTriage read;
    try {
      read = panel.read(taskId, task, StopTriagePanel.Trail.of(task), role.modelOrInherited());
    } catch (StopTriageFailed failed) {
      stops.triaged(taskId, stop.key(), StopTriage.failed(failed.why()));
      log.atWarn()
          .setMessage("stop triage failed")
          .addKeyValue("task", taskId)
          .addKeyValue("key", stop.key())
          .addKeyValue("cause", failed.why())
          .addKeyValue("effect", "the stop is not read again")
          .log();
      return;
    }
    stops.triaged(taskId, stop.key(), read);
    log.atInfo()
        .setMessage("stop triaged")
        .addKeyValue("task", taskId)
        .addKeyValue("role", ROLE)
        .addKeyValue("key", stop.key())
        .addKeyValue("outcome", read.who().label())
        .log();
  }
}
