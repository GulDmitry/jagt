package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.flow.TurnKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** A nudge whose Enter was lost to a turn still ending stays in the input for hours; the idle session is pressed again. */
@Service
@RequiredArgsConstructor
@Slf4j
public class NudgeResubmitJob implements TaskJob {

    static final String MARK = "nudge-resubmits";
    static final int CEILING = 5;

    private final AgentSessions sessions;
    private final TaskTurns turns;

    @Override
    public String id() {
        return "nudge-resubmit";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "press Enter again on jagt's own nudge left unsent in an idle session's input";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(4);
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withTurns(TurnKind.OPEN);
    }

    @Override
    public void run(String taskId, TaskState task) {
        Turn turn = task.turnOrNone();
        int pressed = pressed(turn);
        if (pressed >= CEILING || !sessions.resubmitUnsentNudge(taskId)) {
            return;
        }
        turns.mark(taskId, MARK, turn.id() + ":" + (pressed + 1));
        log.atInfo().setMessage("nudge resubmitted").addKeyValue("task", taskId)
                .addKeyValue("alias", task.alias()).addKeyValue("turn", turn.id())
                .addKeyValue("pressed", pressed + 1).addKeyValue("cause", "it sat unsent in an idle input").log();
    }

    private static int pressed(Turn turn) {
        String kept = turn.mark(MARK);
        String prefix = turn.id() + ":";
        return kept != null && kept.startsWith(prefix) ? Integer.parseInt(kept.substring(prefix.length())) : 0;
    }
}
