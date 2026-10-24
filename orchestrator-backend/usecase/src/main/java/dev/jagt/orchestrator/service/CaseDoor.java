package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.CaseBook;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** A step goes to a session only where a case sends it at the task's status; a miss is recorded, never decided. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaseDoor {

    private final SessionCases cases;
    private final StateService stateService;
    private final MissLog misses;

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
                .addKeyValue("effect", "nothing relayed")
                .addKeyValue("fix", SessionCases.FILE)
                .log();
        return false;
    }
}
