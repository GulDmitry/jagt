package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.flow.TaskView;
import dev.jagt.orchestrator.command.CommandReference;
import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.UsageTracker;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BoardApiController {

    public record Board(List<TaskView> tasks, Spend spend, List<String> projects, String autoReview,
                        boolean autoReviewEnabled, Jobs.Summary jobs,
                        List<BranchStrategy.Choice> branchStrategies) {
    }

    /** What jagt's OWN model calls have cost this session; an agent's own session spends elsewhere. */
    public record Spend(int calls, long tokens) {
    }

    private final TaskViews taskViews;
    private final UsageTracker usageTracker;
    private final TaskEventStream events;
    private final GlobalCommands globals;
    private final Jobs jobs;

    @GetMapping("/tasks")
    public Board tasks() {
        var session = usageTracker.session();
        var snapshot = taskViews.snapshot();
        return new Board(snapshot.tasks(), new Spend(session.calls(), session.total()), snapshot.projects(),
                snapshot.cadence().summary(), snapshot.cadence().enabled(),
                jobs.summary(System.currentTimeMillis()), BranchStrategy.choices());
    }

    /** Served rather than hardcoded in the page, so a declared verb cannot be missing from the suggestions. */
    @GetMapping("/commands")
    public List<CommandReference.Verb> commands() {
        return CommandReference.verbs(globals.all());
    }

    /**
     * Reports ONLY: a GET must not be able to start work. {@code about} carries what a typed line puts after the
     * verb, so a report that narrows to one task needs no endpoint of its own.
     */
    @GetMapping(value = "/commands/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String report(@PathVariable String id, @RequestParam(required = false) String about) {
        return globals.byId(id).filter(GlobalCommand::report)
                .orElseThrow(() -> new IllegalArgumentException("No report '" + id + "'"))
                .run(about == null ? "" : about);
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return events.open();
    }

}
