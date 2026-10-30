package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.CaseBook;
import dev.jagt.orchestrator.config.MasterCases;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * A step goes to a session only where a case sends it at the task's status, whoever relays it; the session's reply
 * closes its turn only where a case of that step takes it, and the Master presses a verb only where a Master case
 * allows it; a miss stops the task for the human.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaseDoor {

    private final SessionCases cases;
    private final MasterCases masterCases;
    private final StateService stateService;
    private final MissLog misses;
    private final TaskStops stops;

    /** The door's answer, with the status it read to give it: unknown for a task jagt does not hold. */
    public record Admission(boolean admitted, @Nullable TaskStatus status) {
    }

    /** A task jagt does not hold is left to the relay, which refuses it by name. */
    public Admission opens(String taskId, Step step) {
        String id = stateService.canonicalTaskId(taskId);
        Optional<TaskStatus> status = stateService.task(id).map(TaskState::status);
        if (status.isEmpty()) {
            return new Admission(true, null);
        }
        boolean admitted = cases.book().sends(step.id(), status.get(), CaseBook.By.of(OriginContext.current()))
                || refused(id, step, status.get());
        return new Admission(admitted, status.get());
    }

    /** Only the Master's press is held to a case, and a verb only a human presses is refused it outright. */
    public boolean presses(String taskId, TaskAction action) {
        if (!byTheMaster()) {
            return true;
        }
        if (action.humanOnly()) {
            throw new Refusal(Refusal.Code.ACTION_NOT_AVAILABLE,
                    action.label() + " is a human's to press, never the Master's.");
        }
        return held(taskId, action.id());
    }

    /** Only the Master's move is held to a case: any other passes. */
    public boolean presses(String taskId, String move) {
        return !byTheMaster() || held(taskId, move);
    }

    private boolean held(String taskId, String move) {
        String id = stateService.canonicalTaskId(taskId);
        Optional<TaskStatus> status = stateService.task(id).map(TaskState::status);
        return status.isEmpty() || allows(id, move, status.get());
    }

    /** Whether what runs now runs as the Master: the one answer to who that is. */
    public static boolean byTheMaster() {
        return CaseBook.By.of(OriginContext.current()) == CaseBook.By.MASTER;
    }

    private boolean refused(String id, Step step, TaskStatus status) {
        CaseBook.By by = CaseBook.By.of(OriginContext.current());
        misses.record(id, step.id(), status, by);
        log.atWarn().setMessage("step refused").addKeyValue("task", id).addKeyValue("step", step.id())
                .addKeyValue("status", status).addKeyValue("by", by.id())
                .addKeyValue("cause", "no case sends it at this status")
                .addKeyValue("effect", "nothing relayed, the task stopped")
                .addKeyValue("fix", SessionCases.FILE)
                .log();
        stops.stop(id, TaskStop.Trigger.MISS, "no case sends " + step.id() + " at " + status + ", set off by " + by.id());
        return false;
    }

    private boolean allows(String id, String move, TaskStatus status) {
        if (masterCases.book().sends(move, status, CaseBook.By.MASTER)) {
            return true;
        }
        misses.record(id, move, status, CaseBook.By.MASTER);
        log.atWarn().setMessage("move refused").addKeyValue("task", id).addKeyValue("step", move)
                .addKeyValue("status", status).addKeyValue("by", CaseBook.By.MASTER.id())
                .addKeyValue("cause", "no master case allows it at this status")
                .addKeyValue("effect", "the move not made, the task stopped")
                .addKeyValue("fix", MasterCases.FILE)
                .log();
        stops.stop(id, TaskStop.Trigger.MISS, "no master case allows " + move + " at " + status);
        return false;
    }

    /** A step whose telling failed after it was claimed: the claim is undone and the task waits for the human. */
    public void undelivered(String taskId, String step, String cause) {
        stops.stop(stateService.canonicalTaskId(taskId), TaskStop.Trigger.UNDELIVERED,
                step + " could not be told to the session: " + cause);
    }

    /** A round of a loop no thread count measures goes only while it runs below its ceiling; past it the task stops. */
    public boolean roundDue(String taskId, String step) {
        return stops.roundDue(stateService.canonicalTaskId(taskId), step);
    }

    /** A reply with no turn open, or to a step no case names, is taken; a refused one stops the task. */
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
        stops.stop(id, TaskStop.Trigger.MISS, "no case of " + turn.step() + " takes " + got + ", only " + expected.get());
        return false;
    }
}
