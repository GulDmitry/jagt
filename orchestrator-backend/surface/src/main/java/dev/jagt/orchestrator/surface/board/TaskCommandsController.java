package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.AgentSessions;
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
    public LaunchResult launch(@RequestBody LaunchRequest request) {
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
