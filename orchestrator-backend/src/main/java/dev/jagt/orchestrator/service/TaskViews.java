package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskView;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
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
public class TaskViews {

    /** Written by the agent, in the worktree — see {@link ReviewSweepService}'s relayed brief. */
    private static final String DRAFTED_REPLIES_FILE = "review_replies.md";

    private final StateService stateService;

    public TaskViews(StateService stateService) {
        this.stateService = stateService;
    }

    public List<TaskView> all() {
        Map<String, TaskState> tasks = stateService.tasks();
        return tasks.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, TaskState> e) ->
                        e.getValue().lastActiveTimestamp()).reversed())
                .map(entry -> TaskView.of(entry.getKey(), entry.getValue(),
                        hasDraftedReplies(entry.getValue())))
                .toList();
    }

    /**
     * The drafted replies live in a FILE the agent writes, not in {@code state.json} — which is why nothing
     * used to mention them. So the projection looks: one {@code stat} per task per render, which is cheaper
     * than the alternative (an agent-reported flag jagt would then have to keep in sync with the file).
     */
    private static boolean hasDraftedReplies(TaskState task) {
        String worktree = task.worktreePath();
        return worktree != null && !worktree.isBlank()
                && Files.isRegularFile(Path.of(worktree).resolve(DRAFTED_REPLIES_FILE));
    }
}
