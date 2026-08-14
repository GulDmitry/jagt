package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.model.TaskView;
import dev.jagt.orchestrator.service.CommandReference;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateViews;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.UsageTracker;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * The board's READ side: the shared {@link TaskView} projection every surface renders, plus the grammar and the
 * change stream. Acting on a task is {@link TaskCommandsController}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BoardApiController {

    /** Everything one board render needs, in one round trip. */
    public record Board(List<TaskView> tasks, Spend spend, List<String> projects) {
    }

    /** What jagt's own model calls have cost this session — the number the console shows in its header. */
    public record Spend(int calls, long tokens) {
    }

    private final TaskViews taskViews;
    private final ConfigService configService;
    private final UsageTracker usageTracker;
    private final TaskEventStream events;
    private final StateViews views;

    @GetMapping("/tasks")
    public Board tasks() {
        var session = usageTracker.session();
        return new Board(taskViews.all(), new Spend(session.calls(), session.total()),
                List.copyOf(configService.load().projects().keySet()));
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

}
