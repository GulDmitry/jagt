package dev.jagt.orchestrator.surface.board.task;

import dev.jagt.orchestrator.flow.Pressed;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.launch.LaunchRequest;
import dev.jagt.orchestrator.task.launch.Launched;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class TaskCommandsController {

  public record ActionResult(String message) {}

  /** {@code quiet}: the press changed nothing, so its button says so until the card moves. */
  public record PressResult(String message, boolean quiet) {}

  private final CommandService commands;
  private final TaskLauncher launcher;

  /**
   * No aliases: a renamed verb is accepted only where a human types, and a page offering the old id
   * is stale.
   */
  @PostMapping("/tasks/actions/{actionId}")
  public PressResult act(
      @RequestParam("task") String taskId,
      @PathVariable String actionId,
      @RequestParam(value = "project", required = false) @Nullable String project) {
    TaskAction action =
        TaskAction.byId(actionId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown action '" + actionId + "'"));
    Pressed pressed = commands.press(taskId, action, project);
    return new PressResult(pressed.message(), pressed.kind() == Outcome.Kind.NOTHING);
  }

  /** Slow on purpose when a ticket is named: reading it is a remote call. */
  @PostMapping("/tasks")
  public ActionResult launch(@RequestBody LaunchRequest request) {
    LaunchRequest normalized = request.normalized();
    Launched launched = launched(normalized);
    if (!launched.created()) {
      log.atWarn()
          .setMessage("launch refused")
          .addKeyValue("ref", normalized.ref())
          .addKeyValue("project", normalized.project())
          .addKeyValue("said", OneLine.clipped(launched.message()))
          .addKeyValue("cause", launched.reason().name().toLowerCase(Locale.ROOT))
          .log();
      throw new Refusal(
          launched.reason() == Launched.Reason.BRANCH_EXISTS
              ? Refusal.Code.BRANCH_EXISTS
              : Refusal.Code.STATE,
          launched.message());
    }
    return new ActionResult(launched.message());
  }

  private Launched launched(LaunchRequest request) {
    try {
      return request.project() == null
          ? launcher.launchUnplaced(request)
          : launcher.launch(request);
    } catch (IllegalArgumentException e) {
      log.atWarn()
          .setMessage("launch refused")
          .addKeyValue("ref", request.ref())
          .addKeyValue("project", request.project())
          .addKeyValue("said", OneLine.clipped(String.valueOf(e.getMessage())))
          .addKeyValue("cause", Causes.of(e))
          .log();
      throw e;
    }
  }
}
