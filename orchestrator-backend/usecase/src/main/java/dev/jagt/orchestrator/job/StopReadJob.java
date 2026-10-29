package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Reads the checks of every task stopped by an outside condition, so the stop can end once the host is well. */
@Service
@Slf4j
@RequiredArgsConstructor
public class StopReadJob implements Job {

    private final StateService stateService;
    private final StopReads reads;

    @Override
    public String id() {
        return "stop-read";
    }

    @Override
    public String describe() {
        return "read the checks of a task stopped by an outside condition, and end the stop once the host is well";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(5);
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public void run() {
        stateService.tasks().forEach((taskId, task) -> {
            if (endsOnRead(task)) {
                probe(taskId);
            }
        });
    }

    private static boolean endsOnRead(TaskState task) {
        TaskStop stop = task.stop();
        return stop != null && stop.trigger().ends() == TaskStop.Trigger.Ends.HOST_READ;
    }

    private void probe(String taskId) {
        try {
            reads.probe(taskId);
        } catch (RuntimeException e) {
            log.atWarn().setMessage("stop read failed").addKeyValue("task", taskId)
                    .addKeyValue("cause", e.getMessage()).addKeyValue("effect", "read again in 5 minutes").log();
        }
    }
}
