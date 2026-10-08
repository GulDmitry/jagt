package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.TaskLauncher;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskCommandsController {

    public record ActionResult(String message) {
    }

    private final CommandService commands;
    private final TaskLauncher launcher;

    /** No aliases: a renamed verb is accepted only where a human types, and a page offering the old id is stale. */
    @PostMapping("/tasks/actions/{actionId}")
    public ActionResult act(@RequestParam("task") String taskId, @PathVariable String actionId) {
        TaskAction action = TaskAction.byId(actionId).orElseThrow(() ->
                new IllegalArgumentException("Unknown action '" + actionId + "'"));
        return new ActionResult(commands.execute(taskId, action));
    }

    /** Slow on purpose when a ticket is named: reading it is a remote call. */
    @PostMapping("/tasks")
    public ActionResult launch(@RequestBody LaunchRequest request) {
        Launched launched = launcher.launch(request.normalized());
        if (!launched.created()) {
            throw new Refusal(Refusal.Code.STATE, launched.message());
        }
        return new ActionResult(launched.message());
    }
}
