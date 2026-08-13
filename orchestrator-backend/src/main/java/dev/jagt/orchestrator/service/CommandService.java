package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.mcp.OrchestratorTools;
import dev.jagt.orchestrator.model.Move;
import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.model.TaskState;
import org.springframework.stereotype.Service;

/**
 * The ONE place a per-task action is executed, whoever asked: a typed command in the console, a button in the
 * web board, or an HTTP POST. Without it each surface would call {@link OrchestratorTools} in its own way and
 * "what does ship do" would have as many answers as there are front-ends.
 *
 * <p>It also enforces the same legality the surfaces use to DECIDE what to offer ({@link Move}), so an action
 * that a stale page or a hand-written curl asks for is refused with a sentence rather than attempted — the
 * asymmetric-failure rule: proposing is cheap, executing a deploy on the wrong task is not.
 */
@Service
public class CommandService {

    private final OrchestratorTools tools;
    private final ReviewSweepService reviewSweep;
    private final StateService stateService;

    public CommandService(OrchestratorTools tools, ReviewSweepService reviewSweep, StateService stateService) {
        this.tools = tools;
        this.reviewSweep = reviewSweep;
        this.stateService = stateService;
    }

    /**
     * Runs {@code action} on {@code taskIdOrAlias} and returns what to show the human — the same sentence the
     * console has always printed, so the two surfaces read alike.
     */
    public String execute(String taskIdOrAlias, TaskAction action) {
        String taskId = stateService.canonicalTaskId(taskIdOrAlias);
        TaskState task = stateService.task(taskId).orElseThrow(() -> new IllegalArgumentException(
                "No task " + taskIdOrAlias + " — it may have been closed since this page loaded."));
        Move move = Move.forTask(task.status(), task.mrUrl() != null && !task.mrUrl().isBlank());
        if (!move.actions().contains(action)) {
            throw new IllegalArgumentException(action.label() + " is not available for " + taskId
                    + " (it is " + task.status() + " — " + move.hint() + ")");
        }
        return switch (action) {
            case FOCUS -> tools.focusTask(taskId);
            case IDE -> tools.openInIde(taskId, "project", null);
            case DIFF -> tools.openInIde(taskId, "diff", null);
            case SHIP -> tools.ship(taskId);
            case SWEEP -> reviewSweep.sweep(taskId).message();
            case DEPLOY -> tools.deployTask(taskId, null);
            case RESPAWN -> tools.openTaskTab(taskId, null);
            case DONE -> tools.removeTask(taskId, null);
        };
    }
}
