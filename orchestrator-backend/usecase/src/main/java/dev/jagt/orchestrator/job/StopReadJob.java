package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.AutoReviewCadence;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/** Reads the checks of a task stopped by an outside condition on the auto-review cadence, so the stop can end. */
@Service
@Slf4j
@RequiredArgsConstructor
public class StopReadJob implements Job {

    private final StateService stateService;
    private final ConfigService configService;
    private final StopReads reads;
    private final Clock clock;

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
        return Duration.ofMinutes(1);
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public void run() {
        AutoReviewCadence cadence = AutoReviewCadence.from(configService.load().autoReview());
        long now = clock.millis();
        stateService.tasks().forEach((taskId, task) -> {
            if (endsOnRead(task) && AutoReviewScheduler.decide(task.lifted(now), cadence, now) == AutoReviewScheduler.Action.POLL) {
                probe(taskId, now);
            }
        });
    }

    private static boolean endsOnRead(TaskState task) {
        TaskStop stop = task.stop();
        return stop != null && stop.trigger().ends() == TaskStop.Trigger.Ends.HOST_READ;
    }

    private void probe(String taskId, long now) {
        try {
            reads.probe(taskId);
        } catch (RuntimeException e) {
            log.atWarn().setMessage("stop read failed").addKeyValue("task", taskId)
                    .addKeyValue("cause", e.getMessage()).addKeyValue("effect", "read again next poll").log();
        } finally {
            stateService.updateTask(taskId, task -> task.withLastPolledAt(now));
        }
    }
}
