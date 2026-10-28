package dev.jagt.orchestrator.surface.agent;

import org.jspecify.annotations.Nullable;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NotesDue;
import dev.jagt.orchestrator.service.SessionProbe;
import dev.jagt.orchestrator.service.TurnTree;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * A turn end, answered with the CLI's refusal where the move is still the agent's, or the tree changed, and nothing
 * was reported since the turn began. One refusal per turn: a turn the refusal itself sent on always ends.
 */
@Service
@RequiredArgsConstructor
public class TurnEnds {

    private final SessionProbe probe;
    private final AgentRuntime runtime;
    private final ConfigService configService;
    private final NotesDue notes;
    private final TurnTree turnTree;

    public String answer(String taskId, @Nullable TaskState task, boolean sentOn, boolean pausedOnBackgroundWork) {
        if (sentOn || pausedOnBackgroundWork || task == null) {
            return withTheMaster(task);
        }
        long turnStarted = probe.turnStartedAt(taskId);
        boolean unreported = turnStarted != 0 && task.lastActiveTimestamp() < turnStarted;
        if (unreported && !Move.endsUnreported(task.status()) && Move.endsOnTheTree(task.status())
                && turnTree.changedUnreported(taskId, task)) {
            return runtime.refusedTurnEnd("Your turn is ending with " + taskId + " at " + task.status()
                    + " on the board and the tree changed without a report. Call update_agent_status first:"
                    + " IN_PROGRESS to hand the change back as a round, or revert the change.");
        }
        if (!unreported || !Move.endsUnreported(task.status())) {
            return notes.reminder(task, configService.load().agent().notesEveryOrDefault())
                    .map(runtime::refusedTurnEnd).orElseGet(() -> withTheMaster(task));
        }
        return runtime.refusedTurnEnd("Your turn is ending with " + taskId + " at " + task.status()
                + " on the board and nothing reported this turn. Call update_agent_status first: REVIEW_PENDING"
                + " if the work is done, outcome=question if you need the human, IN_PROGRESS if you go on.");
    }

    private String withTheMaster(@Nullable TaskState task) {
        if (task == null || !FlowRules.headedForTheMaster(task.status()) || !configService.load().master().running()) {
            return "";
        }
        return runtime.toldTheHuman(FlowRules.awaitingVerification(task.status())
                ? "→ verification runs first · the Master reads the round once it passes"
                : "→ with the Master for review · its verdict arrives here");
    }
}
