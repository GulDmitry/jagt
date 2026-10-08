package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One review sweep. The human-in-the-loop rule lives in the OUTCOME, not in who triggered it: an approval advances
 * state, but comments are only RELAYED as drafts — nothing is pushed or posted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewSweepService {

    public record SweepResult(Kind kind, String message) {
        public enum Kind { NO_MR, UNREADABLE, APPROVED, REVIEWED, PENDING, RELAYED, UNCHANGED, IN_FLIGHT }
    }

    private final StateService stateService;
    private final RoundReading reading;
    private final RoundOutcome outcome;
    /**
     * One sweep at a time per task, no matter who asked: a second sweep of one request pays for the read twice and
     * relays a second brief for the same round.
     */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public SweepResult sweep(String taskIdOrAlias) {
        // Resolve first: `sweep a1` and the scheduler's `sweep ABC-1` must take the SAME lock.
        String taskId = stateService.canonicalTaskId(taskIdOrAlias);
        if (!inFlight.add(taskId)) {
            return new SweepResult(SweepResult.Kind.IN_FLIGHT,
                    "sweep " + taskId + ": already running — wait for it");
        }
        try {
            SweepResult result = switch (reading.read(taskId)) {
                case RoundReading.Refused refused -> refused.result();
                case RoundReading.Round round -> outcome.settle(taskId, round.mrUrl(), round.facts());
            };
            String alias = stateService.task(taskId).map(TaskState::alias).orElse(null);
            log.atInfo().setMessage("sweep done").addKeyValue("task", taskId).addKeyValue("alias", alias)
                    .addKeyValue("outcome", result.kind())
                    .addKeyValue("said", result.message())
                    .log();
            return result;
        } finally {
            inFlight.remove(taskId);
        }
    }
}
