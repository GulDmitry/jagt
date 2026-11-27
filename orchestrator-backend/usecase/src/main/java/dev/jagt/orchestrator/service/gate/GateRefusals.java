package dev.jagt.orchestrator.service.gate;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.plan.PlanFirst;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.repo.Remote;
import dev.jagt.orchestrator.task.stop.RefusalKind;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** The verdicts on a tool call, each refusal recorded on the task it refused. */
@Service
@Slf4j
@RequiredArgsConstructor
public class GateRefusals {

  private final StateService stateService;
  private final ToolGate toolGate;

  public Optional<String> shell(
      @Nullable String command, @Nullable String taskBranch, int port, Remote remote) {
    return toolGate
        .refusal(command, taskBranch, port, remote)
        .map(reason -> {
          stateService.updateTask(
              Objects.requireNonNull(taskBranch), task -> task.refused(RefusalKind.SESSION_LINE));
          log.atInfo().setMessage("session line refused").addKeyValue("task", taskBranch).log();
          return reason;
        });
  }

  public Optional<String> write(String taskId, TaskState owner, @Nullable String file) {
    return PlanFirst.refusal(owner, file)
        .map(reason -> {
          stateService.updateTask(taskId, task -> task.refused(RefusalKind.PLAN_FIRST));
          log.atInfo()
              .setMessage("write refused")
              .addKeyValue("task", taskId)
              .addKeyValue("cause", "plan not approved")
              .addKeyValue("file", file)
              .log();
          return reason;
        });
  }
}
