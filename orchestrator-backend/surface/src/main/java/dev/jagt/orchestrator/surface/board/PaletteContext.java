package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskView;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/** The board as the palette's model sees it, and the one way back from a task it named to a task that exists. */
@Component
@RequiredArgsConstructor
public class PaletteContext {

    /** Not a TaskAction: `do` creates a task rather than acting on one, and it is the commonest request. */
    static final String DO = "do";
    /** Also not a TaskAction: `resume` takes over an EXISTING review request instead of starting anything. */
    static final String RESUME = "resume";

    private final StateService stateService;
    private final TaskViews taskViews;

    /** Built from the SAME projection the surfaces render, so nothing off the board can be proposed. */
    public String prompt() {
        String commandList = Arrays.stream(TaskAction.values())
                .map(action -> "- " + action.id() + ": " + action.hint())
                .collect(Collectors.joining("\n"));
        String taskList = taskViews.all().stream()
                .map(PaletteContext::taskLine)
                .collect(Collectors.joining("\n"));
        return "COMMANDS (one of these words, or \"none\"):\n" + commandList
                + "\n- " + DO + ": start a NEW task from a ticket key or URL (the `ticket` field carries it)"
                + "\n- " + RESUME + ": take over an EXISTING review request / merge request — its branch and its"
                + " commits — when the human gives such a URL (the `ticket` field carries the URL)\n\n"
                + "TASKS currently registered"
                + (stateService.tasks().isEmpty() ? " — NONE, so only `do` or `none` can apply:\n(none)"
                        : " (use the id or alias verbatim):\n" + taskList);
    }

    /** Only a task that EXISTS may be acted on; an id the model invented resolves to nothing and is refused. */
    String existingTask(String proposed) {
        if (proposed == null || proposed.isBlank()) {
            return null;
        }
        String canonical = stateService.canonicalTaskId(proposed.strip());
        return stateService.task(canonical).isPresent() ? canonical : null;
    }

    /** One task per line, including what is legal for it — the model should not propose a refused action. */
    private static String taskLine(TaskView task) {
        String legal = task.actions().stream().map(TaskView.ActionView::id).collect(Collectors.joining(","));
        return "- id=" + task.id() + " alias=" + (task.alias() == null ? "-" : task.alias())
                + " status=" + task.status() + " title=\"" + (task.title() == null ? "" : task.title())
                + "\" legal=" + legal;
    }
}
