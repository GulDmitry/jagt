package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * A turn end, answered with the CLI's refusal where the move is still the agent's and nothing was reported since
 * the turn began. One refusal per turn: a turn the refusal itself sent on always ends.
 */
@Service
@RequiredArgsConstructor
public class TurnEnds {

    private final SessionProbe probe;
    private final AgentRuntime runtime;
    private final ConfigService configService;

    public String answer(String taskId, TaskState task, boolean sentOn, boolean pausedOnBackgroundWork) {
        long turnStarted = probe.turnStartedAt(taskId);
        if (sentOn || pausedOnBackgroundWork || task == null || turnStarted == 0
                || task.lastActiveTimestamp() >= turnStarted || !Move.endsUnreported(task.status(), task.message())) {
            return withTheMaster(task);
        }
        return runtime.refusedTurnEnd("Your turn is ending with " + taskId + " at " + task.status()
                + " on the board and nothing reported this turn. Call update_agent_status first: REVIEW_PENDING"
                + " if the work is done, outcome=question if you need the human, IN_PROGRESS if you go on.");
    }

    private String withTheMaster(TaskState task) {
        if (task == null || !(FlowRules.awaitingVerification(task.status())
                || FlowRules.readByTheMaster(task.status())) || !configService.load().master().running()) {
            return "";
        }
        return runtime.toldTheHuman(FlowRules.awaitingVerification(task.status())
                ? "→ verification runs first · the Master reads the round once it passes"
                : "→ with the Master for review · its verdict arrives here");
    }
}
