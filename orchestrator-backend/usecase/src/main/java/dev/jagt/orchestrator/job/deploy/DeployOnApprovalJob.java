package dev.jagt.orchestrator.job.deploy;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.Watch;
import dev.jagt.orchestrator.job.TaskJob;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** A request the code host approved is deployed once, through the door a human's press uses. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeployOnApprovalJob implements TaskJob {

  private final ConfigService configService;
  private final CommandService commands;
  private final StateService stateService;

  /** A refused deploy is not pressed again for the same arrival. */
  private final Map<String, Long> pressed = new ConcurrentHashMap<>();

  @Override
  public String id() {
    return "deploy-on-approval";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "deploy each task whose request the code host approved";
  }

  @Override
  public Duration every() {
    return Duration.ofSeconds(20);
  }

  @Override
  public Watch watch() {
    return Watch.defaults().withStatuses(TaskStatus.APPROVED);
  }

  @Override
  public void run(String taskId, TaskState task) {
    boolean stillApproved =
        stateService
            .task(taskId)
            .map(TaskState::status)
            .filter(TaskStatus.APPROVED::equals)
            .isPresent();
    if (!configService.load().deploy().onApprovalOrDefault()
        || !stillApproved
        || Long.valueOf(task.statusSince()).equals(pressed.put(taskId, task.statusSince()))) {
      return;
    }
    try {
      OriginContext.as(ActionOrigin.CODE_HOST, () -> commands.execute(taskId, TaskAction.DEPLOY));
    } catch (IllegalArgumentException | IllegalStateException refused) {
      log.atWarn()
          .setMessage("deploy on approval refused")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", Causes.of(refused))
          .log();
    }
  }
}
