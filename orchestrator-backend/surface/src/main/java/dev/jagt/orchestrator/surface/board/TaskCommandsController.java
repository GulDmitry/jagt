package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
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

    /** {@code created} is false for an ordinary decline, not only for a failure. */
    public record LaunchResult(String message, boolean created) {
    }

    public record InterpretRequest(String text) {
    }

    /** The URL names both branches, so it is the only input. */
    public record ResumeRequest(String reviewRequestUrl) {
    }

    public record LineRequest(String line) {
    }

    private final CommandService commands;
    private final TaskLauncher launcher;
    private final NaturalLanguageDispatch naturalLanguage;
    /** No aliases: a renamed verb is accepted only where a human types, and a page offering the old id is stale. */
    @PostMapping("/tasks/actions/{actionId}")
    public ActionResult act(@RequestParam("task") String taskId, @PathVariable String actionId) {
        TaskAction action = TaskAction.byId(actionId).orElseThrow(() ->
                new IllegalArgumentException("Unknown action '" + actionId + "'"));
        return new ActionResult(commands.execute(taskId, action));
    }

    /** Slow on purpose: reading the ticket is a remote call. */
    @PostMapping("/tasks")
    public LaunchResult launch(@RequestBody LaunchRequest request) {
        if (request.ref() == null || request.ref().isBlank()) {
            throw new IllegalArgumentException("A ticket key or a URL is required");
        }
        Launched launched = launcher.launch(request.normalized());
        return new LaunchResult(launched.message(), launched.created());
    }

    /** A typed line, parsed by the same grammar `do` uses — never by the page. */
    @PostMapping("/tasks/line")
    public LaunchResult launchLine(@RequestBody LineRequest request) {
        Launched launched = launcher.launchLine(request.line());
        return new LaunchResult(launched.message(), launched.created());
    }

    /** A ticket URL and a review-request URL are indistinguishable, so the caller picks the endpoint. */
    @PostMapping("/tasks/resume")
    public LaunchResult resume(@RequestBody ResumeRequest request) {
        String url = request.reviewRequestUrl() == null ? "" : request.reviewRequestUrl().strip();
        if (!url.startsWith("http")) {
            throw new IllegalArgumentException("A review-request URL is required (http…)");
        }
        Launched launched = launcher.resume(url);
        return new LaunchResult(launched.message(), launched.created());
    }

    /** The model only proposes; the proposal passes the same gate as a button. */
    @PostMapping("/interpret")
    public ActionResult interpret(@RequestBody InterpretRequest request) {
        return new ActionResult(naturalLanguage.interpret(request.text()));
    }
}
