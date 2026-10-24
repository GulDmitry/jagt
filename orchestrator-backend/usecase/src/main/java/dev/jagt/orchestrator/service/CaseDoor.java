package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.CaseBook;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.LoopWatch;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * A step goes to a session only where a case sends it at the task's status, and the session's reply closes its turn
 * only where a case of that step takes it; a miss either way stops the task for the human.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaseDoor {

    private final SessionCases cases;
    private final StateService stateService;
    private final MissLog misses;
    private final TaskStops stops;

    /** A task jagt does not hold is left to the relay, which refuses it by name. */
    public boolean opens(String taskId, String step) {
        String id = stateService.canonicalTaskId(taskId);
        Optional<TaskStatus> status = stateService.task(id).map(TaskState::status);
        if (status.isEmpty() || cases.book().sends(step, status.get())) {
            return true;
        }
        CaseBook.By by = CaseBook.By.of(OriginContext.current());
        misses.record(id, step, status.get(), by);
        log.atWarn().setMessage("step refused").addKeyValue("task", id).addKeyValue("step", step)
                .addKeyValue("status", status.get()).addKeyValue("by", by.id())
                .addKeyValue("cause", "no case sends it at this status")
                .addKeyValue("effect", "nothing relayed, the task stopped")
                .addKeyValue("fix", SessionCases.FILE)
                .log();
        stops.stop(id, LoopWatch.Trigger.MISS, "no case sends " + step + " at " + status.get() + ", set off by " + by.id());
        return false;
    }

    /**
     * A reply with no turn open, or to a step no case names, is taken. A refused one hands the turn to the human, so
     * the session's next report is not refused for the same step again.
     */
    public boolean takes(String taskId, TaskStatus reported, AgentReport kind) {
        String id = stateService.canonicalTaskId(taskId);
        Optional<TaskState> task = stateService.task(id);
        Turn turn = task.map(TaskState::turnOrNone).orElse(Turn.none());
        Optional<CaseBook.Replies> expected = turn.open() ? cases.book().replies(turn.step()) : Optional.empty();
        if (expected.isEmpty() || expected.get().takes(reported, kind)) {
            return true;
        }
        String got = reported + "; " + CaseBook.Replies.kind(kind);
        misses.refused(id, turn.step(), task.orElseThrow().status(), got, expected.get().toString());
        log.atWarn().setMessage("reply refused").addKeyValue("task", id).addKeyValue("step", turn.step())
                .addKeyValue("got", got).addKeyValue("expected", expected.get().toString())
                .addKeyValue("cause", "no case of the step takes it")
                .addKeyValue("effect", "the report not applied, the task stopped")
                .addKeyValue("fix", SessionCases.FILE)
                .log();
        stateService.updateTask(id, held -> held.withTurn(held.turnOrNone().closed(Turn.Holder.HUMAN,
                held.lastActiveTimestamp())));
        stops.stop(id, LoopWatch.Trigger.MISS, "no case of " + turn.step() + " takes " + got + ", only " + expected.get());
        return false;
    }
}
