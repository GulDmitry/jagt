package dev.jagt.orchestrator.web;

import dev.jagt.orchestrator.model.TaskView;
import dev.jagt.orchestrator.service.CommandReference;
import dev.jagt.orchestrator.service.GlobalCommand;
import dev.jagt.orchestrator.service.GlobalCommands;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.UsageTracker;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * The board's READ side: the shared {@link TaskView} projection every surface renders, the grammar, the reports
 * behind it and the change stream. Acting on a task is {@link TaskCommandsController}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BoardApiController {

    /** Everything one board render needs, in one round trip. */
    public record Board(List<TaskView> tasks, Spend spend, List<String> projects, String autoReview,
                        boolean autoReviewEnabled) {
    }

    /** What jagt's own model calls have cost this session — the number the console shows in its header. */
    public record Spend(int calls, long tokens) {
    }

    private final TaskViews taskViews;
    private final UsageTracker usageTracker;
    private final TaskEventStream events;
    private final GlobalCommands globals;

    @GetMapping("/tasks")
    public Board tasks() {
        var session = usageTracker.session();
        // Whether polling runs at all is a property of the INSTALL, so it is answered once per render rather
        // than repeated on every card — a board with no task out for review must still be able to say it.
        var snapshot = taskViews.snapshot();
        return new Board(snapshot.tasks(), new Spend(session.calls(), session.total()), snapshot.projects(),
                snapshot.cadence().summary(), snapshot.cadence().enabled());
    }

    /**
     * The grammar as data, for the palette's autocomplete and its client-side validation. Served rather than
     * hardcoded in the page, so a verb cannot exist in the console and be missing from the suggestions — minus
     * the ones that only mean something in a terminal, which the board must not be able to offer at all.
     */
    @GetMapping("/commands")
    public List<CommandReference.Verb> commands() {
        return CommandReference.verbs(globals.all()).stream()
                .filter(verb -> !verb.consoleOnly()).toList();
    }

    /**
     * Any command's text report, at one address, so declaring another one needs no endpoint here. Reports ONLY:
     * a GET must not be able to start work.
     */
    @GetMapping(value = "/commands/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String report(@PathVariable String id) {
        return globals.byId(id).filter(GlobalCommand::report).filter(command -> !command.consoleOnly())
                .orElseThrow(() -> new IllegalArgumentException("No report '" + id + "'"))
                .run("");
    }

    /** "Something moved" — the browser re-fetches the board. See {@link TaskEventStream}. */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return events.open();
    }

}
