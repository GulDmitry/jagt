package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.AgentSessions;
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

    public record InterpretRequest(String text) {
    }

    public record LineRequest(String line) {
    }

    private final CommandService commands;
    private final TaskLauncher launcher;
    private final NaturalLanguageDispatch naturalLanguage;
    private final AgentSessions sessions;
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
            throw new IllegalStateException(launched.message());
        }
        return new ActionResult(launched.message());
    }

    /** A line typed where a round is read: it goes into the session, never over the brief on disk. */
    @PostMapping("/tasks/say")
    public ActionResult say(@RequestParam("task") String taskId, @RequestBody LineRequest request) {
        String line = request.line() == null ? "" : request.line().strip();
        if (line.isBlank()) {
            throw new IllegalArgumentException("There is nothing to say");
        }
        return new ActionResult(sessions.say(taskId, line));
    }

    /** The model only proposes; the proposal passes the same gate as a button. */
    @PostMapping("/interpret")
    public ActionResult interpret(@RequestBody InterpretRequest request) {
        return new ActionResult(naturalLanguage.interpret(request.text()));
    }
}
