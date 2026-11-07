package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.port.ToolGateEnvelope;
import dev.jagt.orchestrator.port.ToolGateEnvelope.Kind;
import dev.jagt.orchestrator.protocol.ToolCall;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.KeepAlive;
import dev.jagt.orchestrator.service.PlanFirst;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.ToolGate;
import dev.jagt.orchestrator.task.Guard;
import dev.jagt.orchestrator.task.Remote;
import dev.jagt.orchestrator.task.TaskState;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * A gate that failed closed would make a stopped backend look like a rule, so anything not refused
 * answers nothing.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class AgentToolGateController {

  private final StateService stateService;
  private final Clock clock;
  private final ConfigService configService;
  private final ToolGateEnvelope envelope;
  private final ToolGate toolGate;

  /** Whatever comes back here is printed into the session, so an allowed call answers no body. */
  @PostMapping(value = "/api/agent/tool", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> gate(
      @RequestHeader(value = "X-Working-Directory", required = false) @Nullable String cwd,
      @RequestBody(required = false) @Nullable ToolCall call,
      HttpServletRequest request) {
    String tool = call == null ? null : call.toolName();
    if (call == null || tool == null) {
      return ResponseEntity.noContent().build();
    }
    stateService
        .findByWorktree(cwd)
        .ifPresent(
            task -> KeepAlive.beat(stateService, task, new SessionSign.ToolUsed(), clock.millis()));
    Kind kind = envelope.kindOf(tool);
    if (kind == Kind.FILE_WRITE) {
      return planFirst(cwd, call);
    }
    if (kind != Kind.SHELL) {
      return ResponseEntity.noContent().build();
    }
    // A directory no task owns has no branch a push could be refused against.
    String taskBranch = stateService.findByWorktree(cwd).map(Map.Entry::getKey).orElse(null);
    Remote remote =
        stateService
            .findByWorktree(cwd)
            .map(owner -> remoteOf(owner.getValue()))
            .orElse(Remote.ORIGIN);
    return toolGate.refusal(call.command(), taskBranch, request.getLocalPort(), remote)
        .map(
            reason -> {
              stateService.updateTask(
                  Objects.requireNonNull(taskBranch), task -> task.refused(Guard.SESSION_LINE));
              log.atInfo().setMessage("session line refused").addKeyValue("task", taskBranch).log();
              return denied(reason);
            })
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  private Remote remoteOf(TaskState task) {
    try {
      return configService.project(Objects.requireNonNull(task.project())).gitRemote();
    } catch (IllegalArgumentException unknownProject) {
      return Remote.ORIGIN;
    }
  }

  private ResponseEntity<Map<String, Object>> planFirst(@Nullable String cwd, ToolCall call) {
    return stateService
        .findByWorktree(cwd)
        .flatMap(
            owner ->
                PlanFirst.refusal(owner.getValue(), call.file())
                    .map(
                        reason -> {
                          stateService.updateTask(
                              owner.getKey(), task -> task.refused(Guard.PLAN_FIRST));
                          log.atInfo()
                              .setMessage("write refused")
                              .addKeyValue("task", owner.getKey())
                              .addKeyValue("cause", "plan not approved")
                              .addKeyValue("file", call.file())
                              .log();
                          return denied(reason);
                        }))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  private ResponseEntity<Map<String, Object>> denied(String reason) {
    return ResponseEntity.ok(envelope.denial(reason));
  }
}
