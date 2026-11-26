package dev.jagt.orchestrator.job.master;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.capability.Watch;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.job.TaskJob;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.master.MasterRight;
import dev.jagt.orchestrator.task.turn.Turn;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** In act, a request green with every thread closed is deployed as the human's own press would. */
@Service
@RequiredArgsConstructor
public class MasterDeployJob implements TaskJob {

  private final ConfigService configService;
  private final MasterPress press;
  private final TaskTurns turns;

  @Override
  public String id() {
    return "master-deploy";
  }

  @Override
  public Serves serves() {
    return Serves.ARBITER;
  }

  @Override
  public String describe() {
    return "deploy each task whose request is green with every thread closed";
  }

  @Override
  public Duration every() {
    return Duration.ofSeconds(20);
  }

  @Override
  public Watch watch() {
    return Watch.defaults().withStatuses(FlowRules::deployedByTheArbiter);
  }

  @Override
  public void run(String taskId, TaskState task) {
    ConfigFile config = configService.load();
    Arbiter.Profile profile = config.arbiter().profile();
    if (!FlowRules.deployedByTheArbiter(
        task.standing().status(), config.deploy().requireApprovalOrDefault())) {
      return;
    }
    String arrival = String.valueOf(task.standing().statusSince());
    // Once per arrival, kept on the task: a refused deploy is not pressed again, nor after a
    // restart.
    if (!profile.may(MasterRight.DEPLOY)
        || !Facts.checksGreen(task)
        || !turns.next(task, profile).equals(Turn.Holder.ARBITER)
        || task.work().arbiterOrNone().pressedDeploy().equals(arrival)) {
      return;
    }
    turns.pressedDeploy(taskId, arrival);
    press.press(taskId, task, TaskAction.DEPLOY);
  }
}
