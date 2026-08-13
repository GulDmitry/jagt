package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.model.TaskView;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.UsageTracker;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * The web board's API. It adds no rules of its own: the tasks are the shared {@link TaskView} projection, an
 * action is executed by {@link CommandService} (which refuses what the projection would not have offered), and
 * a new task goes through {@link TaskLauncher} exactly as the typed {@code do} does.
 *
 * <p>So a button cannot do something the console cannot, and a stale page cannot do something the task's
 * status no longer allows — it gets 400 and the reason, in a sentence meant for a human.
 */
@RestController
@RequestMapping("/api")
public class BoardApiController {

    /**
     * Everything one board render needs, in one round trip. {@code capacity} is the task cap
     * ({@code agent.maxConcurrentTasks}, 0 = none) — the same number `do` is refused against, so the board
     * shows the limit before a human hits it.
     */
    public record Board(List<TaskView> tasks, Spend spend, List<String> projects, int capacity) {
    }

    /** What jagt's own model calls have cost this session — the number the console shows in its header. */
    public record Spend(int calls, long tokens) {
    }

    public record ActionResult(String message) {
    }

    /** A `do` from the browser: the same modifiers the command grammar accepts, as fields. */
    public record LaunchRequest(String ref, String project, String mode, String strategy, String notes) {
    }

    private final TaskViews taskViews;
    private final CommandService commands;
    private final TaskLauncher launcher;
    private final ConfigService configService;
    private final UsageTracker usageTracker;
    private final TaskEventStream events;

    public BoardApiController(TaskViews taskViews, CommandService commands, TaskLauncher launcher,
                              ConfigService configService, UsageTracker usageTracker, TaskEventStream events) {
        this.taskViews = taskViews;
        this.commands = commands;
        this.launcher = launcher;
        this.configService = configService;
        this.usageTracker = usageTracker;
        this.events = events;
    }

    @GetMapping("/tasks")
    public Board tasks() {
        var session = usageTracker.session();
        var config = configService.load();
        return new Board(taskViews.all(), new Spend(session.calls(), session.total()),
                List.copyOf(config.projects().keySet()),
                Math.max(0, config.agent().maxConcurrentTasksOrDefault()));
    }

    /**
     * Runs one action on one task. The action id in the URL is the same word the console takes, and an unknown
     * one is refused rather than mapped to something near it.
     */
    @PostMapping("/tasks/{taskId}/actions/{actionId}")
    public ActionResult act(@PathVariable String taskId, @PathVariable String actionId) {
        TaskAction action = TaskAction.byId(actionId).orElseThrow(() ->
                new IllegalArgumentException("Unknown action '" + actionId + "'"));
        return new ActionResult(commands.execute(taskId, action));
    }

    /** Starts a task. Slow on purpose: reading the ticket is a model call, and the UI shows it as pending. */
    @PostMapping("/tasks")
    public ActionResult launch(@RequestBody LaunchRequest request) {
        if (request.ref() == null || request.ref().isBlank()) {
            throw new IllegalArgumentException("A ticket key or a URL is required");
        }
        return new ActionResult(launcher.launch(request.ref().strip(), blankToNull(request.project()),
                blankToNull(request.mode()), blankToNull(request.strategy()), request.notes()));
    }

    /** "Something moved" — the browser re-fetches the board. See {@link TaskEventStream}. */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return events.open();
    }

    /**
     * Every refusal a human can cause — an action that is not legal now, an unknown project, a task that was
     * closed in another tab — is a 400 with the sentence, not a stack trace. The board shows it as-is.
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> refused(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "refused" : e.getMessage()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
