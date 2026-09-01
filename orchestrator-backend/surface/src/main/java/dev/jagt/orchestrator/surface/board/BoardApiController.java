package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.flow.Phase;
import dev.jagt.orchestrator.flow.TaskView;
import dev.jagt.orchestrator.command.CommandReference;
import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.service.TaskViews;
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

    public record Board(List<TaskView> tasks, List<String> projects, String autoReview,
                        boolean autoReviewEnabled, Jobs.Summary jobs,
                        List<BranchStrategy.Choice> branchStrategies, List<Phase.Choice> phases) {
    }

    private final TaskViews taskViews;
    private final TaskEventStream events;
    private final GlobalCommands globals;
    private final Jobs jobs;

    @GetMapping("/tasks")
    public Board tasks() {
        var snapshot = taskViews.snapshot();
        return new Board(snapshot.tasks(), snapshot.projects(),
                snapshot.cadence().summary(), snapshot.cadence().enabled(),
                jobs.summary(System.currentTimeMillis()), BranchStrategy.choices(), Phase.choices());
    }

    @GetMapping("/commands")
    public List<CommandReference.Verb> commands() {
        return CommandReference.verbs(globals.all());
    }

    /** Reports only: a GET must not start work. {@code about} is what a typed line puts after the verb. */
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
