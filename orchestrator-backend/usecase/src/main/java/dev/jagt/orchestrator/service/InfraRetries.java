package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.TaskStop;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The re-runs of a pipeline that died on infrastructure, kept on the task so they outlive a restart: the ones sent
 * since the last lift, and at most one armed. Only the host's own verdict arms one, and only the job sends it.
 */
@Component
@RequiredArgsConstructor
public class InfraRetries {

    private final StateService stateService;
    private final TaskStops stops;
    private final InfraWaitProperties properties;
    private final Clock clock;

    /** The retry waiting for {@code pipelineUrl}: the one already armed, or a new one; empty once every wait was spent. */
    public Optional<InfraRetry> arm(String taskId, String pipelineUrl, String jobs, String reason) {
        AtomicReference<Optional<InfraRetry>> armed = new AtomicReference<>(Optional.empty());
        stateService.updateTask(taskId, task -> {
            Optional<InfraRetry> pending = InfraRetry.pending(task.infraRetries())
                    .filter(retry -> retry.pipelineUrl().equals(pipelineUrl));
            if (pending.isPresent()) {
                armed.set(pending);
                return task;
            }
            List<InfraRetry> sent = InfraRetry.sentSince(task.infraRetries(), task.trailOrNone());
            Optional<InfraRetry> next = properties.before(sent.size())
                    .map(wait -> InfraRetry.armed(clock.millis() + wait.toMillis(), pipelineUrl, jobs, reason));
            armed.set(next);
            return next.map(retry -> task.withInfraRetries(with(sent, retry))).orElse(task);
        });
        return armed.get();
    }

    /** Forgets the armed retry, and the sent ones too when {@code all}: a run that went green or red for the code owes no more. */
    public void clear(String taskId, boolean all) {
        stateService.updateTask(taskId, task -> {
            List<InfraRetry> kept = all ? List.of() : task.infraRetries().stream().filter(InfraRetry::sent).toList();
            return kept.equals(task.infraRetries()) ? task : task.withInfraRetries(kept);
        });
    }

    /** The retry sent: it joins those counted, and the task's history keeps the move. */
    public void sent(String taskId, InfraRetry retry) {
        stateService.updateTask(taskId, task -> {
            List<InfraRetry> kept = task.infraRetries().stream().filter(InfraRetry::sent).toList();
            return task.withInfraRetries(with(kept, retry.sentAt(clock.millis()))).withMoveRecorded(clock.millis());
        });
    }

    /** The armed retries whose time has come, by task. */
    public Map<String, InfraRetry> due() {
        long now = clock.millis();
        Map<String, InfraRetry> due = new LinkedHashMap<>();
        stateService.tasks().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(task ->
                InfraRetry.pending(task.getValue().infraRetries()).filter(retry -> retry.at() <= now)
                        .ifPresent(retry -> due.put(task.getKey(), retry)));
        return due;
    }

    /** The task is open, not stopped, and its checks still read red: a human may have acted since the retry was armed. */
    public boolean stillWaiting(String taskId) {
        return stateService.task(taskId).filter(task -> !FlowRules.closed(task.status()) && !task.stopped())
                .filter(task -> Pipeline.of(task.pipelineStatus()) == Pipeline.RED).isPresent();
    }

    /** The stop once the last wait is spent and the pipeline is red on infrastructure again. */
    public Optional<TaskStop> exhausted(String taskId) {
        return stops.trip(taskId, task -> InfraRetry.stop(task.infraRetries(), task.trailOrNone(),
                properties.backoff().size(), clock.millis()));
    }

    /** The stop for a pipeline that could not be read or re-run, in the words of whatever refused. */
    public Optional<TaskStop> refused(String taskId, String words) {
        return stops.stop(taskId, TaskStop.Trigger.INFRA, words);
    }

    /** What the armed retry waits on and when it goes, as a human reads it; blank where none is armed. */
    public String reading(String taskId) {
        return stateService.task(taskId).flatMap(task -> InfraRetry.pending(task.infraRetries()).map(retry ->
                retry.reading(InfraRetry.sentSince(task.infraRetries(), task.trailOrNone()).size() + 1,
                        properties.backoff().size()))).orElse("");
    }

    private static List<InfraRetry> with(List<InfraRetry> kept, InfraRetry retry) {
        List<InfraRetry> all = new ArrayList<>(kept);
        all.add(retry);
        return all;
    }
}
