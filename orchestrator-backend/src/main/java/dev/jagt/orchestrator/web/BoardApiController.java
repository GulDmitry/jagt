package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.model.LaunchRequest;
import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.model.TaskView;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.service.CommandReference;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateViews;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
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

    /** Everything one board render needs, in one round trip. */
    public record Board(List<TaskView> tasks, Spend spend, List<String> projects) {
    }

    /** What jagt's own model calls have cost this session — the number the console shows in its header. */
    public record Spend(int calls, long tokens) {
    }

    public record ActionResult(String message) {
    }

    /** {@code delete=false} lists what WOULD go (the console's bare `prune`); true is the console's `prune all`. */
    public record PruneRequest(boolean delete) {
    }

    /** Free text from the command palette (Cmd-K) — tier 2 of the dispatch, not a command. */
    public record InterpretRequest(String text) {
    }

    /**
     * Taking over a review request that already exists: its branch is resumed with the commits already on it
     * (someone else's, or your own from before), the request is linked, and no second one is opened. The ticket
     * is optional — the read supplies it — and giving it skips that read.
     */
    public record ResumeRequest(String reviewRequestUrl, String ticket) {
    }

    private final TaskViews taskViews;
    private final CommandService commands;
    private final TaskLauncher launcher;
    private final ConfigService configService;
    private final UsageTracker usageTracker;
    private final TaskEventStream events;
    private final NaturalLanguageDispatch naturalLanguage;
    private final OrchestratorTools tools;
    private final StateViews views;

    public BoardApiController(TaskViews taskViews, CommandService commands, TaskLauncher launcher,
                              ConfigService configService, UsageTracker usageTracker, TaskEventStream events,
                              NaturalLanguageDispatch naturalLanguage, OrchestratorTools tools,
                              StateViews views) {
        this.taskViews = taskViews;
        this.commands = commands;
        this.launcher = launcher;
        this.configService = configService;
        this.usageTracker = usageTracker;
        this.events = events;
        this.naturalLanguage = naturalLanguage;
        this.tools = tools;
        this.views = views;
    }

    @GetMapping("/tasks")
    public Board tasks() {
        var session = usageTracker.session();
        return new Board(taskViews.all(), new Spend(session.calls(), session.total()),
                List.copyOf(configService.load().projects().keySet()));
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
        return new ActionResult(launcher.launch(request.normalized()));
    }

    /**
     * Resumes a task from an existing review request — the board's half of the console's `resume <mr-url>`.
     * Deliberately its own endpoint rather than a smarter {@code /api/tasks}: a ticket URL and a review-request
     * URL look alike, and jagt guessing which one you meant would create the wrong thing half the time.
     */
    @PostMapping("/tasks/resume")
    public ActionResult resume(@RequestBody ResumeRequest request) {
        String url = request.reviewRequestUrl() == null ? "" : request.reviewRequestUrl().strip();
        if (!url.startsWith("http")) {
            throw new IllegalArgumentException("A review-request URL is required (http…)");
        }
        String ticket = request.ticket() == null || request.ticket().isBlank() ? null : request.ticket().strip();
        return new ActionResult(launcher.resume(url, ticket));
    }

    /**
     * The command palette: free text in, one executed grammar command out (or an explanation). The model that
     * reads the text cannot execute anything — {@link NaturalLanguageDispatch} validates its proposal against
     * the same gate the buttons use, so the palette can never do more than a button could.
     */
    @PostMapping("/interpret")
    public ActionResult interpret(@RequestBody InterpretRequest request) {
        return new ActionResult(naturalLanguage.interpret(request.text()));
    }

    /**
     * Branch cleanup, the board's half of `prune [all]`. {@code delete=false} is the dry run — the same rule the
     * console has, where a bare `prune` lists and only `prune all` deletes, so the destructive form is never
     * one click away from the harmless one.
     */
    @PostMapping("/prune")
    public ActionResult prune(@RequestBody PruneRequest request) {
        return new ActionResult(tools.pruneBranches(request != null && request.delete()));
    }

    /**
     * The grammar as data, for the palette's autocomplete and its client-side validation. Served rather than
     * hardcoded in the page, so a verb cannot exist in the console and be missing from the suggestions.
     */
    @GetMapping("/commands")
    public List<CommandReference.Verb> commands() {
        return CommandReference.verbs();
    }

    /** The command grammar — the same text the console prints for `help` (see {@link CommandReference}). */
    @GetMapping(value = "/help", produces = MediaType.TEXT_PLAIN_VALUE)
    public String help() {
        return CommandReference.text();
    }

    /** Token spend, the same text `stats` prints, so the board does not need a second renderer for it. */
    @GetMapping(value = "/stats", produces = MediaType.TEXT_PLAIN_VALUE)
    public String stats() {
        return views.usageStats();
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
}
