package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.task.LaunchRequest;
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

/**
 * It adds no rules of its own: everything here goes through the same gate the typed commands do, so a button
 * cannot do something the console cannot.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskCommandsController {

    public record ActionResult(String message) {
    }

    public record InterpretRequest(String text) {
    }

    /** The URL is the only input: the request names its own source branch (= the task) and target (= the base). */
    public record ResumeRequest(String reviewRequestUrl) {
    }

    private final CommandService commands;
    private final TaskLauncher launcher;
    private final NaturalLanguageDispatch naturalLanguage;
    /**
     * An unknown action id is refused rather than mapped to something near it — including a verb the grammar was
     * renamed FROM, which is accepted where a human types and nowhere else. A page still offering the old id is
     * a stale page, and it reloads.
     */
    @PostMapping("/tasks/actions/{actionId}")
    public ActionResult act(@RequestParam("task") String taskId, @PathVariable String actionId) {
        TaskAction action = TaskAction.byId(actionId).orElseThrow(() ->
                new IllegalArgumentException("Unknown action '" + actionId + "'"));
        return new ActionResult(commands.execute(taskId, action));
    }

    /** Slow on purpose: reading the ticket is a remote call, and the page shows it as pending. */
    @PostMapping("/tasks")
    public ActionResult launch(@RequestBody LaunchRequest request) {
        if (request.ref() == null || request.ref().isBlank()) {
            throw new IllegalArgumentException("A ticket key or a URL is required");
        }
        return new ActionResult(launcher.launch(request.normalized()));
    }

    /**
     * Its own endpoint rather than a smarter {@code /api/tasks}: a ticket URL and a review-request URL look
     * alike, and guessing would create the wrong thing half the time.
     */
    @PostMapping("/tasks/resume")
    public ActionResult resume(@RequestBody ResumeRequest request) {
        String url = request.reviewRequestUrl() == null ? "" : request.reviewRequestUrl().strip();
        if (!url.startsWith("http")) {
            throw new IllegalArgumentException("A review-request URL is required (http…)");
        }
        return new ActionResult(launcher.resume(url));
    }

    /**
     * Free text in, one executed grammar command out. The model that reads the text cannot execute anything: its
     * proposal is validated against the same gate the buttons use, so this can never do more than a button.
     */
    @PostMapping("/interpret")
    public ActionResult interpret(@RequestBody InterpretRequest request) {
        return new ActionResult(naturalLanguage.interpret(request.text()));
    }
}
