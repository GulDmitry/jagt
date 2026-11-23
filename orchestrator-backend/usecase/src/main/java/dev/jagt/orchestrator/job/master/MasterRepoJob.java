package dev.jagt.orchestrator.job.master;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.MasterRight;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** In act, the repository a session asked for is attached as the human's own press would. */
@Service
@RequiredArgsConstructor
public class MasterRepoJob implements Job {

  private final StateService stateService;
  private final ConfigService configService;
  private final MasterPress press;
  private final TaskTurns turns;
  private static final String PRESSED = "press:add-repo";

  @Override
  public String id() {
    return "master-repo";
  }

  @Override
  public Serves serves() {
    return Serves.ARBITER;
  }

  @Override
  public String describe() {
    return "attach the repository a session asked for, where the Master stands in for the human";
  }

  @Override
  public Duration every() {
    return Duration.ofSeconds(2);
  }

  @Override
  public void run() {
    if (!configService.load().arbiter().may(MasterRight.REPO)) {
      return;
    }
    stateService
        .tasks()
        .forEach((taskId, task) -> {
          String request = task.work().repoRequest();
          String pressed = task.work().turnOrNone().mark(PRESSED);
          if (request == null) {
            if (pressed != null) {
              turns.unmark(taskId, PRESSED);
            }
            return;
          }
          // Once per request, kept on the task: a refused press is not repeated, nor after a
          // restart.
          if (task.standing().status() == TaskStatus.DONE
              || task.work().stopped()
              || request.equals(pressed)) {
            return;
          }
          turns.mark(taskId, PRESSED, request);
          press.execute(taskId, TaskAction.ADD_REPO);
        });
  }
}
