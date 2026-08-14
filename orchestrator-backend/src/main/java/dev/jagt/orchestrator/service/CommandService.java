package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.RoundState;
import dev.jagt.orchestrator.model.Move;
import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.model.TaskState;
import org.springframework.stereotype.Service;

/**
 * The ONE place a per-task action is executed, whoever asked: a typed command in the console, a button in the
 * web board, or an HTTP POST. Without it each surface would call these services in its own way and
 * "what does ship do" would have as many answers as there are front-ends.
 *
 * <p>It also enforces the same legality the surfaces use to DECIDE what to offer ({@link Move}), so an action
 * that a stale page or a hand-written curl asks for is refused with a sentence rather than attempted — the
 * asymmetric-failure rule: proposing is cheap, executing a deploy on the wrong task is not.
 */
@Service
public class CommandService {

    private final TaskOperations operations;
    private final DeployService deploys;
    private final ReviewSweepService reviewSweep;
    private final ShipService shipService;
    private final StateService stateService;

    public CommandService(TaskOperations operations, DeployService deploys, ReviewSweepService reviewSweep,
                          ShipService shipService, StateService stateService) {
        this.operations = operations;
        this.deploys = deploys;
        this.reviewSweep = reviewSweep;
        this.shipService = shipService;
        this.stateService = stateService;
    }

    /**
     * Runs {@code action} on {@code taskIdOrAlias} and returns what to show the human — the same sentence the
     * console has always printed, so the two surfaces read alike.
     */
    public String execute(String taskIdOrAlias, TaskAction action) {
        String taskId = stateService.canonicalTaskId(taskIdOrAlias);
        TaskState task = stateService.task(taskId).orElseThrow(() -> new Refusal(Refusal.Code.NO_SUCH_TASK,
                "No task " + taskIdOrAlias + " — it may have been closed since this page loaded."));
        Move move = Move.forTask(task.status(), task.mrUrl() != null && !task.mrUrl().isBlank(),
                RoundState.of(task.message(), WorktreeFiles.draftedReplies(task)));
        if (!move.actions().contains(action)) {
            throw new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE, action.label() + " is not available for "
                    + taskId + " (it is " + task.status() + " — " + move.hint() + ")");
        }
        return switch (action) {
            case FOCUS -> operations.focus(taskId);
            case IDE -> operations.openProject(taskId);
            case DIFF -> operations.openDiff(taskId);
            case SHIP -> shipService.ship(taskId);
            case SWEEP -> reviewSweep.sweep(taskId).message();
            case DEPLOY -> deploys.deploy(taskId);
            case REVERT -> deploys.revert(taskId);
            case RESPAWN -> operations.respawnAgent(taskId);
            case DONE -> operations.retire(taskId);
        };
    }
}
