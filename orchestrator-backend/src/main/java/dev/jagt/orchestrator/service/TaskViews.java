package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.TaskChoice;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds the ONE projection every human surface renders. Extracted so the TUI, the plain-text {@code /status}
 * and the web board cannot drift: a phase, an owner and a set of legal actions are computed once, here.
 *
 * <p>Order is "most recently active first", the same order the text dashboard has always used — the web board
 * regroups and re-sorts client-side, which is where a sort belongs when the data is already in the browser.
 */
@Component
@RequiredArgsConstructor
public class TaskViews {

    private final StateService stateService;

    /** Every task as something to pick from. */
    public List<TaskChoice> choices() {
        return stateService.tasks().entrySet().stream()
                .map(e -> new TaskChoice(e.getValue().alias(), e.getKey(), e.getValue().title()))
                .toList();
    }

    public List<TaskView> all() {
        Map<String, TaskState> tasks = stateService.tasks();
        return tasks.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, TaskState> e) ->
                        e.getValue().lastActiveTimestamp()).reversed())
                .map(entry -> TaskView.of(entry.getKey(), entry.getValue(),
                        WorktreeFiles.draftedReplies(entry.getValue())))
                .toList();
    }
}
