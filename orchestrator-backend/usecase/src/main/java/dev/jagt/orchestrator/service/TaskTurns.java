package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.function.UnaryOperator;

/** A task's turn, written into the task so a restart keeps it. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskTurns {

    private final StateService stateService;
    private final Clock clock;

    public Turn of(String taskId) {
        return stateService.task(stateService.canonicalTaskId(taskId)).map(TaskState::turnOrNone).orElse(Turn.none());
    }

    public void opened(String taskId, String step, String digest) {
        update(taskId, turn -> turn.relayed(step, digest, clock.millis()));
        String id = stateService.canonicalTaskId(taskId);
        log.atInfo().setMessage("turn opened").addKeyValue("task", id).addKeyValue("step", step)
                .addKeyValue("turn", of(id).id()).log();
    }

    public void mark(String taskId, String step, String value) {
        update(taskId, turn -> turn.withMark(step, value));
    }

    public void unmark(String taskId, String step) {
        update(taskId, turn -> turn.withoutMark(step));
    }

    private void update(String taskId, UnaryOperator<Turn> change) {
        stateService.updateTask(stateService.canonicalTaskId(taskId),
                task -> task.withTurn(change.apply(task.turnOrNone())));
    }
}
